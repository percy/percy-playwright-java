package io.percy.playwright;

import com.microsoft.playwright.*;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

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
        browser = playwright.firefox().launch(new BrowserType.LaunchOptions().setHeadless(false));
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
    public void takesLocalAppSnapshotWithProvidedName() {
        page.navigate(TEST_URL);
        percy.snapshot("Snapshot with provided name");
    }

    @Test
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
    public void takesMultipleSnapshotsInOneTestCase() {
        page.navigate(TEST_URL);

        page.fill(".new-todo", "A new todo to check off");
        page.keyboard().press("Enter");
        percy.snapshot("Multiple snapshots in one test case -- #1");

        page.click("input.toggle");
        percy.snapshot("Multiple snapshots in one test case -- #2");
    }

    @Test
    public void takeSnapshotThrowErrorForPOA() {
        percy.sessionType = "automate";
        Throwable exception = assertThrows(RuntimeException.class, () -> percy.snapshot("Test"));
        assertEquals("Invalid function call - snapshot(). Please use screenshot() function while using Percy with Automate. For more information on usage of PercyScreenshot, refer https://docs.percy.io/docs/integrate-functional-testing-with-visual-testing", exception.getMessage());
    }

    @Test
    public void takeScreenshotThrowErrorForWeb() throws Exception {
        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        Page page = browser.newPage();
        Percy percy = new Percy(page);
        percy.sessionType = "web";

        Throwable exception = assertThrows(RuntimeException.class, () -> percy.screenshot("Test"));
        assertEquals("Invalid function call - screenshot(). Please use snapshot() function for taking screenshot. screenshot() should be used only while using Percy with Automate. For more information on usage of snapshot(), refer doc for your language https://docs.percy.io/docs/end-to-end-testing", exception.getMessage());

        page.close();
        browser.close();
        playwright.close();
    }

    @Test
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
}
