package io.percy.playwright;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.impl.PageImpl;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class PageMetadata extends Exception {
    private  Page page;

    private String pageGuid = null;
    private String frameGuid = null;
    private String browserGuid = null;
    private  String sessionId = null;
    private Map<String, Object> sessionDetails;

    public PageMetadata(Page page) {
        this.pageGuid = null;
        this.frameGuid = null;
        this.browserGuid = null;
        this.page = page;
    }

    /**
     * FrameWork Details : playwright
     */
    public  String getFramework() {
        return "playwright";
    }

    /**
     * Get Playwright Page Guid
     */
    public String getPageGuid() throws Exception {
        try {
            if (this.pageGuid != null) return this.pageGuid;
            PageImpl pageImpl = (PageImpl) this.page;
            Field pageField = pageImpl.getClass().getSuperclass().getDeclaredField("guid");
            pageField.setAccessible(true);
            this.pageGuid = (String) pageField.get(pageImpl);
        } catch (Exception err) {
            Percy.log("Failed to fetch PageGuid, error: " + err.getMessage());
            throw new Exception("Failed to fetch PageGuid");
        }
        return this.pageGuid;
    }

    /**
     * Get Playwright Frame Guid
     */
    public String getFrameGuid() throws Exception {
        try {
            if (this.frameGuid != null) return this.frameGuid;
            PageImpl pageImpl = (PageImpl) this.page;
            Field frameField = pageImpl.mainFrame().getClass().getSuperclass().getDeclaredField("guid");
            frameField.setAccessible(true);
            this.frameGuid = (String) frameField.get(pageImpl.mainFrame());
        } catch (Exception err) {
            Percy.log("Failed to fetch FrameGuid, error: " + err.getMessage());
            throw new Exception("Failed to fetch FrameGuid");
        }
        return this.frameGuid;
    }

    /**
     * Get Playwright Browser Guid
     */
    public String getBrowserGuid() throws Exception {
        try {
            if (this.browserGuid != null) return this.browserGuid;
            PageImpl pageImpl = (PageImpl) this.page;
            BrowserContext context = this.page.context();
            Browser browserFromContext = context.browser();
            Field browserField = browserFromContext.getClass().getSuperclass().getDeclaredField("guid");
            browserField.setAccessible(true);
            this.browserGuid = (String) browserField.get(pageImpl.mainFrame());
        } catch (Exception err) {
            Percy.log("Failed to fetch BrowserGuid, error: " + err.getMessage());
            throw new Exception("Failed to fetch BrowserGuid");
        }
        return this.browserGuid;
    }

    /**
     * Get Browserstack Automate Session Details
     */
    public Map<String, Object> getSessionDetails() throws Exception {
        String key = "sessionDetails_" + this.getBrowserGuid();
        try {
            if (Cache.CACHE_MAP.get(key) == null) {
                Object sessionDetailResponse = this.page.evaluate("_ => {}", "browserstack_executor: {\"action\": \"getSessionDetails\"}");
                JsonObject sessionDetail = JsonParser.parseString((String) sessionDetailResponse).getAsJsonObject();
                ObjectMapper objectMapper = new ObjectMapper();
                this.sessionDetails = objectMapper.readValue(sessionDetail.toString(), new TypeReference<HashMap<String, Object>>() {});
                Cache.CACHE_MAP.put(key, this.sessionDetails);
            }
        }
        catch (Exception err) {
            Percy.log("Failed to fetch SessionCapabilities, error: " + err.getMessage());
            throw new Exception("Failed to fetch SessionCapabilities");
        }
        return (Map<String, Object>) Cache.CACHE_MAP.get(key);
    }

    /**
     * Get Browserstack Automate SessionId
     */
    public String getSessionId() throws Exception {
        try {
            Map<String, Object> sessionDetail = this.getSessionDetails();
            this.sessionId = sessionDetail.get("hashed_id").toString();
        } catch (Exception err) {
            Percy.log("Failed to fetch SessionId, error: " + err.getMessage());
            throw new Exception("Failed to fetch SessionId, error");
        }
        return this.sessionId;
    }
}
