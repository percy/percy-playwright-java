package io.percy.playwright;

import com.microsoft.playwright.Playwright;

/**
 * Package-private class to compute Environment information.
 */
class Environment {
    private final static String SDK_VERSION = "1.0.1-beta.0";
    private final static String SDK_NAME = "percy-playwright-java";

    public String getClientInfo() {
        return SDK_NAME + "/" + SDK_VERSION;
    }

    public String getEnvironmentInfo() {
        String playwrightVersion = Playwright.class.getPackage().getImplementationVersion();
        return String.format("playwright-java; %s", playwrightVersion);
    }
}
