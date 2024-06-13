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

`percy.snapshot(driver, name[, **kwargs])`

- `page` (**required**) - A playwright page instance
- `name` (**required**) - The snapshot name; must be unique to each snapshot
- `**kwargs` - [See per-snapshot configuration options](https://docs.percy.io/docs/cli-configuration#per-snapshot-configuration)


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
