package io.percy.playwright;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Environment}: client/environment info defaults and overrides.
 */
public class EnvironmentTest {

    @Test
    public void getClientInfoReturnsSdkNameAndVersionByDefault() {
        Environment env = new Environment();
        String info = env.getClientInfo();
        assertEquals("percy-playwright-java/" + Environment.getSdkVersion(), info);
    }

    @Test
    public void getClientInfoReturnsOverrideWhenSet() {
        Environment env = new Environment();
        env.setClientInfo("wrapper/1.0.0");
        assertEquals("wrapper/1.0.0", env.getClientInfo());
    }

    @Test
    public void getEnvironmentInfoReturnsPlaywrightStringByDefault() {
        Environment env = new Environment();
        String info = env.getEnvironmentInfo();
        assertNotNull(info);
        assertTrue(info.startsWith("playwright-java;"),
                "Expected default environment info to start with 'playwright-java;' but was: " + info);
    }

    @Test
    public void getEnvironmentInfoReturnsOverrideWhenSet() {
        Environment env = new Environment();
        env.setEnvironmentInfo("custom-env; playwright-java");
        assertEquals("custom-env; playwright-java", env.getEnvironmentInfo());
    }

    @Test
    public void getSdkVersionIsNonEmpty() {
        assertNotNull(Environment.getSdkVersion());
        assertFalse(Environment.getSdkVersion().isEmpty());
    }
}
