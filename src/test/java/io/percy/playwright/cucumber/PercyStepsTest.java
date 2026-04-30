package io.percy.playwright.cucumber;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.percy.playwright.Percy;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PercyStepsTest {

    private Page mockPage;

    @BeforeEach
    void setUp() {
        mockPage = mock(Page.class);
    }

    @AfterEach
    void tearDown() {
        PercySteps.reset();
    }

    @Test
    void testSetPageAndGetPercy() {
        PercySteps.setPage(mockPage);
        assertNotNull(PercySteps.getPercy());
    }

    @Test
    void testResetClearsState() {
        PercySteps.setPage(mockPage);
        assertNotNull(PercySteps.getPercy());

        PercySteps.reset();
        assertNull(PercySteps.getPercy());
    }

    @Test
    void testIHaveAPercyInstanceThrowsWithoutPage() {
        PercySteps steps = new PercySteps();
        assertThrows(IllegalStateException.class, steps::iHaveAPercyInstance);
    }

    @Test
    void testIHaveAPercyInstanceSucceedsWithPage() {
        PercySteps.setPage(mockPage);
        PercySteps steps = new PercySteps();
        assertDoesNotThrow(steps::iHaveAPercyInstance);
    }

    @Test
    void testCreateIgnoreRegionCSS() {
        PercySteps.setPage(mockPage);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateIgnoreRegionCSS(".ad-banner"));
    }

    @Test
    void testCreateIgnoreRegionXPath() {
        PercySteps.setPage(mockPage);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateIgnoreRegionXPath("//div[@id='header']"));
    }

    @Test
    void testCreateIgnoreRegionBoundingBox() {
        PercySteps.setPage(mockPage);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateIgnoreRegionBoundingBox(0, 0, 200, 100));
    }

    @Test
    void testCreateConsiderRegionCSS() {
        PercySteps.setPage(mockPage);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateConsiderRegionCSS(".content"));
    }

    @Test
    void testCreateConsiderRegionWithSensitivity() {
        PercySteps.setPage(mockPage);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(
            () -> steps.iCreateConsiderRegionCSSWithSensitivity(".content", 3));
    }

    @Test
    void testCreateIntelliIgnoreRegion() {
        PercySteps.setPage(mockPage);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateIntelliIgnoreRegionCSS(".dynamic"));
    }

    @Test
    void testClearRegions() {
        PercySteps.setPage(mockPage);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        steps.iCreateIgnoreRegionCSS(".ad");
        assertDoesNotThrow(steps::iClearPercyRegions);
    }

    @Test
    void testPercyShouldBeEnabledThrowsWithoutInit() {
        PercySteps steps = new PercySteps();
        assertThrows(IllegalStateException.class, steps::percyShouldBeEnabled);
    }

    @Test
    void testPercyShouldBeEnabledSucceeds() {
        PercySteps.setPage(mockPage);
        PercySteps steps = new PercySteps();
        assertDoesNotThrow(steps::percyShouldBeEnabled);
    }
}
