package com.cloud.pc.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class CacheLayerTest {
    @Test
    public void Test_CacheLayer() {
        CacheLayer cacheLayer = new CacheLayer(CacheLayer.MEMORY);
        assertEquals(cacheLayer.maxLayer(), CacheLayer.MEMORY);

        cacheLayer.addLayer(CacheLayer.DISK);
        assertEquals(cacheLayer.maxLayer(), CacheLayer.DISK);

        cacheLayer.addLayer(CacheLayer.REMOTE);
        assertEquals(cacheLayer.maxLayer(), CacheLayer.REMOTE);
    }
}