package com.cloud.pc.model;

public class CacheLayer {
    public static final int MEMORY = 1 << 0;    // 0001 (1)
    public static final int DISK = 1 << 1;      // 0010 (2)
    public static final int REMOTE = 1 << 2;  // 0100 (4)
    public static final int ALL = MEMORY | DISK | REMOTE; // 011

    private int value;

    public CacheLayer() {
        this.value = 0;
    }

    public CacheLayer(int value) {
        if (value < MEMORY || value > ALL) {
            this.value = ALL;
        } else {
            this.value = value;
        }
    }

    public int getValue() {
        return value;
    }

    public void addLayer(int layer) {
        value |= layer;
    }

    public void removeLayer(int layer) {
        value &= ~layer;
    }

    public boolean hasLayer(int layer) {
        return (value & layer) == layer;
    }

    public int maxLayer() {
        if (hasLayer(REMOTE)) {
            return REMOTE;
        }
        if (hasLayer(DISK)) {
            return DISK;
        }
        if (hasLayer(REMOTE)) {
            return REMOTE;
        }
        return value;
    }
}
