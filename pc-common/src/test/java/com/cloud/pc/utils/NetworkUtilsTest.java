package com.cloud.pc.utils;

import org.junit.Assert;
import org.junit.Test;

import java.net.SocketException;
import java.util.List;

public class NetworkUtilsTest {

    @Test
    public void Test_getLocalIpAddress() throws SocketException {
        System.out.println("=== Getting Local IP Address ===");

        // Method 1: Get special ip
        String localIp = NetworkUtils.getLocalIpAddress("no_this_device");
        Assert.assertEquals(localIp, "127.0.0.1");
        System.out.println("Primary IPv4 Address: " + (localIp != null ? localIp : "Not found"));

        // get first valid ip
        localIp = NetworkUtils.getLocalIpAddress("");
        Assert.assertNotEquals(localIp, "127.0.0.1");
    }
}