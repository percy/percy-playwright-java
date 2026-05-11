package io.percy.playwright;

import com.microsoft.playwright.Playwright;

/**
 * Package-private class to compute Environment information.
 */
class Environment {
    private final static String SDK_VERSION = "1.0.2";
    private final static String SDK_NAME = "percy-playwright-java";

    private String clientInfoOverride;
    private String environmentInfoOverride;

    public String getClientInfo() {
        if (clientInfoOverride != null) {
            return clientInfoOverride;
        }
        return SDK_NAME + "/" + SDK_VERSION;
    }

    public String getEnvironmentInfo() {
        if (environmentInfoOverride != null) {
            return environmentInfoOverride;
        }
        String playwrightVersion = Playwright.class.getPackage().getImplementationVersion();
        return String.format("playwright-java; %s", playwrightVersion);
    }

    void setClientInfo(String clientInfo) {
        this.clientInfoOverride = clientInfo;
    }

    void setEnvironmentInfo(String environmentInfo) {
        this.environmentInfoOverride = environmentInfo;
    }

    public static String getSdkVersion() {
        return SDK_VERSION;
    }
}
