/*
 * Copyright (c) 2025 Yangagile. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloud.pc.utils;

import org.apache.commons.lang3.StringUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class NetworkUtils {

    /**
     * Get local IPv4 address
     * @return The first non-loopback, non-link-local, non-virtual IPv4 address
     * @throws SocketException
     */
    public static String getLocalIpAddress(String interfaceName) throws SocketException {
        String ip = "127.0.0.1";
        if (StringUtils.isNotBlank(interfaceName)) {
            String sip = getIpByInterfaceName(interfaceName);
            if (StringUtils.isNotBlank(sip)) {
                ip = sip;
            }
        } else {
            List<String> ipAddresses = getAllLocalIpAddresses();
            if (!ipAddresses.isEmpty()) {
                ip = ipAddresses.get(0);
            }
        }
        return ip;
    }

    /**
     * Get IP address for specific network interface
     * @param interfaceName Network interface name (e.g., "en0", "eth0")
     * @return IPv4 address for that interface
     */
    public static String getIpByInterfaceName(String interfaceName) throws SocketException {
        NetworkInterface iface = NetworkInterface.getByName(interfaceName);
        if (iface != null && iface.isUp() && !iface.isLoopback()) {
            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    return addr.getHostAddress();
                }
            }
        }
        return null;
    }

    /**
     * Get all local IP addresses matching criteria
     * @return List of qualified IP addresses
     * @throws SocketException
     */
    public static List<String> getAllLocalIpAddresses()
            throws SocketException {
        List<String> ipList = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();

            // Skip interfaces that don't meet criteria
            if (!isValidInterface(iface)) {
                continue;
            }

            // Get all IP addresses for this interface
            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();

                // Filter IP addresses based on parameters
                if (isValidAddress(addr)) {
                    ipList.add(addr.getHostAddress());
                }
            }
        }

        return ipList;
    }

    /**
     * Check if network interface is valid
     */
    private static boolean isValidInterface(NetworkInterface iface) throws SocketException {
        // Interface must be up
        if (!iface.isUp()) {
            return false;
        }

        // Exclude loopback interfaces
        if (iface.isLoopback()) {
            return false;
        }

        // Better virtual interface detection (cross-platform)
        try {
            if (isVirtualInterface(iface)) {
                return false;
            }
        } catch (SocketException e) {
            // If we can't determine, continue with other checks
        }

        // Exclude specific types of interfaces
        String name = iface.getName();
        String displayName = iface.getDisplayName().toLowerCase();

        // Common virtual/tunnel interface filtering
        if (name.startsWith("docker") ||
                name.startsWith("br-") ||
                name.startsWith("veth") ||
                name.startsWith("virbr") ||
                name.contains("virtual") ||
                displayName.contains("virtual") ||
                name.startsWith("vmnet") ||
                name.startsWith("vboxnet") ||
                name.startsWith("tun") ||
                name.startsWith("tap") ||
                displayName.contains("virtualbox") ||
                displayName.contains("vmware") ||
                displayName.contains("hyper-v")) {
            return false;
        }

        return true;
    }

    /**
     * Check if IP address is valid
     */
    private static boolean isValidAddress(InetAddress addr) {
        // Exclude loopback addresses
        if (addr.isLoopbackAddress()) {
            return false;
        }

        // Exclude link-local addresses (169.254.x.x)
        if (addr.isLinkLocalAddress()) {
            return false;
        }

        // Filter IPv4/IPv6 based on parameter
        if (!(addr instanceof Inet4Address)) {
            return false;
        }

        return true;
    }

    /**
     * Detect if interface is virtual
     */
    private static boolean isVirtualInterface(NetworkInterface iface) throws SocketException {
        // Method 1: Check if virtual interface (some platforms support this)
        try {
            if (iface.isVirtual()) {
                return true;
            }
        } catch (Exception e) {
            // Some platforms might not support isVirtual method
        }

        // Method 2: Check if parent interface exists
        NetworkInterface parent = iface.getParent();
        if (parent != null) {
            return true;
        }

        return false;
    }

}
