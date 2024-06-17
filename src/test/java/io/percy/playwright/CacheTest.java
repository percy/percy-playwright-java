package io.percy.playwright;

import java.io.IOException;

//import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class CacheTest {

    @BeforeAll
    public static void testSetup() throws IOException {
        Cache.CACHE_MAP.put("some-key-1", "value-abc");
        Map<String, String> value = new HashMap<>();
        value.put("a", "value-1");
        value.put("b", "value-2");
        Cache.CACHE_MAP.put("some-key-2", value);
    }
    @Test
    public void getCacheTest() {
        String v1 = (String) Cache.CACHE_MAP.get("some-key-1");
        assertEquals(v1, "value-abc");
        Map<String , String> v2 = (Map<String , String>) Cache.CACHE_MAP.get("some-key-2");
        Map<String, String> value = new HashMap<>();
        value.put("a", "value-1");
        value.put("b", "value-2");
        assertEquals(v2, value);
    }

    @Test void putCacheTest() {
        Cache.CACHE_MAP.clear();
        String v1 = (String) Cache.CACHE_MAP.get("some-key-1");
        assertNull(v1);
        Cache.CACHE_MAP.put("some-key-2", "value-1");
        assertEquals((String) Cache.CACHE_MAP.get("some-key-2"), "value-1");
    }
}
