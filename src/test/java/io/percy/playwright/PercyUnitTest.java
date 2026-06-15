package io.percy.playwright;

import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.SameSiteAttribute;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the non-CLI logic in {@link Percy}.
 *
 * <p>The Percy CLI HTTP endpoints are emulated by a lightweight local
 * {@link HttpServer} bound to the default {@code PERCY_SERVER_ADDRESS}
 * (http://localhost:5338) so the HTTP wrapper methods ({@code request},
 * {@code fetchPercyDOM}, {@code getResponsiveWidths}, {@code log}) can be
 * exercised without a live CLI. Playwright {@link Page}/{@link Frame}
 * objects are mocked, mirroring the style used in {@code SDKTest}.</p>
 */
public class PercyUnitTest {

    private static HttpServer server;
    // Saved PERCY_SERVER_ADDRESS so we can restore it after redirecting the SDK
    // to our ephemeral stub port.
    private static String originalServerAddress;

    // Routed responses keyed by request path.
    private static final Map<String, StubResponse> ROUTES = new HashMap<>();
    // Captures the last POST body received per path.
    private static final Map<String, String> LAST_BODY = new HashMap<>();

    static class StubResponse {
        int status = 200;
        String body = "{}";
        Map<String, String> headers = new HashMap<>();

        StubResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    @BeforeAll
    public static void startStub() throws Exception {
        // Bind to an ephemeral port (0 -> OS-assigned) so we never collide with a
        // real Percy CLI, which owns localhost:5338 under `percy exec` on CI.
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                // Record the request body for assertion. Read the stream manually
                // for Java 8 compatibility (InputStream#readAllBytes is Java 9+).
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int read;
                try (InputStream reqStream = exchange.getRequestBody()) {
                    while ((read = reqStream.read(chunk)) != -1) {
                        buffer.write(chunk, 0, read);
                    }
                }
                LAST_BODY.put(path, new String(buffer.toByteArray(), StandardCharsets.UTF_8));

                StubResponse stub = ROUTES.getOrDefault(path, new StubResponse(200, "{}"));
                for (Map.Entry<String, String> h : stub.headers.entrySet()) {
                    exchange.getResponseHeaders().add(h.getKey(), h.getValue());
                }
                byte[] out = stub.body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(stub.status, out.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            }
        });
        server.setExecutor(null);
        server.start();

        // Redirect the SDK's HTTP calls to our stub for the duration of these tests.
        originalServerAddress = getStaticStringField("PERCY_SERVER_ADDRESS");
        setStaticStringField("PERCY_SERVER_ADDRESS",
                "http://localhost:" + server.getAddress().getPort());
    }

    @AfterAll
    public static void stopStub() throws Exception {
        if (server != null) {
            server.stop(0);
        }
        if (originalServerAddress != null) {
            setStaticStringField("PERCY_SERVER_ADDRESS", originalServerAddress);
        }
    }

    private static String getStaticStringField(String name) throws Exception {
        java.lang.reflect.Field f = Percy.class.getDeclaredField(name);
        f.setAccessible(true);
        return (String) f.get(null);
    }

    private static void setStaticStringField(String name, String value) throws Exception {
        java.lang.reflect.Field f = Percy.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static boolean getStaticBooleanField(String name) throws Exception {
        java.lang.reflect.Field f = Percy.class.getDeclaredField(name);
        f.setAccessible(true);
        return (Boolean) f.get(null);
    }

    private static void setStaticBooleanField(String name, boolean value) throws Exception {
        java.lang.reflect.Field f = Percy.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private void route(String path, int status, String body) {
        ROUTES.put(path, new StubResponse(status, body));
    }

    /**
     * Build a Percy instance against a mocked Page. The constructor runs
     * healthcheck() against the local stub server, so we control whether the
     * instance is "enabled" via the /percy/healthcheck route.
     */
    private Percy newPercy(Page page) {
        return new Percy(page);
    }

    // -------------------------------------------------------------------------
    // request() — HTTP POST wrapper
    // -------------------------------------------------------------------------

    @Test
    public void requestReturnsDataObjectWhenPresent() {
        route("/percy/snapshot", 200,
                "{\"success\":true,\"data\":{\"snapshot-name\":\"Foo\",\"status\":\"ok\"}}");

        Percy percy = newPercy(Mockito.mock(Page.class));
        JSONObject payload = new JSONObject();
        payload.put("name", "Foo");

        JSONObject result = percy.request("/percy/snapshot", payload, "Foo");
        assertNotNull(result);
        assertEquals("Foo", result.getString("snapshot-name"));
        assertEquals("ok", result.getString("status"));

        // Body should have been forwarded as JSON
        String sent = LAST_BODY.get("/percy/snapshot");
        assertNotNull(sent);
        assertTrue(sent.contains("Foo"));
    }

    @Test
    public void requestReturnsNullWhenNoDataKey() {
        route("/percy/snapshot", 200, "{\"success\":true}");

        Percy percy = newPercy(Mockito.mock(Page.class));
        JSONObject result = percy.request("/percy/snapshot", new JSONObject(), "NoData");
        assertNull(result);
    }

    @Test
    public void requestReturnsNullWhenResponseIsNotJson() {
        // Non-JSON body forces the JSONObject constructor to throw -> caught -> null.
        route("/percy/snapshot", 200, "this is not json");

        Percy percy = newPercy(Mockito.mock(Page.class));
        JSONObject result = percy.request("/percy/snapshot", new JSONObject(), "Bad");
        assertNull(result);
    }

    // -------------------------------------------------------------------------
    // log() — static logging wrapper
    // -------------------------------------------------------------------------

    @Test
    public void logInfoPostsToCliAndDoesNotThrow() {
        route("/percy/log", 200, "{}");
        assertDoesNotThrow(() -> Percy.log("hello world"));
        // The log message should have been posted to /percy/log
        String body = LAST_BODY.get("/percy/log");
        assertNotNull(body);
        assertTrue(body.contains("hello world"));
        assertTrue(body.contains("info"));
    }

    @Test
    public void logDebugLevelDoesNotThrow() {
        route("/percy/log", 200, "{}");
        assertDoesNotThrow(() -> Percy.log("debug detail", "debug"));
    }

    // -------------------------------------------------------------------------
    // createRegion() — additional branches not covered by SDKTest
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void createRegionDefaultsToIgnoreWithoutConfigOrAssertion() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        Map<String, Object> params = new HashMap<>();
        params.put("elementCSS", ".only-css");

        Map<String, Object> region = percy.createRegion(params);

        assertEquals("ignore", region.get("algorithm"));
        Map<String, Object> selector = (Map<String, Object>) region.get("elementSelector");
        assertEquals(".only-css", selector.get("elementCSS"));
        assertFalse(selector.containsKey("boundingBox"));
        assertFalse(selector.containsKey("elementXpath"));
        // With ignore algorithm and no diff/assertion options, neither block is attached.
        assertNull(region.get("configuration"));
        assertNull(region.get("assertion"));
        assertFalse(region.containsKey("padding"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void createRegionIntelliignoreAttachesConfiguration() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        Map<String, Object> params = new HashMap<>();
        params.put("algorithm", "intelliignore");
        params.put("elementXpath", "//x");
        params.put("diffSensitivity", 4);
        params.put("padding", 7);

        Map<String, Object> region = percy.createRegion(params);

        assertEquals("intelliignore", region.get("algorithm"));
        assertEquals(7, region.get("padding"));
        Map<String, Object> config = (Map<String, Object>) region.get("configuration");
        assertNotNull(config);
        assertEquals(4, config.get("diffSensitivity"));
    }

    @Test
    public void createRegionIgnoreAlgorithmDropsDiffOptions() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        Map<String, Object> params = new HashMap<>();
        params.put("algorithm", "ignore");
        params.put("elementCSS", ".x");
        // These should be ignored because algorithm is not standard/intelliignore.
        params.put("diffSensitivity", 4);
        params.put("carouselsEnabled", true);

        Map<String, Object> region = percy.createRegion(params);
        assertNull(region.get("configuration"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void createRegionWithBoundingBoxOnly() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        Map<String, Object> params = new HashMap<>();
        params.put("boundingBox", "1,2,3,4");
        params.put("diffIgnoreThreshold", 0.3);

        Map<String, Object> region = percy.createRegion(params);
        Map<String, Object> selector = (Map<String, Object>) region.get("elementSelector");
        assertEquals("1,2,3,4", selector.get("boundingBox"));
        Map<String, Object> assertion = (Map<String, Object>) region.get("assertion");
        assertNotNull(assertion);
        assertEquals(0.3, assertion.get("diffIgnoreThreshold"));
    }

    // -------------------------------------------------------------------------
    // setClientInfo() / getSdkVersion()
    // -------------------------------------------------------------------------

    @Test
    public void getSdkVersionReturnsSdkVersion() {
        assertEquals(Environment.getSdkVersion(), Percy.getSdkVersion());
    }

    @Test
    public void setClientInfoOverridesEnvironmentValues() throws Exception {
        Percy percy = newPercy(Mockito.mock(Page.class));
        percy.setClientInfo("custom-client/9.9.9", "custom-env; playwright");

        // Reach into the private env via reflection to assert the overrides took.
        java.lang.reflect.Field envField = Percy.class.getDeclaredField("env");
        envField.setAccessible(true);
        Environment env = (Environment) envField.get(percy);
        assertEquals("custom-client/9.9.9", env.getClientInfo());
        assertEquals("custom-env; playwright", env.getEnvironmentInfo());
    }

    // -------------------------------------------------------------------------
    // isCaptureResponsiveDOM() — branches not in SDKTest
    // -------------------------------------------------------------------------

    @Test
    public void isCaptureResponsiveDOMFalseWhenNothingEnabled() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        assertFalse(percy.isCaptureResponsiveDOM(new HashMap<>()));
    }

    @Test
    public void isCaptureResponsiveDOMFalseWhenDeferUploadsFalseAndNoFlag() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        JSONObject percyConfig = new JSONObject();
        percyConfig.put("deferUploads", false);
        JSONObject config = new JSONObject();
        config.put("percy", percyConfig);
        percy.cliConfig = config;

        assertFalse(percy.isCaptureResponsiveDOM(new HashMap<>()));
    }

    @Test
    public void isCaptureResponsiveDOMFalseWhenSdkOptionExplicitlyFalse() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        Map<String, Object> options = new HashMap<>();
        options.put("responsiveSnapshotCapture", false);
        assertFalse(percy.isCaptureResponsiveDOM(options));
    }

    @Test
    public void isCaptureResponsiveDOMFalseWhenSnapshotConfigPresentButFlagMissing() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        JSONObject snapshotConfig = new JSONObject();
        snapshotConfig.put("minHeight", 100);
        JSONObject config = new JSONObject();
        config.put("snapshot", snapshotConfig);
        percy.cliConfig = config;

        assertFalse(percy.isCaptureResponsiveDOM(new HashMap<>()));
    }

    // -------------------------------------------------------------------------
    // getSerializedDOM() — additional branches
    // -------------------------------------------------------------------------

    @Test
    public void getSerializedDOMThrowsWhenSerializeReturnsNull() {
        Page mockPage = Mockito.mock(Page.class);
        when(mockPage.evaluate(anyString())).thenReturn(null);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());

        Percy percy = newPercy(mockPage);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> percy.getSerializedDOM(new ArrayList<>(), "// dom", new HashMap<>()));
        assertTrue(ex.getMessage().contains("DOM serialization returned null"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getSerializedDOMSerializesSameSiteCookie() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());

        Percy percy = newPercy(mockPage);

        Cookie cookie = new Cookie("k", "v");
        cookie.domain = "example.com";
        cookie.path = "/";
        cookie.expires = -1.0;
        cookie.httpOnly = true;
        cookie.secure = true;
        cookie.sameSite = SameSiteAttribute.STRICT;

        Map<String, Object> result = percy.getSerializedDOM(
                Arrays.asList(cookie), "// dom", new HashMap<>());

        List<Map<String, Object>> cookies = (List<Map<String, Object>>) result.get("cookies");
        assertEquals(1, cookies.size());
        // sameSite is serialized via SameSiteAttribute.toString() (enum constant name).
        assertEquals(SameSiteAttribute.STRICT.toString(), cookies.get(0).get("sameSite"));
        assertEquals(true, cookies.get(0).get("httpOnly"));
        assertEquals(true, cookies.get(0).get("secure"));
    }

    @Test
    public void getSerializedDOMSkipsAboutBlankAndEmptyFrames() {
        Page mockPage = Mockito.mock(Page.class);
        Frame blank = Mockito.mock(Frame.class);
        Frame empty = Mockito.mock(Frame.class);
        when(blank.url()).thenReturn("about:blank");
        when(empty.url()).thenReturn("");

        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com/page");
        when(mockPage.frames()).thenReturn(Arrays.asList(blank, empty));

        Percy percy = newPercy(mockPage);
        Map<String, Object> result = percy.getSerializedDOM(new ArrayList<>(), "// dom", new HashMap<>());
        // No cross-origin frames captured.
        assertNull(result.get("corsIframes"));
    }

    @Test
    public void getSerializedDOMSkipsCorsDetectionForHostlessPage() {
        Page mockPage = Mockito.mock(Page.class);
        Frame crossFrame = Mockito.mock(Frame.class);
        when(crossFrame.url()).thenReturn("http://other.com/");

        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        // file: URL has no host -> CORS detection short-circuits to false for all frames.
        when(mockPage.url()).thenReturn("file:///tmp/index.html");
        when(mockPage.frames()).thenReturn(Arrays.asList(crossFrame));

        Percy percy = newPercy(mockPage);
        Map<String, Object> result = percy.getSerializedDOM(new ArrayList<>(), "// dom", new HashMap<>());
        assertNull(result.get("corsIframes"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getSerializedDOMReadinessUsesGlobalCliConfig() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");

        Map<String, Object> diagnostics = new HashMap<>();
        diagnostics.put("ok", true);
        when(mockPage.evaluate(anyString(), any(Map.class))).thenReturn(diagnostics);
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());

        Percy percy = newPercy(mockPage);
        // Global readiness config (no per-snapshot override) drives the merge.
        JSONObject readiness = new JSONObject();
        readiness.put("preset", "default");
        JSONObject snapshotConfig = new JSONObject();
        snapshotConfig.put("readiness", readiness);
        JSONObject config = new JSONObject();
        config.put("snapshot", snapshotConfig);
        percy.cliConfig = config;

        Map<String, Object> result = percy.getSerializedDOM(new ArrayList<>(), "// dom", new HashMap<>());
        assertEquals(diagnostics, result.get("readiness_diagnostics"));
        verify(mockPage, atLeastOnce()).evaluate(contains("waitForReady"), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getSerializedDOMReadinessDisabledViaGlobalConfigInheritedByPartialOverride() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());

        Percy percy = newPercy(mockPage);
        // Global says preset: disabled.
        JSONObject readiness = new JSONObject();
        readiness.put("preset", "disabled");
        JSONObject snapshotConfig = new JSONObject();
        snapshotConfig.put("readiness", readiness);
        JSONObject config = new JSONObject();
        config.put("snapshot", snapshotConfig);
        percy.cliConfig = config;

        // Partial per-snapshot override that does NOT set preset -> inherits "disabled".
        Map<String, Object> perSnap = new HashMap<>();
        perSnap.put("timeout", 5000);
        Map<String, Object> options = new HashMap<>();
        options.put("readiness", perSnap);

        Map<String, Object> result = percy.getSerializedDOM(new ArrayList<>(), "// dom", options);
        // waitForReady evaluate must NOT have been called because merged preset == disabled.
        verify(mockPage, never()).evaluate(contains("waitForReady"), any(Map.class));
        assertNull(result.get("readiness_diagnostics"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getSerializedDOMReadinessAcceptsJsonObjectPerSnapshot() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());

        Percy percy = newPercy(mockPage);
        // Per-snapshot readiness supplied as a JSONObject with preset disabled.
        JSONObject perSnap = new JSONObject();
        perSnap.put("preset", "disabled");
        Map<String, Object> options = new HashMap<>();
        options.put("readiness", perSnap);

        Map<String, Object> result = percy.getSerializedDOM(new ArrayList<>(), "// dom", options);
        verify(mockPage, never()).evaluate(contains("waitForReady"), any(Map.class));
        assertNull(result.get("readiness_diagnostics"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getSerializedDOMProcessFrameReturnsNullWhenNoPercyElementId() {
        Page mockPage = Mockito.mock(Page.class);
        Frame crossFrame = Mockito.mock(Frame.class);

        Map<String, Object> mainDom = new HashMap<>();
        mainDom.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(mainDom);
        // page.evaluate(js, frameUrl) returns null -> processFrame returns null (skip).
        when(mockPage.evaluate(anyString(), any())).thenReturn(null);
        when(mockPage.url()).thenReturn("http://example.com/page");
        when(crossFrame.url()).thenReturn("http://other.com/");

        Map<String, Object> iframeSnapshot = new HashMap<>();
        iframeSnapshot.put("html", "<iframe/>");
        when(crossFrame.evaluate(anyString())).thenReturn(null).thenReturn(iframeSnapshot);
        when(mockPage.frames()).thenReturn(Arrays.asList(crossFrame));

        Percy percy = newPercy(mockPage);
        Map<String, Object> result = percy.getSerializedDOM(new ArrayList<>(), "// dom", new HashMap<>());
        // No processed frames -> corsIframes not attached.
        assertNull(result.get("corsIframes"));
    }

    @Test
    public void getSerializedDOMProcessFrameSwallowsFrameEvaluateException() {
        Page mockPage = Mockito.mock(Page.class);
        Frame crossFrame = Mockito.mock(Frame.class);

        Map<String, Object> mainDom = new HashMap<>();
        mainDom.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(mainDom);
        when(mockPage.url()).thenReturn("http://example.com/page");
        when(crossFrame.url()).thenReturn("http://other.com/");
        // frame.evaluate throws -> processFrame catches and returns null.
        when(crossFrame.evaluate(anyString())).thenThrow(new RuntimeException("frame boom"));
        when(mockPage.frames()).thenReturn(Arrays.asList(crossFrame));

        Percy percy = newPercy(mockPage);
        Map<String, Object> result = percy.getSerializedDOM(new ArrayList<>(), "// dom", new HashMap<>());
        assertNull(result.get("corsIframes"));
    }

    // -------------------------------------------------------------------------
    // snapshot() / screenshot() guards when Percy disabled (no live CLI)
    // -------------------------------------------------------------------------

    @Test
    public void snapshotReturnsNullWhenPercyDisabled() {
        // No /percy/healthcheck route returning a valid percy header -> disabled.
        route("/percy/healthcheck", 500, "{}");
        Percy percy = newPercy(Mockito.mock(Page.class));
        // isPercyEnabled is false, so snapshot short-circuits to null.
        assertNull(percy.snapshot("disabled snapshot"));
    }

    @Test
    public void snapshotWithWidthsReturnsNullWhenDisabled() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        assertNull(percy.snapshot("n", Arrays.asList(800, 1200)));
    }

    @Test
    public void snapshotWithWidthsAndMinHeightReturnsNullWhenDisabled() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        assertNull(percy.snapshot("n", Arrays.asList(800), 600));
    }

    @Test
    public void snapshotWithEnableJsReturnsNullWhenDisabled() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        assertNull(percy.snapshot("n", Arrays.asList(800), 600, true));
    }

    @Test
    public void snapshotWithPercyCssReturnsNullWhenDisabled() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        assertNull(percy.snapshot("n", Arrays.asList(800), 600, true, "body{}"));
    }

    @Test
    public void snapshotWithScopeReturnsNullWhenDisabled() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        assertNull(percy.snapshot("n", Arrays.asList(800), 600, true, "body{}", "div"));
    }

    @Test
    public void snapshotWithSyncReturnsNullWhenDisabled() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        assertNull(percy.snapshot("n", Arrays.asList(800), 600, true, "body{}", "div", true));
    }

    @Test
    public void snapshotWithOptionsMapReturnsNullWhenDisabled() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        assertNull(percy.snapshot("n", new HashMap<>()));
    }

    @Test
    public void screenshotReturnsNullWhenPercyDisabled() throws Exception {
        Percy percy = newPercy(Mockito.mock(Page.class));
        assertNull(percy.screenshot("disabled screenshot"));
    }

    // -------------------------------------------------------------------------
    // healthcheck() variants exercised via constructor and cliConfig parsing
    // -------------------------------------------------------------------------

    @Test
    public void healthcheckParsesSessionTypeAndConfigWhenCliHealthy() {
        // A "healthy" CLI: 200 + x-percy-core-version: 1.x + JSON body with type/config.
        StubResponse stub = new StubResponse(200,
                "{\"type\":\"web\",\"config\":{\"snapshot\":{\"minHeight\":1024}}}");
        stub.headers.put("x-percy-core-version", "1.27.0");
        ROUTES.put("/percy/healthcheck", stub);
        try {
            Percy percy = newPercy(Mockito.mock(Page.class));
            assertEquals("web", percy.sessionType);
            assertTrue(percy.cliConfig.has("snapshot"));
            assertEquals(1024, percy.cliConfig.getJSONObject("snapshot").getInt("minHeight"));
        } finally {
            // Reset to non-healthy so other tests don't see a stale healthy CLI.
            ROUTES.remove("/percy/healthcheck");
        }
    }

    @Test
    public void healthcheckDisablesOnUnsupportedMajorVersion() {
        StubResponse stub = new StubResponse(200, "{}");
        stub.headers.put("x-percy-core-version", "2.0.0");
        ROUTES.put("/percy/healthcheck", stub);
        try {
            Percy percy = newPercy(Mockito.mock(Page.class));
            // Unsupported version -> isPercyEnabled false -> snapshot returns null.
            assertNull(percy.snapshot("x"));
        } finally {
            ROUTES.remove("/percy/healthcheck");
        }
    }

    // -------------------------------------------------------------------------
    // End-to-end snapshot path against a healthy stub CLI (mocked Page).
    // Exercises the enabled snapshot() body, fetchPercyDOM(), postSnapshot(),
    // and (for responsive) getResponsiveWidths() + captureResponsiveDom().
    // -------------------------------------------------------------------------

    /** Register a healthy /percy/healthcheck and /percy/dom.js, returning an enabled Percy. */
    private Percy newEnabledPercy(Page page, String configJson) {
        StubResponse health = new StubResponse(200, configJson);
        health.headers.put("x-percy-core-version", "1.27.0");
        ROUTES.put("/percy/healthcheck", health);
        route("/percy/dom.js", 200, "window.PercyDOM = {};");
        return new Percy(page);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void snapshotFullPathPostsSerializedDomWhenEnabled() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());
        // page.context().cookies()
        com.microsoft.playwright.BrowserContext ctx = Mockito.mock(com.microsoft.playwright.BrowserContext.class);
        when(mockPage.context()).thenReturn(ctx);
        when(ctx.cookies()).thenReturn(new ArrayList<>());

        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"E2E\"}}");

        try {
            Percy percy = newEnabledPercy(mockPage, "{\"type\":\"web\"}");
            JSONObject result = percy.snapshot("E2E");
            assertNotNull(result);
            assertEquals("E2E", result.getString("snapshot-name"));

            // The posted body must include the serialized DOM and not the readiness key.
            String body = LAST_BODY.get("/percy/snapshot");
            assertNotNull(body);
            assertTrue(body.contains("domSnapshot"));
            assertTrue(body.contains("E2E"));
        } finally {
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
        }
    }

    @Test
    public void snapshotSwallowsCookieCollectionFailure() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());
        // context() throws -> cookie collection fails but snapshot proceeds.
        when(mockPage.context()).thenThrow(new RuntimeException("no context"));

        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"NoCookies\"}}");
        try {
            Percy percy = newEnabledPercy(mockPage, "{\"type\":\"web\"}");
            JSONObject result = percy.snapshot("NoCookies");
            assertNotNull(result);
            assertEquals("NoCookies", result.getString("snapshot-name"));
        } finally {
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
        }
    }

    @Test
    public void snapshotReturnsNullWhenSerializeThrows() {
        Page mockPage = Mockito.mock(Page.class);
        // evaluate throws -> capture fails -> snapshot() catch returns null.
        when(mockPage.evaluate(anyString())).thenThrow(new RuntimeException("serialize boom"));
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());
        com.microsoft.playwright.BrowserContext ctx = Mockito.mock(com.microsoft.playwright.BrowserContext.class);
        when(mockPage.context()).thenReturn(ctx);
        when(ctx.cookies()).thenReturn(new ArrayList<>());

        try {
            Percy percy = newEnabledPercy(mockPage, "{\"type\":\"web\"}");
            assertNull(percy.snapshot("Boom"));
        } finally {
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void screenshotFullPathPostsAutomatePayloadWhenEnabled() throws Exception {
        // Automate screenshot path: requires a PageImpl for metadata, so spy + stub
        // setPageMetadata via reflection (mirrors SDKTest.takeScreenshot).
        Page mockPage = Mockito.mock(Page.class);
        route("/percy/automateScreenshot", 200, "{\"data\":{\"name\":\"Auto\"}}");
        try {
            Percy percy = spy(newEnabledPercy(mockPage, "{\"type\":\"automate\"}"));
            PageMetadata mockMeta = Mockito.mock(PageMetadata.class);
            Mockito.doAnswer(inv -> {
                java.lang.reflect.Field f = Percy.class.getDeclaredField("pageMetadata");
                f.setAccessible(true);
                f.set(percy, mockMeta);
                return null;
            }).when(percy).setPageMetadata();
            when(mockMeta.getSessionId()).thenReturn("sid");
            when(mockMeta.getPageGuid()).thenReturn("pg");
            when(mockMeta.getFrameGuid()).thenReturn("fg");
            when(mockMeta.getFramework()).thenReturn("playwright");

            JSONObject result = percy.screenshot("Auto");
            assertNotNull(result);
            String body = LAST_BODY.get("/percy/automateScreenshot");
            assertNotNull(body);
            assertTrue(body.contains("sid"));
            assertTrue(body.contains("playwright"));
        } finally {
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void responsiveSnapshotFetchesWidthsAndCapturesPerWidth() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());
        when(mockPage.viewportSize()).thenReturn(new com.microsoft.playwright.options.ViewportSize(1280, 720));
        com.microsoft.playwright.BrowserContext ctx = Mockito.mock(com.microsoft.playwright.BrowserContext.class);
        when(mockPage.context()).thenReturn(ctx);
        when(ctx.cookies()).thenReturn(new ArrayList<>());

        // CLI widths-config returns two width/height pairs.
        route("/percy/widths-config", 200,
                "{\"widths\":[{\"width\":480,\"height\":800},{\"width\":1200}]}");
        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"Resp\"}}");

        try {
            // CLI config enables responsive capture globally.
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"snapshot\":{\"responsiveSnapshotCapture\":true}}}");

            Map<String, Object> options = new HashMap<>();
            options.put("widths", Arrays.asList(480, 1200));
            options.put("responsiveSnapshotCapture", true);

            JSONObject result = percy.snapshot("Resp", options);
            assertNotNull(result);

            // widths-config endpoint must have been queried.
            assertTrue(LAST_BODY.containsKey("/percy/widths-config"));
            // Viewport was resized at least once during responsive capture.
            verify(mockPage, atLeastOnce()).setViewportSize(anyInt(), anyInt());
            // The posted snapshot carries a list of per-width DOMs.
            String body = LAST_BODY.get("/percy/snapshot");
            assertNotNull(body);
            assertTrue(body.contains("domSnapshot"));
        } finally {
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/widths-config");
        }
    }

    @Test
    public void responsiveWidthsConfigErrorAbortsSnapshotGracefully() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());
        when(mockPage.viewportSize()).thenReturn(new com.microsoft.playwright.options.ViewportSize(1280, 720));
        com.microsoft.playwright.BrowserContext ctx = Mockito.mock(com.microsoft.playwright.BrowserContext.class);
        when(mockPage.context()).thenReturn(ctx);
        when(ctx.cookies()).thenReturn(new ArrayList<>());

        // widths-config returns 404 -> getResponsiveWidths throws -> snapshot() catch -> null.
        route("/percy/widths-config", 404, "not found");
        try {
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"snapshot\":{\"responsiveSnapshotCapture\":true}}}");
            Map<String, Object> options = new HashMap<>();
            options.put("responsiveSnapshotCapture", true);
            assertNull(percy.snapshot("RespErr", options));
        } finally {
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/widths-config");
        }
    }

    @Test
    public void responsiveWidthsConfigMissingWidthsKeyAborts() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());
        when(mockPage.viewportSize()).thenReturn(new com.microsoft.playwright.options.ViewportSize(1280, 720));
        com.microsoft.playwright.BrowserContext ctx = Mockito.mock(com.microsoft.playwright.BrowserContext.class);
        when(mockPage.context()).thenReturn(ctx);
        when(ctx.cookies()).thenReturn(new ArrayList<>());

        // 200 but no "widths" key -> getResponsiveWidths throws -> snapshot null.
        route("/percy/widths-config", 200, "{\"foo\":1}");
        try {
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"snapshot\":{\"responsiveSnapshotCapture\":true}}}");
            Map<String, Object> options = new HashMap<>();
            options.put("responsiveSnapshotCapture", true);
            assertNull(percy.snapshot("RespNoWidths", options));
        } finally {
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/widths-config");
        }
    }

    @Test
    public void fetchPercyDomFailureDisablesSnapshot() {
        Page mockPage = Mockito.mock(Page.class);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());

        // Healthy healthcheck enables Percy, but dom.js returns 500 -> fetchPercyDOM
        // disables Percy and snapshot proceeds with empty script then posts.
        StubResponse health = new StubResponse(200, "{\"type\":\"web\"}");
        health.headers.put("x-percy-core-version", "1.27.0");
        ROUTES.put("/percy/healthcheck", health);
        route("/percy/dom.js", 500, "boom");
        try {
            Percy percy = new Percy(mockPage);
            // dom.js fetch fails inside snapshot; isPercyEnabled flips false; result is null.
            JSONObject result = percy.snapshot("DomFail");
            assertNull(result);
        } finally {
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
        }
    }

    /** Wire a mocked Page so a snapshot can be serialized and posted successfully. */
    private Page mockSerializablePage() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());
        com.microsoft.playwright.BrowserContext ctx =
                Mockito.mock(com.microsoft.playwright.BrowserContext.class);
        when(mockPage.context()).thenReturn(ctx);
        when(ctx.cookies()).thenReturn(new ArrayList<>());
        return mockPage;
    }

    @Test
    public void snapshotConvenienceOverloadsRunBodiesWhenEnabled() {
        Page mockPage = mockSerializablePage();
        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"ok\"}}");
        try {
            Percy percy = newEnabledPercy(mockPage, "{\"type\":\"web\"}");
            List<Integer> widths = Arrays.asList(768, 1200);

            // Each overload builds an options map then delegates to snapshot(name, options).
            assertNotNull(percy.snapshot("w", widths));
            assertNotNull(percy.snapshot("wh", widths, 600));
            assertNotNull(percy.snapshot("whj", widths, 600, true));
            assertNotNull(percy.snapshot("whjc", widths, 600, true, "body{}"));
            assertNotNull(percy.snapshot("whjcs", widths, 600, true, "body{}", "div"));
            assertNotNull(percy.snapshot("whjcsy", widths, 600, true, "body{}", "div", true));
        } finally {
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
        }
    }

    @Test
    public void responsiveCaptureToleratesViewportAndWaitFailures() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());
        when(mockPage.viewportSize()).thenReturn(new com.microsoft.playwright.options.ViewportSize(1280, 720));
        com.microsoft.playwright.BrowserContext ctx =
                Mockito.mock(com.microsoft.playwright.BrowserContext.class);
        when(mockPage.context()).thenReturn(ctx);
        when(ctx.cookies()).thenReturn(new ArrayList<>());
        // Both viewport resize and wait throw -> changeViewportAndWait swallows both.
        doThrow(new RuntimeException("resize boom")).when(mockPage).setViewportSize(anyInt(), anyInt());
        doThrow(new RuntimeException("wait boom")).when(mockPage)
                .waitForFunction(anyString(), any(), any(com.microsoft.playwright.Page.WaitForFunctionOptions.class));

        route("/percy/widths-config", 200, "{\"widths\":[{\"width\":480}]}");
        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"RespTol\"}}");
        try {
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"snapshot\":{\"responsiveSnapshotCapture\":true}}}");
            Map<String, Object> options = new HashMap<>();
            options.put("responsiveSnapshotCapture", true);
            JSONObject result = percy.snapshot("RespTol", options);
            assertNotNull(result);
        } finally {
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/widths-config");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getSerializedDOMHandlesUnparseableFrameUrl() {
        Page mockPage = Mockito.mock(Page.class);
        Frame badFrame = Mockito.mock(Frame.class);
        // A URL that the URI parser will reject -> frameHost lookup throws -> filtered out.
        when(badFrame.url()).thenReturn("ht!tp://[bad uri");

        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com/page");
        when(mockPage.frames()).thenReturn(Arrays.asList(badFrame));

        Percy percy = newPercy(mockPage);
        Map<String, Object> result = percy.getSerializedDOM(new ArrayList<>(), "// dom", new HashMap<>());
        assertNull(result.get("corsIframes"));
    }

    // -------------------------------------------------------------------------
    // healthcheck(): legacy @percy/agent branch (null core version) via seam.
    // -------------------------------------------------------------------------

    @Test
    public void healthcheckDisablesWhenCoreVersionHeaderMissing() throws Exception {
        // Healthy 200 + version header present, but the getCoreVersion seam reports
        // null (mirrors an @percy/agent response with no x-percy-core-version value).
        StubResponse stub = new StubResponse(200, "{}");
        stub.headers.put("x-percy-core-version", "1.27.0");
        ROUTES.put("/percy/healthcheck", stub);
        try {
            Percy percy = new Percy(Mockito.mock(Page.class)) {
                @Override
                protected String getCoreVersion(org.apache.http.HttpResponse response) {
                    return null;
                }
            };
            // version == null -> healthcheck returns false -> Percy disabled.
            assertNull(percy.snapshot("agent"));
        } finally {
            ROUTES.remove("/percy/healthcheck");
        }
    }

    // -------------------------------------------------------------------------
    // setPageMetadata(): constructs a PageMetadata for the current page.
    // -------------------------------------------------------------------------

    @Test
    public void setPageMetadataCreatesMetadataForPage() throws Exception {
        Page mockPage = Mockito.mock(Page.class);
        Percy percy = newPercy(mockPage);
        percy.setPageMetadata();

        java.lang.reflect.Field f = Percy.class.getDeclaredField("pageMetadata");
        f.setAccessible(true);
        Object meta = f.get(percy);
        assertNotNull(meta);
        assertTrue(meta instanceof PageMetadata);
    }

    // -------------------------------------------------------------------------
    // log(): catch branch when the CLI is unreachable AND debug logging is on.
    // -------------------------------------------------------------------------

    @Test
    public void logCatchBranchRunsWhenCliUnreachableAndDebugEnabled() throws Exception {
        boolean originalDebug = getStaticBooleanField("PERCY_DEBUG");
        String currentAddr = getStaticStringField("PERCY_SERVER_ADDRESS");
        try {
            // Point at a closed port so httpClient.execute throws, and enable debug
            // so the catch body (System.out print) executes.
            setStaticStringField("PERCY_SERVER_ADDRESS", "http://localhost:1");
            setStaticBooleanField("PERCY_DEBUG", true);
            assertDoesNotThrow(() -> Percy.log("unreachable log", "info"));
        } finally {
            setStaticBooleanField("PERCY_DEBUG", originalDebug);
            setStaticStringField("PERCY_SERVER_ADDRESS", currentAddr);
        }
    }

    // -------------------------------------------------------------------------
    // getResponsiveWidths(): checked-Exception catch when CLI unreachable.
    // -------------------------------------------------------------------------

    @Test
    public void getResponsiveWidthsThrowsRuntimeWhenWidthsConfigUnreachable() throws Exception {
        Page mockPage = mockSerializablePage();
        when(mockPage.viewportSize())
                .thenReturn(new com.microsoft.playwright.options.ViewportSize(1280, 720));
        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"x\"}}");
        String currentAddr = getStaticStringField("PERCY_SERVER_ADDRESS");
        try {
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"snapshot\":{\"responsiveSnapshotCapture\":true}}}");
            // Now point the SDK at a closed port; the widths-config GET will throw an
            // IOException (a checked Exception), exercising the catch(Exception) branch.
            setStaticStringField("PERCY_SERVER_ADDRESS", "http://localhost:1");
            Map<String, Object> options = new HashMap<>();
            options.put("responsiveSnapshotCapture", true);
            // snapshot() swallows the RuntimeException from getResponsiveWidths and returns null.
            assertNull(percy.snapshot("RespUnreachable", options));
        } finally {
            setStaticStringField("PERCY_SERVER_ADDRESS", currentAddr);
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/snapshot");
        }
    }

    // -------------------------------------------------------------------------
    // captureResponsiveDom(): reload-page branch + sleep-time branches.
    // -------------------------------------------------------------------------

    @Test
    public void responsiveCaptureReloadsPageWhenReloadFlagEnabled() throws Exception {
        Page mockPage = mockSerializablePage();
        when(mockPage.viewportSize())
                .thenReturn(new com.microsoft.playwright.options.ViewportSize(1280, 720));
        route("/percy/widths-config", 200, "{\"widths\":[{\"width\":480},{\"width\":1200}]}");
        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"Reload\"}}");

        boolean originalReload = getStaticBooleanField("PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE");
        try {
            setStaticBooleanField("PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE", true);
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"snapshot\":{\"responsiveSnapshotCapture\":true}}}");
            Map<String, Object> options = new HashMap<>();
            options.put("responsiveSnapshotCapture", true);
            JSONObject result = percy.snapshot("Reload", options);
            assertNotNull(result);
            // page.reload() must have been triggered per width because reload flag is on.
            verify(mockPage, atLeastOnce()).reload();
        } finally {
            setStaticBooleanField("PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE", originalReload);
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/widths-config");
            ROUTES.remove("/percy/snapshot");
        }
    }

    @Test
    public void responsiveCaptureParsesNonNumericSleepTimeWithoutSleeping() throws Exception {
        Page mockPage = mockSerializablePage();
        when(mockPage.viewportSize())
                .thenReturn(new com.microsoft.playwright.options.ViewportSize(1280, 720));
        route("/percy/widths-config", 200, "{\"widths\":[{\"width\":480}]}");
        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"BadSleep\"}}");

        String originalSleep = getStaticStringField("RESPONSIVE_CAPTURE_SLEEP_TIME");
        try {
            // Non-numeric sleep time -> Integer.parseInt throws NumberFormatException
            // which is caught and ignored (no actual sleep).
            setStaticStringField("RESPONSIVE_CAPTURE_SLEEP_TIME", "abc");
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"snapshot\":{\"responsiveSnapshotCapture\":true}}}");
            Map<String, Object> options = new HashMap<>();
            options.put("responsiveSnapshotCapture", true);
            assertNotNull(percy.snapshot("BadSleep", options));
        } finally {
            setStaticStringField("RESPONSIVE_CAPTURE_SLEEP_TIME", originalSleep);
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/widths-config");
            ROUTES.remove("/percy/snapshot");
        }
    }

    @Test
    public void responsiveCaptureBreaksWhenSleepInterrupted() throws Exception {
        Page mockPage = mockSerializablePage();
        when(mockPage.viewportSize())
                .thenReturn(new com.microsoft.playwright.options.ViewportSize(1280, 720));
        route("/percy/widths-config", 200, "{\"widths\":[{\"width\":480},{\"width\":1200}]}");
        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"Interrupt\"}}");

        String originalSleep = getStaticStringField("RESPONSIVE_CAPTURE_SLEEP_TIME");
        try {
            // Positive sleep time; pre-set the interrupt flag so Thread.sleep throws
            // InterruptedException immediately, exercising the interrupt/break branch.
            setStaticStringField("RESPONSIVE_CAPTURE_SLEEP_TIME", "1");
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"snapshot\":{\"responsiveSnapshotCapture\":true}}}");
            Map<String, Object> options = new HashMap<>();
            options.put("responsiveSnapshotCapture", true);

            Thread.currentThread().interrupt();
            JSONObject result = percy.snapshot("Interrupt", options);
            // The capture loop breaks early on interrupt; snapshot still posts the
            // DOMs gathered so far (one width).
            assertNotNull(result);
        } finally {
            // Clear the interrupt flag we set so it doesn't leak into other tests.
            Thread.interrupted();
            setStaticStringField("RESPONSIVE_CAPTURE_SLEEP_TIME", originalSleep);
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/widths-config");
            ROUTES.remove("/percy/snapshot");
        }
    }

    // -------------------------------------------------------------------------
    // calculateDefaultHeight(): PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT enabled.
    // -------------------------------------------------------------------------

    @Test
    public void responsiveCaptureUsesMinHeightFromOptionsWhenMinHeightFlagEnabled() throws Exception {
        Page mockPage = mockSerializablePage();
        when(mockPage.viewportSize())
                .thenReturn(new com.microsoft.playwright.options.ViewportSize(1280, 720));
        // Width entries without height force the default-height calculation path.
        route("/percy/widths-config", 200, "{\"widths\":[{\"width\":480}]}");
        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"MinH\"}}");

        boolean originalMinHeight = getStaticBooleanField("PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT");
        try {
            setStaticBooleanField("PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", true);
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"snapshot\":{\"responsiveSnapshotCapture\":true}}}");
            Map<String, Object> options = new HashMap<>();
            options.put("responsiveSnapshotCapture", true);
            options.put("minHeight", 1500); // exercises the Number branch.
            assertNotNull(percy.snapshot("MinH", options));
        } finally {
            setStaticBooleanField("PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", originalMinHeight);
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/widths-config");
            ROUTES.remove("/percy/snapshot");
        }
    }

    @Test
    public void responsiveCaptureUsesMinHeightFromCliConfigWhenMinHeightFlagEnabled() throws Exception {
        Page mockPage = mockSerializablePage();
        when(mockPage.viewportSize())
                .thenReturn(new com.microsoft.playwright.options.ViewportSize(1280, 720));
        route("/percy/widths-config", 200, "{\"widths\":[{\"width\":480}]}");
        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"MinHCli\"}}");

        boolean originalMinHeight = getStaticBooleanField("PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT");
        try {
            setStaticBooleanField("PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", true);
            // No minHeight option -> falls through to cliConfig.snapshot.minHeight (an int).
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"snapshot\":{\"responsiveSnapshotCapture\":true,\"minHeight\":1300}}}");
            Map<String, Object> options = new HashMap<>();
            options.put("responsiveSnapshotCapture", true);
            assertNotNull(percy.snapshot("MinHCli", options));
        } finally {
            setStaticBooleanField("PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", originalMinHeight);
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/widths-config");
            ROUTES.remove("/percy/snapshot");
        }
    }

    @Test
    public void responsiveCaptureDefaultHeightSwallowsExceptionWhenMinHeightConfigInvalid() throws Exception {
        Page mockPage = mockSerializablePage();
        when(mockPage.viewportSize())
                .thenReturn(new com.microsoft.playwright.options.ViewportSize(1280, 720));
        route("/percy/widths-config", 200, "{\"widths\":[{\"width\":480}]}");
        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"MinHErr\"}}");

        boolean originalMinHeight = getStaticBooleanField("PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT");
        try {
            setStaticBooleanField("PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", true);
            // snapshot.minHeight is a non-numeric string -> getInt() throws inside the
            // try, exercising calculateDefaultHeight's catch branch.
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"snapshot\":{\"responsiveSnapshotCapture\":true,\"minHeight\":\"oops\"}}}");
            Map<String, Object> options = new HashMap<>();
            options.put("responsiveSnapshotCapture", true);
            assertNotNull(percy.snapshot("MinHErr", options));
        } finally {
            setStaticBooleanField("PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", originalMinHeight);
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/widths-config");
            ROUTES.remove("/percy/snapshot");
        }
    }

    // -------------------------------------------------------------------------
    // getSerializedDOM(): cross-origin processing block catch (bad page URL).
    // -------------------------------------------------------------------------

    @Test
    public void getSerializedDOMSwallowsCorsProcessingExceptionOnBadPageUrl() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        // A URL with an illegal space makes `new URI(page.url())` throw inside the
        // cross-origin block; the catch logs and proceeds.
        when(mockPage.url()).thenReturn("http://exa mple.com/page");
        when(mockPage.frames()).thenReturn(new ArrayList<>());

        Percy percy = newPercy(mockPage);
        Map<String, Object> result = percy.getSerializedDOM(new ArrayList<>(), "// dom", new HashMap<>());
        // Snapshot still produced; no corsIframes attached.
        assertNotNull(result);
        assertNull(result.get("corsIframes"));
    }

    // -------------------------------------------------------------------------
    // createRegion(): standard algorithm attaches every configuration key.
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void createRegionStandardAttachesAllConfigurationKeys() {
        Percy percy = newPercy(Mockito.mock(Page.class));
        Map<String, Object> params = new HashMap<>();
        params.put("algorithm", "standard");
        params.put("elementCSS", ".banner");
        params.put("diffSensitivity", 3);
        params.put("imageIgnoreThreshold", 0.1);
        params.put("carouselsEnabled", true);
        params.put("bannersEnabled", false);
        params.put("adsEnabled", true);
        params.put("diffIgnoreThreshold", 0.25);

        Map<String, Object> region = percy.createRegion(params);

        assertEquals("standard", region.get("algorithm"));
        Map<String, Object> config = (Map<String, Object>) region.get("configuration");
        assertNotNull(config);
        assertEquals(3, config.get("diffSensitivity"));
        assertEquals(0.1, config.get("imageIgnoreThreshold"));
        assertEquals(true, config.get("carouselsEnabled"));
        assertEquals(false, config.get("bannersEnabled"));
        assertEquals(true, config.get("adsEnabled"));
        Map<String, Object> assertion = (Map<String, Object>) region.get("assertion");
        assertNotNull(assertion);
        assertEquals(0.25, assertion.get("diffIgnoreThreshold"));
    }

    // -------------------------------------------------------------------------
    // isCaptureResponsiveDOM(): percy.deferUploads=true forces the non-responsive
    // path even when responsiveSnapshotCapture is requested.
    // -------------------------------------------------------------------------

    @Test
    public void deferUploadsDisablesResponsiveCaptureAndUsesPlainSnapshot() {
        Page mockPage = mockSerializablePage();
        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"Defer\"}}");
        try {
            // deferUploads=true short-circuits isCaptureResponsiveDOM -> plain serialize,
            // so /percy/widths-config is never queried.
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"percy\":{\"deferUploads\":true},"
                    + "\"snapshot\":{\"responsiveSnapshotCapture\":true}}}");
            Map<String, Object> options = new HashMap<>();
            options.put("responsiveSnapshotCapture", true);
            JSONObject result = percy.snapshot("Defer", options);
            assertNotNull(result);
            // Responsive capture is the only path that resizes the viewport; deferUploads
            // forces the plain serialize path, so the viewport is never resized.
            verify(mockPage, never()).setViewportSize(anyInt(), anyInt());
        } finally {
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/snapshot");
        }
    }

    // -------------------------------------------------------------------------
    // captureResponsiveDom(): positive sleep time -> Thread.sleep completes.
    // -------------------------------------------------------------------------

    @Test
    public void responsiveCaptureSleepsBetweenWidthsWhenSleepTimePositive() throws Exception {
        Page mockPage = mockSerializablePage();
        when(mockPage.viewportSize())
                .thenReturn(new com.microsoft.playwright.options.ViewportSize(1280, 720));
        // Single width keeps the (1-second) sleep to a single iteration.
        route("/percy/widths-config", 200, "{\"widths\":[{\"width\":480}]}");
        route("/percy/snapshot", 200, "{\"data\":{\"snapshot-name\":\"Sleep\"}}");

        String originalSleep = getStaticStringField("RESPONSIVE_CAPTURE_SLEEP_TIME");
        try {
            // "1" -> sleepMs = 1000 (> 0) -> Thread.sleep actually runs to completion.
            setStaticStringField("RESPONSIVE_CAPTURE_SLEEP_TIME", "1");
            Percy percy = newEnabledPercy(mockPage,
                    "{\"type\":\"web\",\"config\":{\"snapshot\":{\"responsiveSnapshotCapture\":true}}}");
            Map<String, Object> options = new HashMap<>();
            options.put("responsiveSnapshotCapture", true);
            assertNotNull(percy.snapshot("Sleep", options));
        } finally {
            setStaticStringField("RESPONSIVE_CAPTURE_SLEEP_TIME", originalSleep);
            ROUTES.remove("/percy/healthcheck");
            ROUTES.remove("/percy/dom.js");
            ROUTES.remove("/percy/widths-config");
            ROUTES.remove("/percy/snapshot");
        }
    }

    // -------------------------------------------------------------------------
    // getSerializedDOM(): cross-origin frame processed successfully -> corsIframes.
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void getSerializedDOMAttachesCorsIframeWhenFrameProcessedSuccessfully() {
        Page mockPage = Mockito.mock(Page.class);
        Frame crossFrame = Mockito.mock(Frame.class);

        Map<String, Object> mainDom = new HashMap<>();
        mainDom.put("html", "<html></html>");
        // page.evaluate(serializeJs) -> main DOM.
        when(mockPage.evaluate(anyString())).thenReturn(mainDom);
        // page.evaluate(js, frameUrl) -> iframe element lookup returns a percyElementId.
        Map<String, Object> iframeData = new HashMap<>();
        iframeData.put("percyElementId", "el-123");
        when(mockPage.evaluate(anyString(), any())).thenReturn(iframeData);
        when(mockPage.url()).thenReturn("http://example.com/page");
        when(crossFrame.url()).thenReturn("http://other.com/widget");

        // frame.evaluate(domScript) (void) then frame.evaluate(serialize) -> iframe snapshot.
        Map<String, Object> iframeSnapshot = new HashMap<>();
        iframeSnapshot.put("html", "<iframe-content/>");
        when(crossFrame.evaluate(anyString())).thenReturn(null).thenReturn(iframeSnapshot);
        when(mockPage.frames()).thenReturn(Arrays.asList(crossFrame));

        Percy percy = newPercy(mockPage);
        Map<String, Object> result =
                percy.getSerializedDOM(new ArrayList<>(), "// dom", new HashMap<>());

        // The processed cross-origin frame must be attached under corsIframes.
        List<Map<String, Object>> corsIframes =
                (List<Map<String, Object>>) result.get("corsIframes");
        assertNotNull(corsIframes);
        assertEquals(1, corsIframes.size());
        Map<String, Object> processed = corsIframes.get(0);
        assertEquals("http://other.com/widget", processed.get("frameUrl"));
        assertEquals(iframeSnapshot, processed.get("iframeSnapshot"));
        Map<String, Object> data = (Map<String, Object>) processed.get("iframeData");
        assertEquals("el-123", data.get("percyElementId"));
    }

    // -------------------------------------------------------------------------
    // waitForReady(): page.evaluate throws -> catch logs and returns null.
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void waitForReadySwallowsEvaluateExceptionAndProceeds() {
        Page mockPage = Mockito.mock(Page.class);
        Map<String, Object> mainDom = new HashMap<>();
        mainDom.put("html", "<html></html>");
        // Single-arg evaluate (the serialize call) succeeds.
        when(mockPage.evaluate(anyString())).thenReturn(mainDom);
        // Two-arg evaluate is the waitForReady call -> throw, exercising the catch branch.
        when(mockPage.evaluate(anyString(), any())).thenThrow(new RuntimeException("ready boom"));
        when(mockPage.url()).thenReturn("http://example.com/page");
        when(mockPage.frames()).thenReturn(new ArrayList<>());

        Percy percy = newPercy(mockPage);
        // No readiness option -> merged config is empty -> preset != "disabled" -> enters
        // the try, evaluate throws, catch returns null; serialize still produces a snapshot.
        Map<String, Object> result =
                percy.getSerializedDOM(new ArrayList<>(), "// dom", new HashMap<>());
        assertNotNull(result);
        assertNull(result.get("readiness_diagnostics"));
    }
}
