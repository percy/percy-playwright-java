# percy-playwright-java
![Test](https://github.com/percy/percy-playwright-java/workflows/Test/badge.svg)

[Percy](https://percy.io) visual testing for Java Playwright.

## Installation

npm install `@percy/cli`:

```sh-session
$ npm install --save-dev @percy/cli
```

Add percy-playwright-java to your project dependencies. If you're using Maven:

``` xml
<dependency>
  <groupId>io.percy</groupId>
  <artifactId>percy-playwright-java</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Usage

This is an example test using the `percy.snapshot` function.

``` java
// import ...
import io.percy.playwright;

public class Example {
    private static Browser browser;
    private static BrowserContext context;
    private static Page page;
    private static Percy percy;

    public static void main(String[] args) {
        browser = playwright.firefox().launch(new BrowserType.LaunchOptions().setHeadless(false));
        context = browser.newContext();
        page = context.newPage();
        percy = new Percy(page);
        percy.snapshot("Java example");
    }
}
```

Running the test above normally will result in the following log:

```sh-session
[percy] Percy is not running, disabling snapshots
```

When running with [`percy
exec`](https://github.com/percy/cli/tree/master/packages/cli-exec#percy-exec), and your project's
`PERCY_TOKEN`, a new Percy build will be created and snapshots will be uploaded to your project.

```sh-session
$ export PERCY_TOKEN=[your-project-token]
$ percy exec -- [java test command]
[percy] Percy has started!
[percy] Created build #1: https://percy.io/[your-project]
[percy] Snapshot taken "Java example"
[percy] Stopping percy...
[percy] Finalized build #1: https://percy.io/[your-project]
[percy] Done!
```

## Configuration

The snapshot method arguments:

`percy.snapshot(name, widths[], minHeight, enableJavaScript, percyCSS, scope)`

- `name` (**required**) - The snapshot name; must be unique to each snapshot
- Additional snapshot options (overrides any project options):
    - `widths` - An array of widths to take screenshots at
    - `minHeight` - The minimum viewport height to take screenshots at
    - `enableJavaScript` - Enable JavaScript in Percy's rendering environment
    - `percyCSS` - Percy specific CSS only applied in Percy's rendering
      environment
    - `scope` - A CSS selector to scope the screenshot to

    
## Percy on Automate

## Usage

This is an example test using the `percy.screenshot` function.

``` java
import io.percy.playwright;

public class Example {
    private static Percy percy;

    public static void main(String[] args) {
        JsonObject capabilitiesObject = new JsonObject();
        capabilitiesObject.addProperty("browser", "chrome");    // allowed browsers are `chrome`, `edge`, `playwright-chromium`, `playwright-firefox` and `playwright-webkit`
        capabilitiesObject.addProperty("browser_version", "latest");
        capabilitiesObject.addProperty("os", "osx");
        capabilitiesObject.addProperty("os_version", "catalina");
        capabilitiesObject.addProperty("name", "Playwright");
        capabilitiesObject.addProperty("build", "Playwright-Java");
        capabilitiesObject.addProperty("browserstack.username", "<username>");
        capabilitiesObject.addProperty("browserstack.accessKey", "<accessKey>");

        BrowserType chromium = playwright.chromium();
        String caps = URLEncoder.encode(capabilitiesObject.toString(), "utf-8");
        String ws_endpoint = "wss://cdp.browserstack.com/playwright?caps=" + caps;
        Browser browser = chromium.connect(ws_endpoint);
        Page page = browser.newPage();
        page.navigate("https://percy.io");
        percy = new Percy(page);
        percy.screenshot("Screenshot-1");
    }
}
```

- `page` (**required**) - A playwright page instance
- `name` (**required**) - The screenshot name; must be unique to each screenshot
- `options` (**optional**) - There are various options supported by percy.screenshot to server further functionality.
    - `sync` - Boolean value by default it falls back to false, Gives the processed result around screenshot [From CLI v1.28.9-beta.0+]
    - `fullPage` - Boolean value by default it falls back to `false`, Takes full page screenshot [From CLI v1.28.9-beta.0+]
    - `freezeAnimatedImage` - Boolean value by default it falls back to `false`, you can pass `true` and percy will freeze image based animations.
    - `freezeImageBySelectors` - List of selectors. Images will be freezed which are passed using selectors. For this to work `freezeAnimatedImage` must be set to true.
    - `freezeImageByXpaths` - List of xpaths. Images will be freezed which are passed using xpaths. For this to work `freezeAnimatedImage` must be set to true.
    - `percyCSS` - Custom CSS to be added to DOM before the screenshot being taken. Note: This gets removed once the screenshot is taken.
    - `ignoreRegionXpaths` - List of xpaths. elements in the DOM can be ignored using xpath
    - `ignoreRegionSelectors` - List of selectors. elements in the DOM can be ignored using selectors.
    - `customIgnoreRegions` - List of custom objects. elements can be ignored using custom boundaries
        - Refer to example -
            - ```
          List<HashMap> customRegion = new ArrayList<>();
          HashMap<String, Integer> region1 = new HashMap<>();
          region1.put("top", 10);
          region1.put("bottom", 110);
          region1.put("right", 10);
          region1.put("left", 120);
          customRegion.add(region1);
          options.put("custom_ignore_regions", customRegion);
        ```
        - Parameters:
            - `top` (int): Top coordinate of the ignore region.
            - `bottom` (int): Bottom coordinate of the ignore region.
            - `left` (int): Left coordinate of the ignore region.
            - `right` (int): Right coordinate of the ignore region.
    - `considerRegionXpaths` - List of xpaths. elements in the DOM can be considered for diffing and will be ignored by Intelli Ignore using xpaths.
    - `considerRegionSelectors` - List of selectors. elements in the DOM can be considered for diffing and will be ignored by Intelli Ignore using selectors.
    - `customConsiderRegions` - List of custom objects. elements can be considered for diffing and will be ignored by Intelli Ignore using custom boundaries
        - Refer to example -
            - ```
          List<HashMap> customRegion = new ArrayList<>();
          HashMap<String, Integer> region2 = new HashMap<>();
          region2.put("top", 10);
          region2.put("bottom", 110);
          region2.put("right", 10);
          region2.put("left", 120);
          customRegion.add(region2);
          options.put("custom_consider_regions", customRegion);
        ```
            - Parameters:
                - `top` (int): Top coordinate of the consider region.
                - `bottom` (int): Bottom coordinate of the consider region.
                - `left` (int): Left coordinate of the consider region.
                - `right` (int): Right coordinate of the consider region.

### Creating Percy on automate build
Note: Automate Percy Token starts with `auto` keyword. The command can be triggered using `exec` keyword.
```sh-session
$ export PERCY_TOKEN=[your-project-token]
$ percy exec -- [java test command]
[percy] Percy has started!
[percy] [Java example] : Starting automate screenshot ...
[percy] Screenshot taken "Java example"
[percy] Stopping percy...
[percy] Finalized build #1: https://percy.io/[your-project]
[percy] Done!
```

Refer to docs here: [Percy on Automate](https://www.browserstack.com/docs/percy/integrate/functional-and-visual)

