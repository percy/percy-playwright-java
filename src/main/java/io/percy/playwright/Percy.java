package io.percy.playwright;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import java.util.*;

import javax.swing.text.html.CSS;
import javax.xml.xpath.XPath;

import com.microsoft.playwright.*;


/**
 * Percy client for visual testing.
 */
public class Percy {
    // Playwright Page Object
    private Page page;

    // The JavaScript contained in dom.js
    private String domJs = "";

    // Maybe get the CLI server address
    private String PERCY_SERVER_ADDRESS = System.getenv().getOrDefault("PERCY_SERVER_ADDRESS", "http://localhost:5338");

    // Determine if we're debug logging
    private static boolean PERCY_DEBUG = System.getenv().getOrDefault("PERCY_LOGLEVEL", "info").equals("debug");

    // for logging
    private static String LABEL = "[\u001b[35m" + (PERCY_DEBUG ? "percy:java" : "percy") + "\u001b[39m]";

    // Type of session automate/web
    protected String sessionType = null;

    // Is the Percy server running or not
    private boolean isPercyEnabled = healthcheck();

    // PageMetadata instance
    private PageMetadata pageMetadata = null;

    // Environment information like Java, browser, & SDK versions
    private Environment env;

    public Percy(Page page) {
        this.page = page;
        this.env = new Environment();
    }

    /**
     * Creates a region configuration based on the provided parameters.
     *
     * @param params A map containing the region configuration options. Expected keys:
     *               <ul>
     *                  <li>boundingBox - The bounding box of the region, or null.</li>
     *                  <li>elementXpath - The XPath of the element, or null.</li>
     *                  <li>elementCSS - The CSS selector of the element, or null.</li>
     *                  <li>padding - The padding around the region, or null.</li>
     *                  <li>algorithm - The algorithm to be used (default: 'ignore').</li>
     *                  <li>diffSensitivity - The sensitivity for diffing, or null.</li>
     *                  <li>imageIgnoreThreshold - The image ignore threshold, or null.</li>
     *                  <li>carouselsEnabled - Flag for enabling carousels, or null.</li>
     *                  <li>bannersEnabled - Flag for enabling banners, or null.</li>
     *                  <li>adsEnabled - Flag for enabling ads, or null.</li>
     *                  <li>diffIgnoreThreshold - The diff ignore threshold, or null.</li>
     *               </ul>
     * @return A map representing the region configuration.
     */
        
    public Map<String, Object> createRegion(Map<String, Object> params) {
        Map<String, Object> elementSelector = new HashMap<>();
        if (params.containsKey("boundingBox")) {
            elementSelector.put("boundingBox", params.get("boundingBox"));
        }
        if (params.containsKey("elementXpath")) {
            elementSelector.put("elementXpath", params.get("elementXpath"));
        }
        if (params.containsKey("elementCSS")) {
            elementSelector.put("elementCSS", params.get("elementCSS"));
        }

        Map<String, Object> region = new HashMap<>();
        region.put("algorithm", params.getOrDefault("algorithm", "ignore"));
        region.put("elementSelector", elementSelector);

        if (params.containsKey("padding")) {
            region.put("padding", params.get("padding"));
        }

        Map<String, Object> configuration = new HashMap<>();
        String algorithm = (String) params.getOrDefault("algorithm", "ignore");
        if (algorithm.equals("standard") || algorithm.equals("intelliignore")) {
            if (params.containsKey("diffSensitivity")) {
                configuration.put("diffSensitivity", params.get("diffSensitivity"));
            }
            if (params.containsKey("imageIgnoreThreshold")) {
                configuration.put("imageIgnoreThreshold", params.get("imageIgnoreThreshold"));
            }
            if (params.containsKey("carouselsEnabled")) {
                configuration.put("carouselsEnabled", params.get("carouselsEnabled"));
            }
            if (params.containsKey("bannersEnabled")) {
                configuration.put("bannersEnabled", params.get("bannersEnabled"));
            }
            if (params.containsKey("adsEnabled")) {
                configuration.put("adsEnabled", params.get("adsEnabled"));
            }
        }

        if (!configuration.isEmpty()) {
            region.put("configuration", configuration);
        }

        Map<String, Object> assertion = new HashMap<>();
        if (params.containsKey("diffIgnoreThreshold")) {
            assertion.put("diffIgnoreThreshold", params.get("diffIgnoreThreshold"));
        }

        if (!assertion.isEmpty()) {
            region.put("assertion", assertion);
        }

        return region;
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the screenshot. Should be unique.
     */
    public JSONObject snapshot(String name) {
        Map<String, Object> options = new HashMap<String, Object>();
        return snapshot(name, options);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the snapshot. Should be unique.
     * @param widths    The browser widths at which you want to take the snapshot.
     *                  In pixels.
     */
    public JSONObject snapshot(String name, @Nullable List<Integer> widths) {
        if (!isPercyEnabled) { return null; }

        Map<String, Object> options = new HashMap<String, Object>();
        options.put("widths", widths);

        return snapshot(name, options);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the snapshot. Should be unique.
     * @param widths    The browser widths at which you want to take the snapshot.
     *                  In pixels.
     * @param minHeight The minimum height of the resulting snapshot. In pixels.
     */
    public JSONObject snapshot(String name, List<Integer> widths, Integer minHeight) {
        if (!isPercyEnabled) {
            return null;
        }

        Map<String, Object> options = new HashMap<String, Object>();
        options.put("widths", widths);
        options.put("minHeight", minHeight);

        return snapshot(name, options);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the snapshot. Should be unique.
     * @param widths    The browser widths at which you want to take the snapshot.
     *                  In pixels.
     * @param minHeight The minimum height of the resulting snapshot. In pixels.
     * @param enableJavaScript  Enable JavaScript in the Percy rendering environment
     *
     */
    public JSONObject snapshot(String name, List<Integer> widths, Integer minHeight, boolean enableJavaScript) {
        if (!isPercyEnabled) { return null; }

        Map<String, Object> options = new HashMap<String, Object>();
        options.put("widths", widths);
        options.put("minHeight", minHeight);
        options.put("enableJavaScript", enableJavaScript);

        return snapshot(name, options);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the snapshot. Should be unique.
     * @param widths    The browser widths at which you want to take the snapshot.
     *                  In pixels.
     * @param minHeight The minimum height of the resulting snapshot. In pixels.
     * @param enableJavaScript Enable JavaScript in the Percy rendering environment
     * @param percyCSS Percy specific CSS that is only applied in Percy's browsers
     */
    public JSONObject snapshot(String name, @Nullable List<Integer> widths, Integer minHeight, boolean enableJavaScript, String percyCSS) {
        if (!isPercyEnabled) { return null; }

        Map<String, Object> options = new HashMap<String, Object>();
        options.put("widths", widths);
        options.put("minHeight", minHeight);
        options.put("enableJavaScript", enableJavaScript);
        options.put("percyCSS", percyCSS);

        return snapshot(name, options);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the snapshot. Should be unique.
     * @param widths    The browser widths at which you want to take the snapshot.
     *                  In pixels.
     * @param minHeight The minimum height of the resulting snapshot. In pixels.
     * @param enableJavaScript Enable JavaScript in the Percy rendering environment
     * @param percyCSS Percy specific CSS that is only applied in Percy's browsers
     * @param scope    A CSS selector to scope the screenshot to
     */
    public JSONObject snapshot(String name, @Nullable List<Integer> widths, Integer minHeight, boolean enableJavaScript, String percyCSS, String scope) {
        if (!isPercyEnabled) { return null; }

        Map<String, Object> options = new HashMap<String, Object>();
        options.put("widths", widths);
        options.put("minHeight", minHeight);
        options.put("enableJavaScript", enableJavaScript);
        options.put("percyCSS", percyCSS);
        options.put("scope", scope);

        return snapshot(name, options);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the snapshot. Should be unique.
     * @param widths    The browser widths at which you want to take the snapshot.
     *                  In pixels.
     * @param minHeight The minimum height of the resulting snapshot. In pixels.
     * @param enableJavaScript Enable JavaScript in the Percy rendering environment
     * @param percyCSS Percy specific CSS that is only applied in Percy's browsers
     * @param scope    A CSS selector to scope the screenshot to
     * @param sync     A boolean flag to get snapshot details
     */
    public JSONObject snapshot(String name, @Nullable List<Integer> widths, Integer minHeight, boolean enableJavaScript, String percyCSS, String scope, @Nullable Boolean sync) {
        if (!isPercyEnabled) { return null; }

        Map<String, Object> options = new HashMap<String, Object>();
        options.put("widths", widths);
        options.put("minHeight", minHeight);
        options.put("enableJavaScript", enableJavaScript);
        options.put("percyCSS", percyCSS);
        options.put("scope", scope);
        options.put("sync", sync);

        return snapshot(name, options);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the screenshot. Should be unique.
     * @param options   Extra options
     */
    public JSONObject snapshot(String name, Map<String, Object> options) {
        if (!isPercyEnabled) { return null; }
        if ("automate".equals(sessionType)) { throw new RuntimeException("Invalid function call - snapshot(). Please use screenshot() function while using Percy with Automate. For more information on usage of PercyScreenshot, refer https://www.browserstack.com/docs/percy/integrate/functional-and-visual"); }

        Map<String, Object> domSnapshot = null;
        try {
            page.evaluate(fetchPercyDOM());
            domSnapshot = (Map<String, Object>) page.evaluate(buildSnapshotJS(options));
        } catch (Exception e) {
            if (PERCY_DEBUG) { log(e.getMessage()); }
        }

        return postSnapshot(domSnapshot, name, page.url(), options);
    }

    /**
     * Take a screenshot and upload it to Percy.
     *
     * @param name      The human-readable name of the screenshot. Should be unique.
     */
    public JSONObject screenshot(String name) throws Exception {
        Map<String, Object> options = new HashMap<String, Object>();
        return screenshot(name, options);
    }

    /**
     * Take a screenshot and upload it to Percy.
     *
     * @param name      The human-readable name of the screenshot. Should be unique.
     * @param options   Extra options
     */
    public JSONObject screenshot(String name, Map<String, Object> options) throws Exception {
        if (!isPercyEnabled) { return null; }
        if ("web".equals(sessionType)) { throw new RuntimeException("Invalid function call - screenshot(). Please use snapshot() function for taking screenshot. screenshot() should be used only while using Percy with Automate. For more information on usage of snapshot(), refer doc for your language https://www.browserstack.com/docs/percy/integrate/overview"); }
        setPageMetadata();
        JSONObject json = new JSONObject();
        json.put("sessionId", pageMetadata.getSessionId());
        json.put("pageGuid", pageMetadata.getPageGuid());
        json.put("frameGuid", pageMetadata.getFrameGuid());
        json.put("framework", pageMetadata.getFramework());
        json.put("snapshotName", name);
        json.put("options", options);
        json.put("clientInfo", env.getClientInfo());
        json.put("environmentInfo", env.getEnvironmentInfo());
        return request("/percy/automateScreenshot", json, name);
    }

    /**
     * POST the DOM taken from the test browser to the Percy Agent node process.
     *
     * @param domSnapshot Stringified & serialized version of the site/applications DOM
     * @param name        The human-readable name of the snapshot. Should be unique.
     * @param url         The url of current website
     * @param options     Map of various options support in percySnapshot Command.
     */
    private JSONObject postSnapshot(
            Map<String, Object> domSnapshot,
            String name,
            String url,
            Map<String, Object> options
    ) {
        if (!isPercyEnabled) { return null; }

        // Build a JSON object to POST back to the agent node process
        JSONObject json = new JSONObject(options);
        json.put("url", url);
        json.put("name", name);
        json.put("domSnapshot", domSnapshot);
        json.put("clientInfo", env.getClientInfo());
        json.put("environmentInfo", env.getEnvironmentInfo());
        return request("/percy/snapshot", json, name);
    }

    /**
     * POST data to the Percy Agent node process.
     *
     * @param url         Endpoint to be called.
     * @param name        The human-readable name of the snapshot. Should be unique.
     * @param json        Json object of all properties.
     */
    protected JSONObject request(String url, JSONObject json, String name) {
        StringEntity entity = new StringEntity(json.toString(), ContentType.APPLICATION_JSON);

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost request = new HttpPost(PERCY_SERVER_ADDRESS + url);
            request.setEntity(entity);
            HttpResponse response = httpClient.execute(request);
            JSONObject jsonResponse = new JSONObject(EntityUtils.toString(response.getEntity()));

            if (jsonResponse.has("data")) {
                return jsonResponse.getJSONObject("data");
            }
        } catch (Exception ex) {
            if (PERCY_DEBUG) { log(ex.toString()); }
            log("Could not post snapshot " + name);
        }
        return null;
    }

    /**
     * @return A String containing the JavaScript needed to instantiate a PercyAgent
     *         and take a snapshot.
     */
    private String buildSnapshotJS(Map<String, Object> options) {
        StringBuilder jsBuilder = new StringBuilder();
        JSONObject json = new JSONObject(options);
        jsBuilder.append(String.format("PercyDOM.serialize(%s)\n", json.toString()));

        return jsBuilder.toString();
    }

    /**
     * Attempts to load dom.js from the local Percy server. Use cached value in `domJs`,
     * if it exists.
     *
     * This JavaScript is critical for capturing snapshots. It serializes and captures
     * the DOM. Without it, snapshots cannot be captured.
     */
    private String fetchPercyDOM() {
        if (!domJs.trim().isEmpty()) { return domJs; }

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpGet httpget = new HttpGet(PERCY_SERVER_ADDRESS + "/percy/dom.js");
            HttpResponse response = httpClient.execute(httpget);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200){
                throw new RuntimeException("Failed with HTTP error code: " + statusCode);
            }
            HttpEntity httpEntity = response.getEntity();
            String domString = EntityUtils.toString(httpEntity);
            domJs = domString;

            return domString;
        } catch (Exception ex) {
            isPercyEnabled = false;
            if (PERCY_DEBUG) { log(ex.toString()); }

            return "";
        }
    }

    /**
     * Checks to make sure the local Percy server is running. If not, disable Percy.
     */
    private boolean healthcheck() {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            //Creating a HttpGet object
            HttpGet httpget = new HttpGet(PERCY_SERVER_ADDRESS + "/percy/healthcheck");

            //Executing the Get request
            HttpResponse response = httpClient.execute(httpget);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200){
                throw new RuntimeException("Failed with HTTP error code : " + statusCode);
            }

            String version = response.getFirstHeader("x-percy-core-version").getValue();

            if (version == null) {
                log("You may be using @percy/agent" +
                        "which is no longer supported by this SDK." +
                        "Please uninstall @percy/agent and install @percy/cli instead." +
                        "https://www.browserstack.com/docs/percy/migration/migrate-to-cli"
                );

                return false;
            }

            if (!version.split("\\.")[0].equals("1")) {
                log("Unsupported Percy CLI version, " + version);

                return false;
            }
            HttpEntity entity = response.getEntity();
            String responseString = EntityUtils.toString(entity, "UTF-8");
            JSONObject responseObject = new JSONObject(responseString);
            sessionType = (String) responseObject.optString("type", null);

            return true;
        } catch (Exception ex) {
            log("Percy is not running, disabling snapshots");
            if (PERCY_DEBUG) { log(ex.toString()); }

            return false;
        }
    }

    /**
     * On Automate session updates the pageMetadata
     */
    protected void setPageMetadata() throws Exception{
        this.pageMetadata = new PageMetadata(this.page);
    }

    protected static void log(String message) {
        System.out.println(LABEL + " " + message);
    }
}
