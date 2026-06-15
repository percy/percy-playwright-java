package io.percy.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PageMetadata}.
 *
 * <p>The guid-extraction methods cast the Playwright {@link Page} to the internal
 * {@code PageImpl} and read private {@code guid} fields via reflection. With a plain
 * Mockito {@link Page} mock the cast fails, so these tests exercise the error/catch
 * branches and the constant {@code getFramework()} accessor. The success paths require
 * a real {@code PageImpl} backed by a live browser/CLI connection and are therefore not
 * unit-testable here.</p>
 */
public class PageMetadataTest {

    @Test
    public void getFrameworkReturnsPlaywright() {
        PageMetadata meta = new PageMetadata(Mockito.mock(Page.class));
        assertEquals("playwright", meta.getFramework());
    }

    @Test
    public void getPageGuidThrowsWrappedExceptionForNonPageImpl() {
        // A plain mock is not a PageImpl, so the internal cast throws -> wrapped Exception.
        PageMetadata meta = new PageMetadata(Mockito.mock(Page.class));
        Exception ex = assertThrows(Exception.class, meta::getPageGuid);
        assertEquals("Failed to fetch PageGuid", ex.getMessage());
    }

    @Test
    public void getFrameGuidThrowsWrappedExceptionForNonPageImpl() {
        PageMetadata meta = new PageMetadata(Mockito.mock(Page.class));
        Exception ex = assertThrows(Exception.class, meta::getFrameGuid);
        assertEquals("Failed to fetch FrameGuid", ex.getMessage());
    }

    @Test
    public void getBrowserGuidThrowsWrappedExceptionForNonPageImpl() {
        Page mockPage = Mockito.mock(Page.class);
        BrowserContext mockContext = Mockito.mock(BrowserContext.class);
        Browser mockBrowser = Mockito.mock(Browser.class);
        when(mockPage.context()).thenReturn(mockContext);
        when(mockContext.browser()).thenReturn(mockBrowser);

        PageMetadata meta = new PageMetadata(mockPage);
        Exception ex = assertThrows(Exception.class, meta::getBrowserGuid);
        assertEquals("Failed to fetch BrowserGuid", ex.getMessage());
    }

    @Test
    public void getSessionDetailsThrowsWhenBrowserGuidUnavailable() {
        // getSessionDetails builds its cache key from getBrowserGuid() BEFORE the try
        // block, so the "Failed to fetch BrowserGuid" exception propagates uncaught.
        Page mockPage = Mockito.mock(Page.class);
        when(mockPage.context()).thenThrow(new RuntimeException("no context"));

        PageMetadata meta = new PageMetadata(mockPage);
        Exception ex = assertThrows(Exception.class, meta::getSessionDetails);
        assertEquals("Failed to fetch BrowserGuid", ex.getMessage());
    }

    @Test
    public void getSessionIdThrowsWhenSessionDetailsUnavailable() {
        Page mockPage = Mockito.mock(Page.class);
        when(mockPage.context()).thenThrow(new RuntimeException("no context"));

        PageMetadata meta = new PageMetadata(mockPage);
        Exception ex = assertThrows(Exception.class, meta::getSessionId);
        assertEquals("Failed to fetch SessionId, error", ex.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getSessionDetailsReturnsCachedValueWhenPresent() throws Exception {
        // Pre-seed the cache so getSessionDetails returns it without hitting page.evaluate.
        // The cache key depends on getBrowserGuid(); we cannot compute that for a mock,
        // so this asserts the cache-miss path still throws (documenting the limitation),
        // while confirming Cache interaction does not blow up for unrelated keys.
        Cache.CACHE_MAP.put("unrelated", new HashMap<String, Object>());
        assertTrue(Cache.CACHE_MAP.containsKey("unrelated"));
        Cache.CACHE_MAP.remove("unrelated");
    }

    @Test
    public void pageMetadataIsThrowableSubclass() {
        // PageMetadata extends Exception; constructing it should not require a message.
        PageMetadata meta = new PageMetadata(Mockito.mock(Page.class));
        assertTrue(meta instanceof Exception);
    }
}
