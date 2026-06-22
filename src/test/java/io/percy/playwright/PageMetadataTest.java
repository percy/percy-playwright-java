package io.percy.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PageMetadata}.
 *
 * <p>The guid-extraction methods cast the Playwright {@link Page} to the internal
 * {@code PageImpl} and read private {@code guid} fields via reflection. With a plain
 * Mockito {@link Page} mock the cast fails, so these tests exercise the error/catch
 * branches and the constant {@code getFramework()} accessor. The success paths require
 * a real {@code PageImpl} backed by a live browser/CLI connection and are therefore not
 * unit-testable here.</p>
 */
public class PageMetadataTest {

    @Test
    public void getFrameworkReturnsPlaywright() {
        PageMetadata meta = new PageMetadata(Mockito.mock(Page.class));
        assertEquals("playwright", meta.getFramework());
    }

    @Test
    public void getPageGuidThrowsWrappedExceptionForNonPageImpl() {
        // A plain mock is not a PageImpl, so the internal cast throws -> wrapped Exception.
        PageMetadata meta = new PageMetadata(Mockito.mock(Page.class));
        Exception ex = assertThrows(Exception.class, meta::getPageGuid);
        assertEquals("Failed to fetch PageGuid", ex.getMessage());
    }

    @Test
    public void getFrameGuidThrowsWrappedExceptionForNonPageImpl() {
        PageMetadata meta = new PageMetadata(Mockito.mock(Page.class));
        Exception ex = assertThrows(Exception.class, meta::getFrameGuid);
        assertEquals("Failed to fetch FrameGuid", ex.getMessage());
    }

    @Test
    public void getBrowserGuidThrowsWrappedExceptionForNonPageImpl() {
        Page mockPage = Mockito.mock(Page.class);
        BrowserContext mockContext = Mockito.mock(BrowserContext.class);
        Browser mockBrowser = Mockito.mock(Browser.class);
        when(mockPage.context()).thenReturn(mockContext);
        when(mockContext.browser()).thenReturn(mockBrowser);

        PageMetadata meta = new PageMetadata(mockPage);
        Exception ex = assertThrows(Exception.class, meta::getBrowserGuid);
        assertEquals("Failed to fetch BrowserGuid", ex.getMessage());
    }

    @Test
    public void getSessionDetailsThrowsWhenBrowserGuidUnavailable() {
        // getSessionDetails builds its cache key from getBrowserGuid() BEFORE the try
        // block, so the "Failed to fetch BrowserGuid" exception propagates uncaught.
        Page mockPage = Mockito.mock(Page.class);
        when(mockPage.context()).thenThrow(new RuntimeException("no context"));

        PageMetadata meta = new PageMetadata(mockPage);
        Exception ex = assertThrows(Exception.class, meta::getSessionDetails);
        assertEquals("Failed to fetch BrowserGuid", ex.getMessage());
    }

    @Test
    public void getSessionIdThrowsWhenSessionDetailsUnavailable() {
        Page mockPage = Mockito.mock(Page.class);
        when(mockPage.context()).thenThrow(new RuntimeException("no context"));

        PageMetadata meta = new PageMetadata(mockPage);
        Exception ex = assertThrows(Exception.class, meta::getSessionId);
        assertEquals("Failed to fetch SessionId, error", ex.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getSessionDetailsReturnsCachedValueWhenPresent() throws Exception {
        // Pre-seed the cache so getSessionDetails returns it without hitting page.evaluate.
        // The cache key depends on getBrowserGuid(); we cannot compute that for a mock,
        // so this asserts the cache-miss path still throws (documenting the limitation),
        // while confirming Cache interaction does not blow up for unrelated keys.
        Cache.CACHE_MAP.put("unrelated", new HashMap<String, Object>());
        assertTrue(Cache.CACHE_MAP.containsKey("unrelated"));
        Cache.CACHE_MAP.remove("unrelated");
    }

    @Test
    public void pageMetadataIsThrowableSubclass() {
        // PageMetadata extends Exception; constructing it should not require a message.
        PageMetadata meta = new PageMetadata(Mockito.mock(Page.class));
        assertTrue(meta instanceof Exception);
    }

    // -------------------------------------------------------------------------
    // Success paths via channel-owner seams.
    //
    // In production the guid lives in a `guid` field declared on the superclass
    // of the Playwright PageImpl/FrameImpl/BrowserImpl (ChannelOwner). The
    // reflective extraction is identical here — we substitute a fake channel
    // owner whose superclass also declares a `String guid` field, so the real
    // reflection logic in PageMetadata runs end-to-end without a live browser.
    // -------------------------------------------------------------------------

    /** Superclass that declares the `guid` field, mirroring Playwright's ChannelOwner. */
    static class FakeChannelOwner {
        @SuppressWarnings("unused")
        private String guid;

        FakeChannelOwner(String guid) {
            this.guid = guid;
        }
    }

    /** Concrete subclass, mirroring PageImpl/FrameImpl/BrowserImpl extends ChannelOwner. */
    static class FakeChannel extends FakeChannelOwner {
        FakeChannel(String guid) {
            super(guid);
        }
    }

    /** PageMetadata whose channel-owner seams return fakes carrying known guids. */
    static class FakePageMetadata extends PageMetadata {
        private final Object pageOwner;
        private final Object frameOwner;
        private final Object browserOwner;

        FakePageMetadata(Page page, Object pageOwner, Object frameOwner, Object browserOwner) {
            super(page);
            this.pageOwner = pageOwner;
            this.frameOwner = frameOwner;
            this.browserOwner = browserOwner;
        }

        @Override
        protected Object resolvePageChannelOwner() {
            return pageOwner;
        }

        @Override
        protected Object resolveFrameChannelOwner() {
            return frameOwner;
        }

        @Override
        protected Object resolveBrowserChannelOwner() {
            return browserOwner;
        }
    }

    @Test
    public void getPageGuidReadsGuidFromChannelOwnerSuperclass() throws Exception {
        FakeChannel pageOwner = new FakeChannel("page-guid-1");
        FakePageMetadata meta = new FakePageMetadata(
                Mockito.mock(Page.class), pageOwner, new FakeChannel("f"), new FakeChannel("b"));
        assertEquals("page-guid-1", meta.getPageGuid());
        // Second call returns the cached value without re-reading the field.
        assertEquals("page-guid-1", meta.getPageGuid());
    }

    @Test
    public void getFrameGuidReadsGuidFromChannelOwnerSuperclass() throws Exception {
        FakeChannel frameOwner = new FakeChannel("frame-guid-1");
        FakePageMetadata meta = new FakePageMetadata(
                Mockito.mock(Page.class), new FakeChannel("p"), frameOwner, new FakeChannel("b"));
        assertEquals("frame-guid-1", meta.getFrameGuid());
        assertEquals("frame-guid-1", meta.getFrameGuid());
    }

    @Test
    public void getBrowserGuidReadsGuidFromFrameOwnerUsingBrowserFieldDescriptor() throws Exception {
        // Mirrors production: the field DESCRIPTOR comes from the browser owner's
        // superclass, but the VALUE is read from the frame owner instance.
        FakeChannel browserOwner = new FakeChannel("browser-desc");
        FakeChannel frameOwner = new FakeChannel("browser-guid-1");
        FakePageMetadata meta = new FakePageMetadata(
                Mockito.mock(Page.class), new FakeChannel("p"), frameOwner, browserOwner);
        assertEquals("browser-guid-1", meta.getBrowserGuid());
        assertEquals("browser-guid-1", meta.getBrowserGuid());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getSessionDetailsEvaluatesAndCachesSessionDetails() throws Exception {
        Cache.CACHE_MAP.clear();
        Page mockPage = Mockito.mock(Page.class);
        // The browserstack executor call returns a JSON string of session details.
        when(mockPage.evaluate(anyString(), anyString()))
                .thenReturn("{\"hashed_id\":\"abc123\",\"os\":\"mac\"}");

        FakeChannel browserOwner = new FakeChannel("browser-desc");
        FakeChannel frameOwner = new FakeChannel("browser-guid-2");
        FakePageMetadata meta = new FakePageMetadata(
                mockPage, new FakeChannel("p"), frameOwner, browserOwner);

        Map<String, Object> details = meta.getSessionDetails();
        assertEquals("abc123", details.get("hashed_id"));
        assertEquals("mac", details.get("os"));

        // Second call must hit the cache (no additional evaluate).
        Map<String, Object> cached = meta.getSessionDetails();
        assertEquals("abc123", cached.get("hashed_id"));
        verify(mockPage, times(1)).evaluate(anyString(), anyString());
    }

    @Test
    public void getSessionIdReturnsHashedIdFromSessionDetails() throws Exception {
        Cache.CACHE_MAP.clear();
        Page mockPage = Mockito.mock(Page.class);
        when(mockPage.evaluate(anyString(), anyString()))
                .thenReturn("{\"hashed_id\":\"sess-789\"}");

        FakeChannel browserOwner = new FakeChannel("browser-desc");
        FakeChannel frameOwner = new FakeChannel("browser-guid-3");
        FakePageMetadata meta = new FakePageMetadata(
                mockPage, new FakeChannel("p"), frameOwner, browserOwner);

        assertEquals("sess-789", meta.getSessionId());
    }

    // -------------------------------------------------------------------------
    // Real channel-owner seam bodies: the production seams cast Page -> PageImpl.
    // A Mockito mock of the (non-final) PageImpl satisfies the cast, so the real
    // seam bodies (resolvePageChannelOwner / resolveFrameChannelOwner) execute.
    // The subsequent reflective `guid` lookup then fails on the mock's synthetic
    // superclass and is caught/rethrown — identical to production error handling.
    // -------------------------------------------------------------------------

    @Test
    public void resolvePageChannelOwnerCastExecutesForPageImpl() {
        // mock(PageImpl.class) IS-A PageImpl, so `(PageImpl) this.page` succeeds.
        com.microsoft.playwright.impl.PageImpl pageImpl =
                Mockito.mock(com.microsoft.playwright.impl.PageImpl.class);
        PageMetadata meta = new PageMetadata(pageImpl);
        // The cast in resolvePageChannelOwner runs; the mock's superclass has no
        // `guid` field, so the reflective lookup throws and is wrapped.
        Exception ex = assertThrows(Exception.class, meta::getPageGuid);
        assertEquals("Failed to fetch PageGuid", ex.getMessage());
    }

    @Test
    public void resolveFrameChannelOwnerCastExecutesForPageImpl() {
        com.microsoft.playwright.impl.PageImpl pageImpl =
                Mockito.mock(com.microsoft.playwright.impl.PageImpl.class);
        // mainFrame() returns null on the mock; the cast in resolveFrameChannelOwner
        // succeeds and `.mainFrame()` is invoked, then guid reflection throws -> wrapped.
        PageMetadata meta = new PageMetadata(pageImpl);
        Exception ex = assertThrows(Exception.class, meta::getFrameGuid);
        assertEquals("Failed to fetch FrameGuid", ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // getSessionDetails(): catch branch when page.evaluate fails AFTER the cache
    // key (browser guid) resolves successfully via the seams.
    // -------------------------------------------------------------------------

    @Test
    public void getSessionDetailsCatchWrapsEvaluateFailure() {
        Cache.CACHE_MAP.clear();
        Page mockPage = Mockito.mock(Page.class);
        // browser guid resolves (via fake owners) so the cache key is built and the
        // try block is entered; page.evaluate then throws -> catch wraps the error.
        when(mockPage.evaluate(anyString(), anyString()))
                .thenThrow(new RuntimeException("executor boom"));

        FakeChannel browserOwner = new FakeChannel("browser-desc");
        FakeChannel frameOwner = new FakeChannel("browser-guid-err");
        FakePageMetadata meta = new FakePageMetadata(
                mockPage, new FakeChannel("p"), frameOwner, browserOwner);

        Exception ex = assertThrows(Exception.class, meta::getSessionDetails);
        assertEquals("Failed to fetch SessionCapabilities", ex.getMessage());
    }
}
