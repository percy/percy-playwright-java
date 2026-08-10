package io.percy.playwright;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the BLOCKER fix and the per-pair try/catch in
 * {@code exposeClosedShadowRoots}.
 *
 * <p>These tests deliberately do NOT depend on a live Playwright browser; they
 * mock {@link Page}/{@link Frame}/{@link CDPSession} so they run on every CI
 * matrix entry (Java 8..21) without networking.</p>
 */
public class PercyShadowAndCorsTopologyTest {

    // ---------------------------------------------------------------------
    // BLOCKER fix: main(A) -> same-origin(A) -> cross-origin(B) must capture B
    // ---------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void crossOriginGrandchildOfSameOriginFrameIsStillCaptured() throws Exception {
        Page mockPage = mock(Page.class);
        Frame mainFrame = mock(Frame.class);
        Frame sameOriginChild = mock(Frame.class);
        Frame crossOriginGrandchild = mock(Frame.class);

        Map<String, Object> mainDomMap = new HashMap<>();
        mainDomMap.put("html", "<html><body></body></html>");
        when(mockPage.evaluate(anyString())).thenReturn(mainDomMap);

        Map<String, Object> iframeDataMap = new HashMap<>();
        iframeDataMap.put("percyElementId", "percy-elem-grandchild");
        when(mockPage.evaluate(anyString(), any())).thenReturn(iframeDataMap);

        when(mockPage.url()).thenReturn("http://example.com/page");

        // Topology: main(example.com) -> sameOriginChild(example.com) ->
        // crossOriginGrandchild(other.com). page.frames() returns the full tree.
        when(mainFrame.url()).thenReturn("http://example.com/page");
        when(sameOriginChild.url()).thenReturn("http://example.com/inner");
        when(crossOriginGrandchild.url()).thenReturn("http://other.com/embed");

        // parent chain
        when(mainFrame.parentFrame()).thenReturn(null);
        when(sameOriginChild.parentFrame()).thenReturn(mainFrame);
        when(crossOriginGrandchild.parentFrame()).thenReturn(sameOriginChild);

        when(mockPage.frames()).thenReturn(Arrays.asList(
                mainFrame, sameOriginChild, crossOriginGrandchild));

        // Frame serialization mock for the grandchild
        Map<String, Object> grandchildSnapshot = new HashMap<>();
        grandchildSnapshot.put("html", "<html>cors grandchild</html>");
        when(crossOriginGrandchild.evaluate(anyString()))
                .thenReturn(null)
                .thenReturn(grandchildSnapshot);

        Percy percyInstance = new Percy(mockPage);

        Map<String, Object> result = percyInstance.getSerializedDOM(
                new ArrayList<>(), "// percy dom script", new HashMap<>());

        assertNotNull(result);
        assertNotNull(result.get("corsIframes"),
                "Cross-origin grandchild (main -> same-origin -> CORS) must still be captured");

        List<Map<String, Object>> corsIframes = (List<Map<String, Object>>) result.get("corsIframes");
        assertEquals(1, corsIframes.size(),
                "Exactly one CORS frame should be emitted for the grandchild");
        assertEquals("http://other.com/embed", corsIframes.get(0).get("frameUrl"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void descendantsOfACorsFrameAreNotRecapturedIndependently() throws Exception {
        // Topology: main(A) -> cors(B) -> same-origin-of-B(B) -> nested-cors(C)
        // We want exactly one top-level CORS emission (B). C is inside B's
        // document tree and is handled by PercyDOM.serialize() inside B; the
        // outer walk must not double-emit it.
        Page mockPage = mock(Page.class);
        Frame mainFrame = mock(Frame.class);
        Frame corsB = mock(Frame.class);
        Frame innerOfB = mock(Frame.class);
        Frame nestedCorsC = mock(Frame.class);

        Map<String, Object> mainDomMap = new HashMap<>();
        mainDomMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(mainDomMap);

        Map<String, Object> iframeDataMap = new HashMap<>();
        iframeDataMap.put("percyElementId", "percy-elem-b");
        when(mockPage.evaluate(anyString(), any())).thenReturn(iframeDataMap);

        when(mockPage.url()).thenReturn("http://a.com/");
        when(mainFrame.url()).thenReturn("http://a.com/");
        when(corsB.url()).thenReturn("http://b.com/");
        when(innerOfB.url()).thenReturn("http://b.com/inner");
        when(nestedCorsC.url()).thenReturn("http://c.com/");

        when(mainFrame.parentFrame()).thenReturn(null);
        when(corsB.parentFrame()).thenReturn(mainFrame);
        when(innerOfB.parentFrame()).thenReturn(corsB);
        when(nestedCorsC.parentFrame()).thenReturn(innerOfB);

        when(mockPage.frames()).thenReturn(Arrays.asList(
                mainFrame, corsB, innerOfB, nestedCorsC));

        Map<String, Object> bSnapshot = new HashMap<>();
        bSnapshot.put("html", "<html>B</html>");
        when(corsB.evaluate(anyString()))
                .thenReturn(null)
                .thenReturn(bSnapshot);

        Percy percyInstance = new Percy(mockPage);

        Map<String, Object> result = percyInstance.getSerializedDOM(
                new ArrayList<>(), "// percy dom script", new HashMap<>());

        assertNotNull(result);
        List<Map<String, Object>> corsIframes = (List<Map<String, Object>>) result.get("corsIframes");
        assertNotNull(corsIframes, "Outer CORS frame B must be captured");
        assertEquals(1, corsIframes.size(),
                "Nested CORS C must not be processed independently; it lives inside B");
        assertEquals("http://b.com/", corsIframes.get(0).get("frameUrl"));

        // nestedCorsC.evaluate must never be called by the outer walk
        verify(nestedCorsC, never()).evaluate(anyString());
    }

    // ---------------------------------------------------------------------
    // DOM.enable / DOM.disable pairing
    // ---------------------------------------------------------------------

    @Test
    public void domEnableIsPairedWithDomDisableOnSuccess() throws Exception {
        Page mockPage = mock(Page.class);
        BrowserContext mockCtx = mock(BrowserContext.class);
        CDPSession mockCdp = mock(CDPSession.class);

        when(mockPage.context()).thenReturn(mockCtx);
        when(mockCtx.newCDPSession(mockPage)).thenReturn(mockCdp);

        // DOM.enable returns empty object
        when(mockCdp.send("DOM.enable")).thenReturn(new JsonObject());
        // DOM.getDocument returns a tiny tree with no closed shadow roots
        JsonObject docResult = new JsonObject();
        JsonObject root = new JsonObject();
        root.addProperty("nodeId", 1);
        root.addProperty("backendNodeId", 1);
        docResult.add("root", root);
        when(mockCdp.send(anyString(), any())).thenReturn(docResult);

        invokeExposeClosedShadowRoots(new Percy(mockPage), mockPage);

        verify(mockCdp, times(1)).send("DOM.enable");
        verify(mockCdp, times(1)).send("DOM.disable");
        verify(mockCdp, times(1)).detach();
    }

    @Test
    public void domEnableIsPairedWithDomDisableOnException() throws Exception {
        Page mockPage = mock(Page.class);
        BrowserContext mockCtx = mock(BrowserContext.class);
        CDPSession mockCdp = mock(CDPSession.class);

        when(mockPage.context()).thenReturn(mockCtx);
        when(mockCtx.newCDPSession(mockPage)).thenReturn(mockCdp);

        when(mockCdp.send("DOM.enable")).thenReturn(new JsonObject());
        // DOM.getDocument explodes — must still pair with DOM.disable
        when(mockCdp.send(anyString(), any()))
                .thenThrow(new RuntimeException("simulated CDP error"));

        invokeExposeClosedShadowRoots(new Percy(mockPage), mockPage);

        verify(mockCdp, times(1)).send("DOM.enable");
        verify(mockCdp, times(1)).send("DOM.disable");
        verify(mockCdp, times(1)).detach();
    }

    @Test
    public void domDisableNotCalledWhenDomEnableFails() throws Exception {
        // If DOM.enable itself throws, we must NOT call DOM.disable (it was
        // never enabled). But the CDP session must still be detached.
        Page mockPage = mock(Page.class);
        BrowserContext mockCtx = mock(BrowserContext.class);
        CDPSession mockCdp = mock(CDPSession.class);

        when(mockPage.context()).thenReturn(mockCtx);
        when(mockCtx.newCDPSession(mockPage)).thenReturn(mockCdp);

        when(mockCdp.send("DOM.enable")).thenThrow(new RuntimeException("no DOM"));

        invokeExposeClosedShadowRoots(new Percy(mockPage), mockPage);

        verify(mockCdp, times(1)).send("DOM.enable");
        verify(mockCdp, never()).send("DOM.disable");
        verify(mockCdp, times(1)).detach();
    }

    // ---------------------------------------------------------------------
    // Per-host loop isolation: one bad backendNodeId must not abort the rest
    // ---------------------------------------------------------------------

    @Test
    public void oneBadBackendNodeIdDoesNotAbortRemainingShadowHosts() throws Exception {
        Page mockPage = mock(Page.class);
        BrowserContext mockCtx = mock(BrowserContext.class);
        CDPSession mockCdp = mock(CDPSession.class);

        when(mockPage.context()).thenReturn(mockCtx);
        when(mockCtx.newCDPSession(mockPage)).thenReturn(mockCdp);

        // Build a fake DOM tree with two closed shadow roots:
        //   host#1 (host backendNodeId=100, shadow backendNodeId=101)  -- bad: resolveNode throws
        //   host#2 (host backendNodeId=200, shadow backendNodeId=201)  -- good
        JsonObject root = new JsonObject();
        root.addProperty("backendNodeId", 1);
        JsonArray rootChildren = new JsonArray();

        rootChildren.add(buildHostWithClosedShadow(100, 101));
        rootChildren.add(buildHostWithClosedShadow(200, 201));
        root.add("children", rootChildren);

        JsonObject docResult = new JsonObject();
        docResult.add("root", root);

        AtomicInteger callFunctionOnCalls = new AtomicInteger(0);
        AtomicReference<RuntimeException> resolveNodeError =
                new AtomicReference<>(new RuntimeException("detached node"));

        when(mockCdp.send("DOM.enable")).thenReturn(new JsonObject());

        when(mockCdp.send(anyString(), any())).thenAnswer(inv -> {
            String method = inv.getArgument(0);
            JsonObject params = inv.getArgument(1);
            if ("DOM.getDocument".equals(method)) {
                return docResult;
            }
            if ("DOM.resolveNode".equals(method)) {
                int bid = params.get("backendNodeId").getAsInt();
                // Fail only for the bad host (100) and its shadow (101)
                if (bid == 100 || bid == 101) {
                    throw resolveNodeError.get();
                }
                JsonObject ok = new JsonObject();
                JsonObject obj = new JsonObject();
                obj.addProperty("objectId", "obj-" + bid);
                ok.add("object", obj);
                return ok;
            }
            if ("Runtime.callFunctionOn".equals(method)) {
                callFunctionOnCalls.incrementAndGet();
                return new JsonObject();
            }
            return new JsonObject();
        });

        invokeExposeClosedShadowRoots(new Percy(mockPage), mockPage);

        // Exactly one shadow host (the good one) must have been exposed.
        assertEquals(1, callFunctionOnCalls.get(),
                "Bad host must be skipped, good host must still be exposed");
        verify(mockCdp, times(1)).send("DOM.disable");
        verify(mockCdp, times(1)).detach();
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static JsonObject buildHostWithClosedShadow(int hostBid, int shadowBid) {
        JsonObject host = new JsonObject();
        host.addProperty("backendNodeId", hostBid);
        JsonArray shadowRoots = new JsonArray();
        JsonObject sr = new JsonObject();
        sr.addProperty("backendNodeId", shadowBid);
        sr.addProperty("shadowRootType", "closed");
        shadowRoots.add(sr);
        host.add("shadowRoots", shadowRoots);
        return host;
    }

    private static void invokeExposeClosedShadowRoots(Percy percy, Page page) throws Exception {
        Method m = Percy.class.getDeclaredMethod("exposeClosedShadowRoots", Page.class);
        m.setAccessible(true);
        m.invoke(percy, page);
    }
}
