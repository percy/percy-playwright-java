package io.percy.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SDKTest {
    private static final String TEST_URL = "http://localhost:8000";
    private static Browser browser;
    private static BrowserContext context;
    private static Page page;
    private static Percy percy;

    @BeforeAll
    public static void testSetup() throws IOException {
        TestServer.startServer();
        Playwright playwright = Playwright.create();
        browser = playwright.firefox().launch(new BrowserType.LaunchOptions().setHeadless(true));
        context = browser.newContext();
        page = context.newPage();
        percy = new Percy(page);
    }

    @AfterAll
    public static void testTeardown() {
        context.close();
        browser.close();
    }

    @BeforeEach
    public void setSessionType() {
        percy.sessionType = "web";
    }

    @Test
    @Order(1)
    public void takesLocalAppSnapshotWithProvidedName() {
        page.navigate(TEST_URL);
        percy.snapshot("Snapshot with provided name");
    }

    @Test
    @Order(2)
    public void snapshotWithOptions() {
        page.navigate("https://example.com");
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("percyCSS", "body { background-color: purple }");
        options.put("domTransformation", "(documentElement) => documentElement.querySelector('body').style.color = 'green';");
        options.put("scope", "div");
        options.put("widths", Arrays.asList(768, 992, 1200));
        percy.snapshot("Site with options", options);
    }

    @Test
    @Order(3)
    public void takeSnapshotWithSyncCLI() {
        page.navigate("https://example.com");
        Map<String, Object> options = new HashMap<>();
        options.put("sync", true);

        JSONObject data = percy.snapshot("test_sync_cli_snapshot", options);
        System.out.println(data);
        assertEquals("test_sync_cli_snapshot", data.getString("snapshot-name"));
        assertEquals("success", data.getString("status"));
        assertTrue(data.get("screenshots") instanceof org.json.JSONArray);
    }

    @Test
    @Order(4)
    public void takesMultipleSnapshotsInOneTestCase() {
        page.navigate(TEST_URL);

        page.fill(".new-todo", "A new todo to check off");
        page.keyboard().press("Enter");
        percy.snapshot("Multiple snapshots in one test case -- #1");

        page.click("input.toggle");
        percy.snapshot("Multiple snapshots in one test case -- #2");
    }

    @Test
    @Order(5)
    public void takeSnapshotThrowErrorForPOA() {
        percy.sessionType = "automate";
        Throwable exception = assertThrows(RuntimeException.class, () -> percy.snapshot("Test"));
        assertEquals("Invalid function call - snapshot(). Please use screenshot() function while using Percy with Automate. For more information on usage of PercyScreenshot, refer https://www.browserstack.com/docs/percy/integrate/functional-and-visual", exception.getMessage());
    }

    @Test
    @Order(6)
    public void snapshotWithResponsiveSnapshotCapture() {
        page.navigate(TEST_URL + "/responsive-capture.html");
        Map<String, Object> options = new HashMap<>();
        options.put("widths", Arrays.asList(480, 680, 992, 1200));
        options.put("responsiveSnapshotCapture", true);
        percy.snapshot("Site with responsive snapshot capture", options);
    }

    @Test
    @Order(7)
    public void snapshotWithCorsIframe() {
        // cors-iframe.html embeds https://todomvc.com/examples/react/dist/ inside an iframe, making it
        // a genuine cross-origin frame for Percy to detect and capture.
        List<String> allowedHostnames = Arrays.asList("todomvc.com");
        Map<String, Object> discoveryOptions = new HashMap<>();
        discoveryOptions.put("allowedHostnames", allowedHostnames);
        Map<String, Object> percyOptions = new HashMap<>();
        percyOptions.put("discovery", discoveryOptions);
        page.navigate(TEST_URL + "/cors-iframe.html");
        percy.snapshot("Page with cross-origin iframe", percyOptions);
    }

    @Test
    @Order(8)
    public void takeScreenshotThrowErrorForWeb() throws Exception {
        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        Page page = browser.newPage();
        Percy percy = new Percy(page);
        percy.sessionType = "web";

        Throwable exception = assertThrows(RuntimeException.class, () -> percy.screenshot("Test"));
        assertEquals("Invalid function call - screenshot(). Please use snapshot() function for taking screenshot. screenshot() should be used only while using Percy with Automate. For more information on usage of snapshot(), refer doc for your language https://www.browserstack.com/docs/percy/integrate/overview", exception.getMessage());

        page.close();
        browser.close();
        playwright.close();
    }

    @Test
    @Order(9)
    public void takeScreenshot() throws Exception {
        // Mock Page and dependencies
        Page mockPage = Mockito.mock(Page.class);
        percy = spy(new Percy(mockPage));
        percy.sessionType = "automate";
        PageMetadata mockPageMetadata = Mockito.mock(PageMetadata.class);

        Mockito.doAnswer(invocation -> {
            // Use reflection to set private field pageMetadata in Percy
            try {
                java.lang.reflect.Field field = Percy.class.getDeclaredField("pageMetadata");
                field.setAccessible(true);
                field.set(percy, mockPageMetadata);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set private field 'pageMetadata'");
            }
            return null; // setPageMetadata has void return type
        }).when(percy).setPageMetadata();
        when(mockPageMetadata.getPageGuid()).thenReturn("page@123");
        when(mockPageMetadata.getFrameGuid()).thenReturn("frame@123");
        when(mockPageMetadata.getBrowserGuid()).thenReturn("browser@123");
        when(mockPageMetadata.getFramework()).thenReturn("playwright");
        when(mockPageMetadata.getSessionId()).thenReturn("123");

        JSONObject json = new JSONObject();
        Map<String, Object> options = new HashMap<String, Object>();
        json.put("sessionId", mockPageMetadata.getSessionId());
        json.put("pageGuid", mockPageMetadata.getPageGuid());
        json.put("frameGuid", mockPageMetadata.getFrameGuid());
        json.put("framework", mockPageMetadata.getFramework());
        json.put("snapshotName", "Test");
        json.put("options", options);

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        // Call the screenshot method
        percy.screenshot("Test");

        // Verify the request method was called with the expected arguments
        verify(percy).request(eq("/percy/automateScreenshot"), captor.capture(), eq("Test"));
        // Retrieve the captured JSON object
        JSONObject capturedJson = captor.getValue();

        // Validate the contents of the captured JSON object
        assertEquals(json.getString("sessionId"), capturedJson.getString("sessionId"));
        assertEquals(json.getString("pageGuid"), capturedJson.getString("pageGuid"));
        assertEquals(json.getString("frameGuid"), capturedJson.getString("frameGuid"));
        assertEquals(json.getString("snapshotName"), capturedJson.getString("snapshotName"));
        assertEquals(json.getString("framework"), capturedJson.getString("framework"));
        assertTrue(json.getJSONObject("options").similar(capturedJson.getJSONObject("options")));
    }

    @Test
    @Order(10)
    public void takeScreenshotWithOptions() throws Exception {
        // Mock Page and dependencies
        Page mockPage = Mockito.mock(Page.class);
        percy = spy(new Percy(mockPage));
        percy.sessionType = "automate";
        PageMetadata mockPageMetadata = Mockito.mock(PageMetadata.class);

        Mockito.doAnswer(invocation -> {
            // Use reflection to set private field pageMetadata in Percy
            try {
                java.lang.reflect.Field field = Percy.class.getDeclaredField("pageMetadata");
                field.setAccessible(true);
                field.set(percy, mockPageMetadata);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set private field 'pageMetadata'");
            }
            return null; // setPageMetadata has void return type
        }).when(percy).setPageMetadata();
        when(mockPageMetadata.getPageGuid()).thenReturn("page@123");
        when(mockPageMetadata.getFrameGuid()).thenReturn("frame@123");
        when(mockPageMetadata.getBrowserGuid()).thenReturn("browser@123");
        when(mockPageMetadata.getFramework()).thenReturn("playwright");
        when(mockPageMetadata.getSessionId()).thenReturn("123");

        JSONObject json = new JSONObject();
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("percyCSS", "h1{color:black;}");
        options.put("sync", true);
        json.put("sessionId", mockPageMetadata.getSessionId());
        json.put("pageGuid", mockPageMetadata.getPageGuid());
        json.put("frameGuid", mockPageMetadata.getFrameGuid());
        json.put("framework", mockPageMetadata.getFramework());
        json.put("snapshotName", "Test");
        json.put("options", options);

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        // Call the screenshot method
        percy.screenshot("Test", options);

        // Verify the request method was called with the expected arguments
        verify(percy).request(eq("/percy/automateScreenshot"), captor.capture(), eq("Test"));
        // Retrieve the captured JSON object
        JSONObject capturedJson = captor.getValue();

        // Validate the contents of the captured JSON object
        assertEquals(json.getString("sessionId"), capturedJson.getString("sessionId"));
        assertEquals(json.getString("pageGuid"), capturedJson.getString("pageGuid"));
        assertEquals(json.getString("frameGuid"), capturedJson.getString("frameGuid"));
        assertEquals(json.getString("snapshotName"), capturedJson.getString("snapshotName"));
        assertEquals(json.getString("framework"), capturedJson.getString("framework"));
        assertTrue(json.getJSONObject("options").similar(capturedJson.getJSONObject("options")));
    }

    @Test
    @Order(11)
    public void createRegionTest() {
        // Setup the parameters for the region
        Map<String, Object> params = new HashMap<>();
        params.put("boundingBox", "100,100,200,200");
        params.put("elementXpath", "//div[@id='test']");
        params.put("elementCSS", ".test-class");
        params.put("padding", 10);
        params.put("algorithm", "standard");
        params.put("diffSensitivity", 0.5);
        params.put("imageIgnoreThreshold", 0.2);
        params.put("carouselsEnabled", true);
        params.put("bannersEnabled", false);
        params.put("adsEnabled", true);
        params.put("diffIgnoreThreshold", 0.1);

        // Call the method to create the region
        Map<String, Object> region = percy.createRegion(params);

        // Validate the returned region
        assertNotNull(region);
        
        // Check if elementSelector was added correctly
        Map<String, Object> elementSelector = (Map<String, Object>) region.get("elementSelector");
        assertNotNull(elementSelector);
        assertEquals("100,100,200,200", elementSelector.get("boundingBox"));
        assertEquals("//div[@id='test']", elementSelector.get("elementXpath"));
        assertEquals(".test-class", elementSelector.get("elementCSS"));

        // Validate algorithm and configuration
        assertEquals("standard", region.get("algorithm"));
        
        Map<String, Object> configuration = (Map<String, Object>) region.get("configuration");
        assertNotNull(configuration);
        assertEquals(0.5, configuration.get("diffSensitivity"));
        assertEquals(0.2, configuration.get("imageIgnoreThreshold"));
        assertTrue((Boolean) configuration.get("carouselsEnabled"));
        assertFalse((Boolean) configuration.get("bannersEnabled"));
        assertTrue((Boolean) configuration.get("adsEnabled"));
        
        // Validate assertion
        Map<String, Object> assertion = (Map<String, Object>) region.get("assertion");
        assertNotNull(assertion);
        assertEquals(0.1, assertion.get("diffIgnoreThreshold"));
    }

    // -------------------------------------------------------------------------
    // Responsive snapshot capture tests
    // -------------------------------------------------------------------------

    @Test
    @Order(12)
    public void isCaptureResponsiveDOMReturnsTrueForSDKOption() throws Exception {
        Page mockPage = Mockito.mock(Page.class);
        Percy percyInstance = new Percy(mockPage);

        java.lang.reflect.Method method =
                Percy.class.getDeclaredMethod("isCaptureResponsiveDOM", Map.class);
        method.setAccessible(true);

        Map<String, Object> options = new HashMap<>();
        options.put("responsiveSnapshotCapture", true);

        boolean result = (boolean) method.invoke(percyInstance, options);
        assertTrue(result);
    }

    @Test
    @Order(13)
    public void isCaptureResponsiveDOMReturnsFalseWhenDeferUploadsEnabled() throws Exception {
        Page mockPage = Mockito.mock(Page.class);
        Percy percyInstance = new Percy(mockPage);

        JSONObject percyConfig = new JSONObject();
        percyConfig.put("deferUploads", true);
        JSONObject config = new JSONObject();
        config.put("percy", percyConfig);

        java.lang.reflect.Field cliConfigField = Percy.class.getDeclaredField("cliConfig");
        cliConfigField.setAccessible(true);
        cliConfigField.set(percyInstance, config);

        java.lang.reflect.Method method =
                Percy.class.getDeclaredMethod("isCaptureResponsiveDOM", Map.class);
        method.setAccessible(true);

        Map<String, Object> options = new HashMap<>();
        options.put("responsiveSnapshotCapture", true);

        boolean result = (boolean) method.invoke(percyInstance, options);
        assertFalse(result, "deferUploads should take priority and disable responsive capture");
    }

    @Test
    @Order(14)
    public void isCaptureResponsiveDOMReturnsTrueFromCLIConfig() throws Exception {
        Page mockPage = Mockito.mock(Page.class);
        Percy percyInstance = new Percy(mockPage);

        JSONObject snapshotConfig = new JSONObject();
        snapshotConfig.put("responsiveSnapshotCapture", true);
        JSONObject config = new JSONObject();
        config.put("snapshot", snapshotConfig);

        java.lang.reflect.Field cliConfigField = Percy.class.getDeclaredField("cliConfig");
        cliConfigField.setAccessible(true);
        cliConfigField.set(percyInstance, config);

        java.lang.reflect.Method method =
                Percy.class.getDeclaredMethod("isCaptureResponsiveDOM", Map.class);
        method.setAccessible(true);

        // No SDK-level flag — should still return true because CLI config enables it
        Map<String, Object> options = new HashMap<>();
        boolean result = (boolean) method.invoke(percyInstance, options);
        assertTrue(result, "CLI config responsiveSnapshotCapture should enable responsive capture");
    }

    // -------------------------------------------------------------------------
    // Cookie capture tests
    // -------------------------------------------------------------------------

    @Test
    @Order(15)
    @SuppressWarnings("unchecked")
    public void cookiesAreCapturedInSerializedDOM() throws Exception {
        Page mockPage = Mockito.mock(Page.class);

        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());

        Percy percyInstance = new Percy(mockPage);

        Cookie cookie = new Cookie("session", "abc123");
        cookie.domain = "example.com";
        cookie.path = "/";
        cookie.expires = -1.0;
        cookie.httpOnly = false;
        cookie.secure = false;

        java.lang.reflect.Method method =
                Percy.class.getDeclaredMethod("getSerializedDOM", List.class, String.class, Map.class);
        method.setAccessible(true);

        Map<String, Object> result = (Map<String, Object>) method.invoke(
                percyInstance, Arrays.asList(cookie), "// percy dom script", new HashMap<>());

        assertNotNull(result);
        assertNotNull(result.get("cookies"));

        List<Map<String, Object>> cookies = (List<Map<String, Object>>) result.get("cookies");
        assertEquals(1, cookies.size());
        assertEquals("session", cookies.get(0).get("name"));
        assertEquals("abc123", cookies.get(0).get("value"));
        assertEquals("example.com", cookies.get(0).get("domain"));
        assertEquals("/", cookies.get(0).get("path"));
    }

    @Test
    @Order(16)
    @SuppressWarnings("unchecked")
    public void emptyCookieListIsAttachedWhenNoCookiesPresent() throws Exception {
        Page mockPage = Mockito.mock(Page.class);

        Map<String, Object> domMap = new HashMap<>();
        domMap.put("html", "<html></html>");
        when(mockPage.evaluate(anyString())).thenReturn(domMap);
        when(mockPage.url()).thenReturn("http://example.com");
        when(mockPage.frames()).thenReturn(new ArrayList<>());

        Percy percyInstance = new Percy(mockPage);

        java.lang.reflect.Method method =
                Percy.class.getDeclaredMethod("getSerializedDOM", List.class, String.class, Map.class);
        method.setAccessible(true);

        Map<String, Object> result = (Map<String, Object>) method.invoke(
                percyInstance, new ArrayList<>(), "// percy dom script", new HashMap<>());

        assertNotNull(result);
        List<Map<String, Object>> cookies = (List<Map<String, Object>>) result.get("cookies");
        assertNotNull(cookies);
        assertEquals(0, cookies.size());
    }

    // -------------------------------------------------------------------------
    // Cross-origin iframe capture tests
    // -------------------------------------------------------------------------

    @Test
    @Order(17)
    @SuppressWarnings("unchecked")
    public void corsIframesAreProcessedAndAttachedInSnapshot() throws Exception {
        Page mockPage = Mockito.mock(Page.class);
        Frame mockFrame = Mockito.mock(Frame.class);

        // Main page DOM
        Map<String, Object> mainDomMap = new HashMap<>();
        mainDomMap.put("html", "<html><body><iframe src='http://other.com/'></iframe></body></html>");
        when(mockPage.evaluate(anyString())).thenReturn(mainDomMap);

        // page.evaluate(js, frameUrl) returns percyElementId for the iframe match
        Map<String, Object> iframeDataMap = new HashMap<>();
        iframeDataMap.put("percyElementId", "percy-elem-1");
        when(mockPage.evaluate(anyString(), any())).thenReturn(iframeDataMap);

        when(mockPage.url()).thenReturn("http://example.com/page");
        when(mockFrame.url()).thenReturn("http://other.com/");

        // frame.evaluate: first call injects script (return ignored),
        // second call serializes the frame DOM
        Map<String, Object> iframeSnapshot = new HashMap<>();
        iframeSnapshot.put("html", "<html>iframe content</html>");
        when(mockFrame.evaluate(anyString()))
                .thenReturn(null)           // inject percyDomScript
                .thenReturn(iframeSnapshot); // PercyDOM.serialize(...)

        when(mockPage.frames()).thenReturn(Arrays.asList(mockFrame));

        Percy percyInstance = new Percy(mockPage);

        java.lang.reflect.Method method =
                Percy.class.getDeclaredMethod("getSerializedDOM", List.class, String.class, Map.class);
        method.setAccessible(true);

        Map<String, Object> result = (Map<String, Object>) method.invoke(
                percyInstance, new ArrayList<>(), "// percy dom script", new HashMap<>());

        assertNotNull(result);
        assertNotNull(result.get("corsIframes"), "corsIframes key should be present");

        List<Map<String, Object>> corsIframes = (List<Map<String, Object>>) result.get("corsIframes");
        assertEquals(1, corsIframes.size());

        Map<String, Object> capturedFrame = corsIframes.get(0);
        assertEquals("http://other.com/", capturedFrame.get("frameUrl"));
        assertNotNull(capturedFrame.get("iframeSnapshot"));
        assertEquals("percy-elem-1",
                ((Map<String, Object>) capturedFrame.get("iframeData")).get("percyElementId"));
    }

    @Test
    @Order(18)
    @SuppressWarnings("unchecked")
    public void sameOriginFramesAreNotProcessedAsCorsIframes() throws Exception {
        Page mockPage = Mockito.mock(Page.class);
        Frame mockSameOriginFrame = Mockito.mock(Frame.class);

        Map<String, Object> mainDomMap = new HashMap<>();
        mainDomMap.put("html", "<html><body></body></html>");
        when(mockPage.evaluate(anyString())).thenReturn(mainDomMap);
        when(mockPage.url()).thenReturn("http://example.com/page");
        // Same origin as the page — should not be treated as cross-origin
        when(mockSameOriginFrame.url()).thenReturn("http://example.com/iframe");
        when(mockPage.frames()).thenReturn(Arrays.asList(mockSameOriginFrame));

        Percy percyInstance = new Percy(mockPage);

        java.lang.reflect.Method method =
                Percy.class.getDeclaredMethod("getSerializedDOM", List.class, String.class, Map.class);
        method.setAccessible(true);

        Map<String, Object> result = (Map<String, Object>) method.invoke(
                percyInstance, new ArrayList<>(), "// percy dom script", new HashMap<>());

        assertNotNull(result);
        assertNull(result.get("corsIframes"), "Same-origin frames must not be added to corsIframes");
    }

}
