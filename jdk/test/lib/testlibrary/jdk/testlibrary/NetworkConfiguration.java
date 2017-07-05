/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.testlibrary;

import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import static java.net.NetworkInterface.getNetworkInterfaces;
import static java.util.Collections.list;

/**
 * Helper class for retrieving network interfaces and local addresses
 * suitable for testing.
 */
public class NetworkConfiguration {

    static final boolean isWindows =
            System.getProperty("os.name").startsWith("Windows");
    static final boolean isMacOS =
            System.getProperty("os.name").contains("OS X");

    private Map<NetworkInterface,List<Inet4Address>> ip4Interfaces;
    private Map<NetworkInterface,List<Inet6Address>> ip6Interfaces;

    private NetworkConfiguration(Map<NetworkInterface,List<Inet4Address>> ip4Interfaces,
                                 Map<NetworkInterface,List<Inet6Address>> ip6Interfaces)
    {
        this.ip4Interfaces = ip4Interfaces;
        this.ip6Interfaces = ip6Interfaces;
    }

    /**
     * Returns a stream of interfaces suitable for functional tests.
     */
    public Stream<NetworkInterface> interfaces() {
        return Stream.concat(ip4Interfaces(), ip6Interfaces())
                     .distinct();
    }

    /**
     * Returns a stream of interfaces suitable for IPv4 functional tests.
     */
    public Stream<NetworkInterface> ip4Interfaces() {
        return ip4Interfaces.keySet().stream()
                .filter(NetworkConfiguration::isNotExcludedInterface)
                .filter(hasIp4Addresses);
    }

    /**
     * Returns a stream of interfaces suitable for IPv6 functional tests.
     */
    public Stream<NetworkInterface> ip6Interfaces() {
        return ip6Interfaces.keySet().stream()
                .filter(NetworkConfiguration::isNotExcludedInterface)
                .filter(hasIp6Addresses);
    }

    private static boolean isNotExcludedInterface(NetworkInterface nif) {
        if (isMacOS && nif.getName().contains("awdl"))
            return false;
        String dName = nif.getDisplayName();
        if (isWindows && dName != null && dName.contains("Teredo"))
            return false;
        return true;
    }

    private final Predicate<NetworkInterface> hasIp4Addresses = nif -> {
        Optional<?> addr = ip4Interfaces.get(nif).stream()
                .filter(a -> !a.isAnyLocalAddress())
                .findAny();

        return addr.isPresent();
    };

    private final Predicate<NetworkInterface> hasIp6Addresses = nif -> {
        Optional<?> addr = ip6Interfaces.get(nif).stream()
                .filter(a -> !a.isAnyLocalAddress())
                .findAny();

        return addr.isPresent();
    };


    /**
     * Returns a stream of interfaces suitable for IPv4 multicast tests.
     */
    public Stream<NetworkInterface> ip4MulticastInterfaces() {
        return ip4Interfaces().filter(supportsIp4Multicast);
    }

    /**
     * Returns a stream of interfaces suitable for IPv6 multicast tests.
     */
    public Stream<NetworkInterface> ip6MulticastInterfaces() {
        return ip6Interfaces().filter(supportsIp6Multicast);
    }

    private final Predicate<NetworkInterface> supportsIp4Multicast = nif -> {
        try {
            if (!nif.supportsMulticast() || nif.isLoopback())
                return false;

            Optional<?> addr = ip4Interfaces.get(nif).stream()
                    .filter(a -> !a.isAnyLocalAddress())
                    .findAny();

            return addr.isPresent();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    };

    private final Predicate<NetworkInterface> supportsIp6Multicast = nif -> {
        try {
            if (!nif.supportsMulticast() || nif.isLoopback())
                return false;

            Optional<?> addr = ip6Interfaces.get(nif).stream()
                    .filter(a -> !a.isAnyLocalAddress())
                    .findAny();

            return addr.isPresent();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    };

    /**
     * Returns all addresses on all "functional" interfaces.
     */
    public Stream<InetAddress> addresses(NetworkInterface nif) {
        return Stream.concat(ip4Interfaces.get(nif).stream(),
                             ip6Interfaces.get(nif).stream());
    }

    /**
     * Returns all IPv4 addresses on all "functional" interfaces.
     */
    public Stream<Inet4Address> ip4Addresses() {
        return ip4Interfaces().flatMap(nif -> ip4Addresses(nif));
    }

    /**
     * Returns all IPv6 addresses on all "functional" interfaces.
     */
    public Stream<Inet6Address> ip6Addresses() {
        return ip6Interfaces().flatMap(nif -> ip6Addresses(nif));
    }

    /**
     * Returns all IPv4 addresses the given interface.
     */
    public Stream<Inet4Address> ip4Addresses(NetworkInterface nif) {
        return ip4Interfaces.get(nif).stream();
    }

    /**
     * Returns all IPv6 addresses for the given interface.
     */
    public Stream<Inet6Address> ip6Addresses(NetworkInterface nif) {
        return ip6Interfaces.get(nif).stream();
    }

    /**
     * Return a NetworkConfiguration instance.
     */
    public static NetworkConfiguration probe() throws IOException {
        Map<NetworkInterface, List<Inet4Address>> ip4Interfaces = new HashMap<>();
        Map<NetworkInterface, List<Inet6Address>> ip6Interfaces = new HashMap<>();

        List<NetworkInterface> nifs = list(getNetworkInterfaces());
        for (NetworkInterface nif : nifs) {
            // ignore interfaces that are down
            if (!nif.isUp() || nif.isPointToPoint())
                continue;

            List<Inet4Address> ip4Addresses = new LinkedList<>();
            List<Inet6Address> ip6Addresses = new LinkedList<>();
            ip4Interfaces.put(nif, ip4Addresses);
            ip6Interfaces.put(nif, ip6Addresses);
            for (InetAddress addr : list(nif.getInetAddresses())) {
                if (addr instanceof Inet4Address)
                    ip4Addresses.add((Inet4Address)addr);
                else if (addr instanceof Inet6Address)
                    ip6Addresses.add((Inet6Address)addr);
            }
        }
        return new NetworkConfiguration(ip4Interfaces, ip6Interfaces);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        interfaces().forEach(nif -> sb.append(interfaceInformation(nif)));
        return sb.toString();
    }

    /** Returns detailed information for the given interface. */
    public static String interfaceInformation(NetworkInterface nif) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("Display name: " + nif.getDisplayName() + "\n");
            sb.append("Name: " + nif.getName() + "\n");
            for (InetAddress inetAddress : list(nif.getInetAddresses()))
                sb.append("InetAddress: " + inetAddress + "\n");
            sb.append("Up? " + nif.isUp() + "\n");
            sb.append("Loopback? " + nif.isLoopback() + "\n");
            sb.append("PointToPoint? " + nif.isPointToPoint() + "\n");
            sb.append("Supports multicast? " + nif.supportsMulticast() + "\n");
            sb.append("Virtual? " + nif.isVirtual() + "\n");
            sb.append("Hardware address: " +
                    Arrays.toString(nif.getHardwareAddress()) + "\n");
            sb.append("MTU: " + nif.getMTU() + "\n");
            sb.append("Index: " + nif.getIndex() + "\n");
            sb.append("\n");
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Prints all the system interface information to the give stream. */
    public static void printSystemConfiguration(PrintStream out) {
        try {
            out.println("*** all system network interface configuration ***");
            List<NetworkInterface> nifs = list(getNetworkInterfaces());
            for (NetworkInterface nif : nifs)
                out.print(interfaceInformation(nif));
            out.println("*** end ***");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
