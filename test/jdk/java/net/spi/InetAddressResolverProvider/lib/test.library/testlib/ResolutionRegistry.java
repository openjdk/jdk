/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package testlib;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver.LookupPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Comparator;

import static java.net.spi.InetAddressResolver.LookupPolicy.*;

public class ResolutionRegistry {

    // Map to store hostName -> InetAddress mappings
    private final Map<String, List<byte[]>> registry;
    private static final int IPV4_RAW_LEN = 4;
    private static final int IPV6_RAW_LEN = 16;

    private static final Logger LOGGER = Logger.getLogger(ResolutionRegistry.class.getName());

    public ResolutionRegistry() {

        // Populate registry from test data file
        String fileName = System.getProperty("test.dataFileName", "addresses.txt");
        Path addressesFile = Paths.get(System.getProperty("test.src", ".")).resolve(fileName);
        LOGGER.info("Creating ResolutionRegistry instance from file:" + addressesFile);
        registry = parseDataFile(addressesFile);
    }

    private Map<String, List<byte[]>> parseDataFile(Path addressesFile) {
        try {
            if (addressesFile.toFile().isFile()) {
                Map<String, List<byte[]>> resReg = new ConcurrentHashMap<>();
                // Prepare list of hostname/address entries
                List<String[]> entriesList = Files.readAllLines(addressesFile).stream()
                        .map(String::trim)
                        .filter(Predicate.not(String::isBlank))
                        .filter(s -> !s.startsWith("#"))
                        .map(s -> s.split("\\s+"))
                        .filter(sarray -> sarray.length == 2)
                        .filter(ResolutionRegistry::hasLiteralAddress)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                // Convert list of entries into registry Map
                for (var entry : entriesList) {
                    String ipAddress = entry[0].trim();
                    String hostName = entry[1].trim();
                    byte[] addrBytes = toByteArray(ipAddress);
                    if (addrBytes != null) {
                        var list = resReg.containsKey(hostName) ? resReg.get(hostName) : new ArrayList();
                        list.add(addrBytes);
                        if (!resReg.containsKey(hostName)) {
                            resReg.put(hostName, list);
                        }
                    }
                }
                resReg.replaceAll((k, v) -> Collections.unmodifiableList(v));
                // Print constructed registry
                StringBuilder sb = new StringBuilder("Constructed addresses registry:" + System.lineSeparator());
                for (var entry : resReg.entrySet()) {
                    sb.append("\t" + entry.getKey() + ": ");
                    for (byte[] addr : entry.getValue()) {
                        sb.append(addressBytesToString(addr) + " ");
                    }
                    sb.append(System.lineSeparator());
                }
                LOGGER.info(sb.toString());
                return resReg;
            } else {
                // If file doesn't exist - return empty map
                return Collections.emptyMap();
            }
        } catch (IOException ioException) {
            // If any problems parsing the file - log a warning and return an empty map
            LOGGER.log(Level.WARNING, "Error reading data file", ioException);
            return Collections.emptyMap();
        }
    }

    // Line is not a blank and not a comment
    private static boolean hasLiteralAddress(String[] lineFields) {
        String addressString = lineFields[0].trim();
        return addressString.charAt(0) == '[' ||
                Character.digit(addressString.charAt(0), 16) != -1 ||
                (addressString.charAt(0) == ':');
    }

    // Line is not blank and not comment
    private static byte[] toByteArray(String addressString) {
        InetAddress address;
        // Will reuse InetAddress functionality to parse literal IP address
        // strings. This call is guarded by 'hasLiteralAddress' method.
        try {
            address = InetAddress.getByName(addressString);
        } catch (UnknownHostException unknownHostException) {
            LOGGER.warning("Can't parse address string:'" + addressString + "'");
            return null;
        }
        return address.getAddress();
    }

    public Stream<InetAddress> lookupHost(String host, LookupPolicy lookupPolicy)
            throws UnknownHostException {
        LOGGER.info("Looking-up '" + host + "' address");
        if (!registry.containsKey(host)) {
            LOGGER.info("Registry doesn't contain addresses for '" + host + "'");
            throw new UnknownHostException(host);
        }

        int characteristics = lookupPolicy.characteristics();
        // Filter IPV4 or IPV6 as needed. Then sort with
        // comparator for IPV4_FIRST or IPV6_FIRST.
        return registry.get(host)
                .stream()
                .filter(ba -> filterAddressByLookupPolicy(ba, characteristics))
                .sorted(new AddressOrderPref(characteristics))
                .map(ba -> constructInetAddress(host, ba))
                .filter(Objects::nonNull);
    }

    private static boolean filterAddressByLookupPolicy(byte[] ba, int ch) {
        // If 0011, return both. If 0001, IPv4. If 0010, IPv6
        boolean ipv4Flag = (ch & IPV4) == IPV4;
        boolean ipv6Flag = (ch & IPV6) == IPV6;

        if (ipv4Flag && ipv6Flag)
            return true; // Return regardless of length
        else if (ipv4Flag)
            return (ba.length == IPV4_RAW_LEN);
        else if (ipv6Flag)
            return (ba.length == IPV6_RAW_LEN);

        throw new RuntimeException("Lookup policy characteristics were improperly set. " +
                "Characteristics: " + Integer.toString(ch, 2));
    }

    private static InetAddress constructInetAddress(String host, byte[] address) {
        try {
            return InetAddress.getByAddress(host, address);
        } catch (UnknownHostException unknownHostException) {
            return null;
        }
    }

    public String lookupAddress(byte[] addressBytes) {
        for (var entry : registry.entrySet()) {
            if (entry.getValue()
                    .stream()
                    .filter(ba -> Arrays.equals(ba, addressBytes))
                    .findAny()
                    .isPresent()) {
                return entry.getKey();
            }
        }
        try {
            return InetAddress.getByAddress(addressBytes).getHostAddress();
        } catch (UnknownHostException unknownHostException) {
            throw new IllegalArgumentException();
        }
    }

    public boolean containsAddressMapping(InetAddress address) {
        String hostName = address.getHostName();
        if (registry.containsKey(hostName)) {
            var mappedBytes = registry.get(address.getHostName());
            for (byte[] mappedAddr : mappedBytes) {
                if (Arrays.equals(mappedAddr, address.getAddress())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String addressBytesToString(byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes).toString();
        } catch (UnknownHostException unknownHostException) {
            return Arrays.toString(bytes);
        }
    }

    private class AddressOrderPref implements Comparator<byte[]> {

        private final int ch;

        AddressOrderPref(int ch) {
            this.ch = ch;
        }

        @Override
        public int compare(byte[] o1, byte[] o2) {
            // Compares based on address length, 4 bytes for IPv4,
            // 16 bytes for IPv6.
            return ((ch & IPV4_FIRST) == IPV4_FIRST) ?
                    Integer.compare(o1.length, o2.length) :
                    Integer.compare(o2.length, o1.length);
        }
    }
}
