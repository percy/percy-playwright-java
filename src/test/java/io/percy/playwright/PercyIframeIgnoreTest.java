package io.percy.playwright;

import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the iframe-ignore controls ported from the JS SDK:
 *   - the {@code data-percy-ignore} attribute opt-out
 *   - the {@code ignoreIframeSelectors} option (per-snapshot AND global config)
 *
 * <p>These mock {@link Page}/{@link Frame} so they run without a live browser.</p>
 */
public class PercyIframeIgnoreTest {

    /**
     * Wires up a main(A) -> cross-origin(B) topology and returns the cross-origin
     * frame so the test can configure its ignore flags / serialization.
     */
    private Frame buildCorsTopology(Page mockPage, Frame mainFrame, Frame corsFrame) {
        Map<String, Object> mainDomMap = new HashMap<>();
        mainDomMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(mainDomMap);

        when(mockPage.url()).thenReturn("http://a.com/");
        when(mainFrame.url()).thenReturn("http://a.com/");
        when(corsFrame.url()).thenReturn("http://b.com/");

        when(mainFrame.parentFrame()).thenReturn(null);
        when(corsFrame.parentFrame()).thenReturn(mainFrame);

        when(mockPage.frames()).thenReturn(Arrays.asList(mainFrame, corsFrame));

        // Cross-origin frame serialization: first evaluate (DOM injection) -> null,
        // second evaluate (PercyDOM.serialize) -> snapshot map.
        Map<String, Object> bSnapshot = new HashMap<>();
        bSnapshot.put("html", "<html>B</html>");
        when(corsFrame.evaluate(anyString())).thenReturn(null).thenReturn(bSnapshot);

        return corsFrame;
    }

    /** Make the parent-frame ignore-flag lookup (evaluate with an arg) return the given flags. */
    private void stubIgnoreFlags(Frame mainFrame, boolean dataPercyIgnore, boolean matchesIgnoreSelector) {
        Map<String, Object> flags = new HashMap<>();
        flags.put("dataPercyIgnore", dataPercyIgnore);
        flags.put("matchesIgnoreSelector", matchesIgnoreSelector);
        when(mainFrame.evaluate(anyString(), any())).thenReturn(flags);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void iframeWithDataPercyIgnoreIsSkipped() {
        Page mockPage = mock(Page.class);
        Frame mainFrame = mock(Frame.class);
        Frame corsFrame = mock(Frame.class);
        buildCorsTopology(mockPage, mainFrame, corsFrame);

        // iframe element in the parent (main) DOM carries data-percy-ignore
        stubIgnoreFlags(mainFrame, true, false);

        Percy percy = new Percy(mockPage);
        Map<String, Object> result = percy.getSerializedDOM(
                new ArrayList<>(), "// percy dom", new HashMap<>());

        assertNotNull(result);
        assertNull(result.get("corsIframes"),
                "iframe marked data-percy-ignore must not be captured");
        // The expensive serialize path must never run for the skipped frame
        verify(corsFrame, never()).evaluate(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void iframeMatchingPerSnapshotIgnoreSelectorIsSkipped() {
        Page mockPage = mock(Page.class);
        Frame mainFrame = mock(Frame.class);
        Frame corsFrame = mock(Frame.class);
        buildCorsTopology(mockPage, mainFrame, corsFrame);

        // The in-browser el.matches() resolved to true for the configured selector
        stubIgnoreFlags(mainFrame, false, true);

        Map<String, Object> options = new HashMap<>();
        options.put("ignoreIframeSelectors", Collections.singletonList(".ad-frame"));

        Percy percy = new Percy(mockPage);
        Map<String, Object> result = percy.getSerializedDOM(
                new ArrayList<>(), "// percy dom", options);

        assertNotNull(result);
        assertNull(result.get("corsIframes"),
                "iframe matching per-snapshot ignoreIframeSelectors must not be captured");
        verify(corsFrame, never()).evaluate(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void iframeMatchingGlobalConfigIgnoreSelectorIsSkipped() {
        Page mockPage = mock(Page.class);
        Frame mainFrame = mock(Frame.class);
        Frame corsFrame = mock(Frame.class);
        buildCorsTopology(mockPage, mainFrame, corsFrame);

        stubIgnoreFlags(mainFrame, false, true);

        Percy percy = new Percy(mockPage);
        // Global config: percy.config.snapshot.ignoreIframeSelectors
        JSONObject config = new JSONObject();
        JSONObject snapshot = new JSONObject();
        snapshot.put("ignoreIframeSelectors", new JSONArray(Collections.singletonList(".ad-frame")));
        config.put("snapshot", snapshot);
        percy.cliConfig = config;

        Map<String, Object> result = percy.getSerializedDOM(
                new ArrayList<>(), "// percy dom", new HashMap<>());

        assertNotNull(result);
        assertNull(result.get("corsIframes"),
                "iframe matching global config ignoreIframeSelectors must not be captured");
        verify(corsFrame, never()).evaluate(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void nonMatchingIframeIsStillCaptured() {
        Page mockPage = mock(Page.class);
        Frame mainFrame = mock(Frame.class);
        Frame corsFrame = mock(Frame.class);
        buildCorsTopology(mockPage, mainFrame, corsFrame);

        // Neither data-percy-ignore nor a selector match.
        // mainFrame.evaluate(js, arg) is used both for the ignore-flag lookup AND
        // the percyElementId lookup, so return ignore-flags first, then the id.
        Map<String, Object> flags = new HashMap<>();
        flags.put("dataPercyIgnore", false);
        flags.put("matchesIgnoreSelector", false);
        Map<String, Object> idData = new HashMap<>();
        idData.put("percyElementId", "percy-elem-b");
        when(mainFrame.evaluate(anyString(), any())).thenReturn(flags);
        when(mockPage.evaluate(anyString(), any())).thenReturn(idData);

        Percy percy = new Percy(mockPage);
        Map<String, Object> result = percy.getSerializedDOM(
                new ArrayList<>(), "// percy dom", new HashMap<>());

        assertNotNull(result);
        List<Map<String, Object>> corsIframes = (List<Map<String, Object>>) result.get("corsIframes");
        assertNotNull(corsIframes, "Non-ignored cross-origin iframe must still be captured");
        assertEquals(1, corsIframes.size());
        assertEquals("http://b.com/", corsIframes.get(0).get("frameUrl"));
    }

    @Test
    public void resolveIgnoreSelectorsNormalizesAndPrefersPerSnapshot() {
        Page mockPage = mock(Page.class);
        Percy percy = new Percy(mockPage);

        // Per-snapshot list with an empty string filtered out
        Map<String, Object> options = new HashMap<>();
        options.put("ignoreIframeSelectors", Arrays.asList(".a", "", ".b"));
        assertEquals(Arrays.asList(".a", ".b"), percy.resolveIgnoreSelectors(options));

        // Single string is wrapped
        Map<String, Object> single = new HashMap<>();
        single.put("ignoreIframeSelectors", ".only");
        assertEquals(Collections.singletonList(".only"), percy.resolveIgnoreSelectors(single));

        // Missing -> empty (no-op)
        assertTrue(percy.resolveIgnoreSelectors(new HashMap<>()).isEmpty());

        // Non-array/non-string -> empty (no-op)
        Map<String, Object> bad = new HashMap<>();
        bad.put("ignoreIframeSelectors", 42);
        assertTrue(percy.resolveIgnoreSelectors(bad).isEmpty());

        // Per-snapshot wins over global config
        JSONObject config = new JSONObject();
        JSONObject snapshot = new JSONObject();
        snapshot.put("ignoreIframeSelectors", new JSONArray(Collections.singletonList(".global")));
        config.put("snapshot", snapshot);
        percy.cliConfig = config;
        Map<String, Object> per = new HashMap<>();
        per.put("ignoreIframeSelectors", Collections.singletonList(".per"));
        assertEquals(Collections.singletonList(".per"), percy.resolveIgnoreSelectors(per));
        // and falls back to global when per-snapshot absent
        assertEquals(Collections.singletonList(".global"),
                percy.resolveIgnoreSelectors(new HashMap<>()));
    }
}
