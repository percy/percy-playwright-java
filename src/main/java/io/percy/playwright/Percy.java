package io.percy.playwright;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.ViewportSize;


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

    // Optional sleep between responsive captures (seconds)
    private static String RESPONSIVE_CAPTURE_SLEEP_TIME = System.getenv().getOrDefault("RESPONSIVE_CAPTURE_SLEEP_TIME", "");

    // Whether to reload the page between responsive captures
    private static boolean PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE =
            "true".equalsIgnoreCase(System.getenv("PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE"));

    // Whether to adjust the default height to account for browser chrome during responsive capture
    private static boolean PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT =
            "true".equalsIgnoreCase(System.getenv("PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT"));

    // for logging
    private static String LABEL = "[\u001b[35m" + (PERCY_DEBUG ? "percy:java" : "percy") + "\u001b[39m]";

    // Type of session automate/web
    protected String sessionType = null;

    // CLI config returned by healthcheck
    private JSONObject cliConfig = new JSONObject();

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

        Object domSnapshot = null;
        try {
            String percyDomScript = fetchPercyDOM();
            page.evaluate(percyDomScript);

            List<Cookie> cookies = new ArrayList<>();
            try {
                cookies = page.context().cookies();
            } catch (Exception e) {
                log("Cookie collection failed: " + e.getMessage(), "debug");
            }

            if (isCaptureResponsiveDOM(options)) {
                domSnapshot = captureResponsiveDom(cookies, percyDomScript, options);
            } else {
                domSnapshot = getSerializedDOM(cookies, percyDomScript, options);
            }
        } catch (Exception e) {
            if(domSnapshot == null) {
                log("Snapshot capture failed: " + e.getMessage());
                return null;
            }
            log(e.getMessage(), "debug");
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
     * @param domSnapshot Stringified &amp; serialized version of the site/applications DOM.
     *                    May be a {@code Map<String,Object>} for a single capture or a
     *                    {@code List<Map<String,Object>>} for a responsive (multi-width) capture.
     * @param name        The human-readable name of the snapshot. Should be unique.
     * @param url         The url of current website
     * @param options     Map of various options support in percySnapshot Command.
     */
    private JSONObject postSnapshot(
            Object domSnapshot,
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

        int timeout = 600000; // 600 seconds
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(timeout)
                .setConnectTimeout(timeout)
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpPost request = new HttpPost(PERCY_SERVER_ADDRESS + url);
            request.setEntity(entity);
            HttpResponse response = httpClient.execute(request);
            JSONObject jsonResponse = new JSONObject(EntityUtils.toString(response.getEntity()));

            if (jsonResponse.has("data")) {
                return jsonResponse.getJSONObject("data");
            }
        } catch (Exception ex) {
            log(ex.toString(), "debug");
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
            JSONObject parsedConfig = responseObject.optJSONObject("config");
            if (parsedConfig != null) {
                cliConfig = parsedConfig;
            }

            return true;
        } catch (Exception ex) {
            log("Percy is not running, disabling snapshots");
            log(ex.toString(), "debug");

            return false;
        }
    }

    /**
     * On Automate session updates the pageMetadata
     */
    protected void setPageMetadata() throws Exception{
        this.pageMetadata = new PageMetadata(this.page);
    }

    // -------------------------------------------------------------------------
    // Responsive snapshot capture helpers
    // -------------------------------------------------------------------------

    /**
     * Determines whether responsive DOM capture should be performed for this snapshot.
     * Returns {@code true} if either the per-snapshot {@code responsiveSnapshotCapture} option
     * or the CLI config's {@code snapshot.responsiveSnapshotCapture} flag is {@code true}.
     * Always returns {@code false} when {@code percy.deferUploads} is enabled in the CLI config,
     * since responsive capture is not supported in that mode.
     */
    private boolean isCaptureResponsiveDOM(Map<String, Object> options) {
        // Respect deferUploads: if enabled, responsive capture is not supported
        if (cliConfig.has("percy") && !cliConfig.isNull("percy")) {
            JSONObject percyProperty = cliConfig.getJSONObject("percy");
            if (percyProperty.has("deferUploads") && !percyProperty.isNull("deferUploads")
                    && percyProperty.getBoolean("deferUploads")) {
                return false;
            }
        }

        boolean responsiveSnapshotCaptureCLI = false;
        if (cliConfig.has("snapshot") && !cliConfig.isNull("snapshot")) {
            JSONObject snapshotConfig = cliConfig.getJSONObject("snapshot");
            if (snapshotConfig.has("responsiveSnapshotCapture")) {
                responsiveSnapshotCaptureCLI = snapshotConfig.getBoolean("responsiveSnapshotCapture");
            }
        }

        Object responsiveSnapshotCaptureSDK = options.get("responsiveSnapshotCapture");
        return (responsiveSnapshotCaptureSDK instanceof Boolean && (Boolean) responsiveSnapshotCaptureSDK)
                || responsiveSnapshotCaptureCLI;
    }

    /**
     * Calculates the default height for responsive capture.
     * When {@code PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT} is enabled, the height is adjusted
     * to account for browser chrome: {@code window.outerHeight - window.innerHeight + minH}.
     *
     * @param currentHeight The current viewport height to use as a fallback.
     * @param options       Snapshot options; may contain a {@code minHeight} override.
     * @return The computed default height in pixels.
     */
    private int calculateDefaultHeight(int currentHeight, Map<String, Object> options) {
        if (!PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT) {
            return currentHeight;
        }
        try {
            Object minHeightOption = options.get("minHeight");
            int minHeight = (minHeightOption instanceof Number)
                    ? ((Number) minHeightOption).intValue()
                    : currentHeight;
            Object result = page.evaluate("(minH) => window.outerHeight - window.innerHeight + minH", minHeight);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Exception e) {
            log("Failed to calculate default height: " + e.getMessage(), "debug");
        }
        return currentHeight;
    }

    /**
     * Fetches responsive width/height pairs from the Percy CLI {@code /percy/widths-config}
     * endpoint.  The optional {@code widths} list is forwarded as a query parameter so that
     * the CLI can merge user-supplied widths with its own configuration.
     *
     * @param widths Optional list of user-supplied widths (may be null or empty).
     * @return A list of {@code {"width": N, "height": N}} maps as returned by the CLI.
     * @throws RuntimeException when the CLI is unavailable or returns an unexpected payload.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getResponsiveWidths(List<Integer> widths) {
        String queryParam = "";
        if (widths != null && !widths.isEmpty()) {
            String joined = widths.stream().map(String::valueOf).collect(Collectors.joining(","));
            queryParam = "?widths=" + joined;
        }

        int timeout = 30000; // 30 seconds
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(timeout)
                .setConnectTimeout(timeout)
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpGet httpget = new HttpGet(PERCY_SERVER_ADDRESS + "/percy/widths-config" + queryParam);
            HttpResponse response = httpClient.execute(httpget);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                EntityUtils.consume(response.getEntity());
                log("Update Percy CLI to the latest version to use responsiveSnapshotCapture");
                throw new RuntimeException(
                        "Failed to fetch widths-config (HTTP " + statusCode + ")");
            }

            String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
            JSONObject json = new JSONObject(responseString);

            if (!json.has("widths") || json.isNull("widths")) {
                log("Update Percy CLI to the latest version to use responsiveSnapshotCapture");
                throw new RuntimeException(
                        "Missing \"widths\" in widths-config response");
            }

            JSONArray widthsArray = json.getJSONArray("widths");
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < widthsArray.length(); i++) {
                JSONObject entry = widthsArray.getJSONObject(i);
                Map<String, Object> item = new HashMap<>();
                item.put("width", entry.getInt("width"));
                if (entry.has("height") && !entry.isNull("height")) {
                    item.put("height", entry.getInt("height"));
                }
                result.add(item);
            }
            return result;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception ex) {
            log("Update Percy CLI to the latest version to use responsiveSnapshotCapture");
            log("Failed to fetch widths-config: " + ex.getMessage(), "debug");
            throw new RuntimeException(
                    "Failed to fetch widths-config: " + ex.getMessage(), ex);
        }
    }

    /**
     * Resizes the page viewport to the requested dimensions and waits for the page to
     * acknowledge the resize via the {@code window.resizeCount} counter injected by
     * {@code PercyDOM.waitForResize()}.
     *
     * @param width       Target viewport width in pixels.
     * @param height      Target viewport height in pixels.
     * @param resizeCount The expected value of {@code window.resizeCount} after resize.
     */
    private void changeViewportAndWait(int width, int height, int resizeCount) {
        try {
            page.setViewportSize(width, height);
        } catch (Exception e) {
            log("Resizing viewport failed for width " + width + ": " + e.getMessage(), "debug");
        }

        try {
            page.waitForFunction(
                    "window.resizeCount === " + resizeCount,
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(1000)
            );
        } catch (Exception e) {
            log("Timed out waiting for window resize event for width " + width, "debug");
        }
    }

    /**
     * Captures serialized DOM snapshots for each responsive width/height pair returned
     * by the Percy CLI.  The viewport is restored to its original size after capture.
     *
     * @param cookies         Page cookies to embed in each snapshot.
     * @param percyDomScript  The cached percy DOM serialization script.
     * @param options         Snapshot options (passed through to the DOM serializer).
     * @return A list of DOM snapshot maps, each annotated with its capture {@code width}.
     */
    private List<Map<String, Object>> captureResponsiveDom(
            List<Cookie> cookies,
            String percyDomScript,
            Map<String, Object> options) {

        @SuppressWarnings("unchecked")
        List<Integer> userWidths = (options.get("widths") instanceof List<?>)
                ? (List<Integer>) options.get("widths")
                : new ArrayList<>();

        List<Map<String, Object>> widthHeights = getResponsiveWidths(userWidths);

        List<Map<String, Object>> domSnapshots = new ArrayList<>();

        ViewportSize originalViewport = page.viewportSize();
        int currentWidth  = (originalViewport != null) ? originalViewport.width  : 1280;
        int currentHeight = (originalViewport != null) ? originalViewport.height : 720;
        int defaultHeight = calculateDefaultHeight(currentHeight, options);
        int lastWindowWidth = currentWidth;
        int lastWindowHeight = currentHeight;
        int resizeCount = 0;

        // Inject the resize counter before iterating widths
        page.evaluate("PercyDOM.waitForResize()");

        for (Map<String, Object> widthHeight : widthHeights) {
            int width  = (int) widthHeight.get("width");
            int height = widthHeight.containsKey("height")
                    ? (int) widthHeight.get("height")
                    : defaultHeight;

            if (lastWindowWidth != width || lastWindowHeight != height) {
                resizeCount++;
                changeViewportAndWait(width, height, resizeCount);
                lastWindowWidth = width;
                lastWindowHeight = height;
            }

            if (PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE) {
                page.reload();
                page.evaluate(percyDomScript);
                page.evaluate("PercyDOM.waitForResize()");
                resizeCount = 0;
            }

            if (!RESPONSIVE_CAPTURE_SLEEP_TIME.isEmpty()) {
                try {
                    int sleepMs = Integer.parseInt(RESPONSIVE_CAPTURE_SLEEP_TIME) * 1000;
                    if (sleepMs > 0) { Thread.sleep(sleepMs); }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (NumberFormatException ignored) { }
            }

            Map<String, Object> domSnapshot = getSerializedDOM(cookies, percyDomScript, options);
            domSnapshot.put("width", width);
            domSnapshots.add(domSnapshot);
        }

        // Restore original viewport only if it was changed
        if (lastWindowWidth != currentWidth || lastWindowHeight != currentHeight) {
            changeViewportAndWait(currentWidth, currentHeight, resizeCount + 1);
        }

        return domSnapshots;
    }

    // -------------------------------------------------------------------------
    // Cross-origin iframe helpers
    // -------------------------------------------------------------------------

    /**
     * Processes a single cross-origin {@link Frame}: injects the Percy DOM script,
     * serializes the frame, and retrieves the matching {@code data-percy-element-id}
     * from the main page so the CLI can stitch the iframe content into the snapshot.
     *
     * @param frame          The cross-origin frame to process.
     * @param percyDomScript The cached percy DOM serialization script.
     * @param options        Snapshot options forwarded to the frame serializer.
     * @return A map containing {@code iframeData}, {@code iframeSnapshot}, and
     *         {@code frameUrl}, or {@code null} if the frame cannot be processed.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> processFrame(
            Frame frame,
            String percyDomScript,
            Map<String, Object> options) {

        String frameUrl = frame.url();
        try {
            // Inject Percy DOM into the cross-origin frame
            frame.evaluate(percyDomScript);

            // enableJavaScript=true prevents standard iframe serialization so we can
            // handle cross-origin frames manually
            Map<String, Object> frameOptions = new HashMap<>(options);
            frameOptions.put("enableJavaScript", true);
            JSONObject frameOptionsJson = new JSONObject(frameOptions);

            Map<String, Object> iframeSnapshot =
                    (Map<String, Object>) frame.evaluate(
                            String.format("PercyDOM.serialize(%s)", frameOptionsJson));

            // Retrieve the matching iframe element's percy ID from the main page
            String js =
                    "(fUrl) => {" +
                    "  const iframes = Array.from(document.querySelectorAll('iframe'));" +
                    "  const match = iframes.find(f => f.src.startsWith(fUrl));" +
                    "  if (match) {" +
                    "    return { percyElementId: match.getAttribute('data-percy-element-id') };" +
                    "  }" +
                    "}";

            Map<String, Object> iframeData =
                    (Map<String, Object>) page.evaluate(js, frameUrl);

            if (iframeData == null || iframeData.get("percyElementId") == null) {
                log("Skipping cross-origin frame " + frameUrl +
                        ": no matching iframe with percyElementId found", "debug");
                return null;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("iframeData", iframeData);
            result.put("iframeSnapshot", iframeSnapshot);
            result.put("frameUrl", frameUrl);
            return result;

        } catch (Exception e) {
            log("Failed to process cross-origin frame " + frameUrl + ": " + e.getMessage(), "debug");
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // DOM serialization
    // -------------------------------------------------------------------------

    /**
     * Serializes the main page DOM, captures cross-origin iframes, and attaches cookies.
     *
     * @param cookies        Page cookies to embed in the snapshot payload.
     * @param percyDomScript The cached percy DOM serialization script.
     * @param options        Snapshot options forwarded to the DOM serializer.
     * @return A mutable snapshot map ready for posting to the Percy CLI.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getSerializedDOM(
            List<Cookie> cookies,
            String percyDomScript,
            Map<String, Object> options) {

        Map<String, Object> domSnapshot =
                (Map<String, Object>) page.evaluate(buildSnapshotJS(options));
        if (domSnapshot == null) {
            throw new RuntimeException("DOM serialization returned null — PercyDOM.serialize() may not be loaded or returned undefined");
        }
        Map<String, Object> mutableSnapshot = new HashMap<>(domSnapshot);

        // Process cross-origin iframes
        try {
            URI pageUri = new URI(page.url());
            String pageHost = pageUri.getHost();

            List<Frame> crossOriginFrames = page.frames().stream()
                    .filter(f -> {
                        String fUrl = f.url();
                        if ("about:blank".equals(fUrl) || fUrl.isEmpty()) { return false; }
                        // If the page has no host (e.g., file:, data:), skip CORS detection
                        if (pageHost == null) { return false; }
                        try {
                            String frameHost = new URI(fUrl).getHost();
                            // Treat frames with no host as non-cross-origin
                            return frameHost != null && !Objects.equals(frameHost, pageHost);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            if (!crossOriginFrames.isEmpty()) {
                List<Map<String, Object>> processedFrames = new ArrayList<>();
                for (Frame frame : crossOriginFrames) {
                    Map<String, Object> frameResult = processFrame(frame, percyDomScript, options);
                    if (frameResult != null) {
                        processedFrames.add(frameResult);
                    }
                }
                if (!processedFrames.isEmpty()) {
                    mutableSnapshot.put("corsIframes", processedFrames);
                }
            }
        } catch (Exception e) {
            log("Failed to process cross-origin iframes: " + e.getMessage(), "debug");
        }

        // Serialize cookies as a list of plain maps
        List<Map<String, Object>> cookiesList = new ArrayList<>();
        for (Cookie c : cookies) {
            Map<String, Object> cookieMap = new HashMap<>();
            cookieMap.put("name",     c.name);
            cookieMap.put("value",    c.value);
            cookieMap.put("domain",   c.domain);
            cookieMap.put("path",     c.path);
            cookieMap.put("expires",  c.expires);
            cookieMap.put("httpOnly", c.httpOnly);
            cookieMap.put("secure",   c.secure);
            if (c.sameSite != null) {
                cookieMap.put("sameSite", c.sameSite.toString());
            }
            cookiesList.add(cookieMap);
        }
        mutableSnapshot.put("cookies", cookiesList);

        return mutableSnapshot;
    }

    // -------------------------------------------------------------------------
    // Logging
    // -------------------------------------------------------------------------

    protected static void log(String message) {
        log(message, "info");
    }

    protected static void log(String message, String level) {
        message = LABEL + " " + message;
        JSONObject logJson = new JSONObject();
        logJson.put("message", message);
        logJson.put("level", level);
        StringEntity entity = new StringEntity(logJson.toString(), ContentType.APPLICATION_JSON);

        int timeout = 1000; // 1 second
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(timeout)
                .setConnectTimeout(timeout)
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpPost logRequest = new HttpPost(
                    System.getenv().getOrDefault("PERCY_SERVER_ADDRESS", "http://localhost:5338") + "/percy/log");
            logRequest.setEntity(entity);
            httpClient.execute(logRequest);
        } catch (Exception ex) {
            if (PERCY_DEBUG) { System.out.println("Sending log to CLI Failed " + ex.toString()); }
        } finally {
            // Print to stdout unless it is a debug message and debug mode is off
            if (!"debug".equals(level) || PERCY_DEBUG) {
                System.out.println(message);
            }
        }
    }
}
