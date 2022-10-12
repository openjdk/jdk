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

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.net.spi.InetAddressResolver.LookupPolicy.IPV4;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV4_FIRST;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV6;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV6_FIRST;

import jdk.test.lib.net.IPSupport;
import jdk.test.lib.NetworkConfiguration;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.testng.SkipException;

/*
 * @test
 * @summary Test that platform lookup characteristic value is correctly initialized from
 *  system properties affecting order and type of queried addresses.
 * @library lib providers/simple /test/lib
 * @build test.library/testlib.ResolutionRegistry simple.provider/impl.SimpleResolverProviderImpl
 *        jdk.test.lib.net.IPSupport LookupPolicyMappingTest
 * @run testng/othervm LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=true LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=system LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=true LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=true LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=false LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=system LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=false LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack -Djava.net.preferIPv6Addresses=true LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack -Djava.net.preferIPv6Addresses=false LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack -Djava.net.preferIPv6Addresses=system LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack -Djava.net.preferIPv6Addresses LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv6Addresses=true LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv6Addresses=false LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv6Addresses=system LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv6Addresses LookupPolicyMappingTest
 */

public class LookupPolicyMappingTest {

    @Test
    public void testSystemProperties() throws Exception {

        // Check if platform network configuration matches the test requirements,
        // if not throw a SkipException
        checkPlatformNetworkConfiguration();

        System.err.println("javaTest.org resolved to:" + Arrays.deepToString(
                InetAddress.getAllByName("javaTest.org")));

        // Acquire runtime characteristics from the test NSP
        int runtimeCharacteristics = impl.SimpleResolverProviderImpl.lastLookupPolicy().characteristics();

        // Calculate expected lookup policy characteristic
        String preferIPv4Stack = System.getProperty("java.net.preferIPv4Stack");
        String preferIPv6Addresses = System.getProperty("java.net.preferIPv6Addresses");
        String expectedResultsKey = calculateMapKey(preferIPv4Stack, preferIPv6Addresses);
        int expectedCharacteristics = EXPECTED_RESULTS_MAP.get(expectedResultsKey);

        Assert.assertTrue(characteristicsMatch(
                runtimeCharacteristics, expectedCharacteristics), "Unexpected LookupPolicy observed");
    }

    // Throws SkipException if platform doesn't support required IP address types
    static void checkPlatformNetworkConfiguration() {
        IPSupport.throwSkippedExceptionIfNonOperational();
        IPSupport.printPlatformSupport(System.err);
        NetworkConfiguration.printSystemConfiguration(System.err);
        // If preferIPv4=true and no IPv4 - skip
        if (IPSupport.preferIPv4Stack()) {
            if (!IPSupport.hasIPv4()) {
                throw new SkipException("Skip tests - IPv4 support required");
            }
            return;
        }
    }

    record ExpectedResult(String ipv4stack, String ipv6addresses, int characteristics) {
        ExpectedResult {
            if (!IPSupport.hasIPv4()) {
                characteristics = IPV6;
            } else if (!IPSupport.hasIPv6()) {
                characteristics = IPV4;
            }
        }

        public String key() {
            return calculateMapKey(ipv4stack, ipv6addresses);
        }
    }

    /*
     *  Each row describes a combination of 'preferIPv4Stack', 'preferIPv6Addresses'
     *  values and the expected characteristic value
     */
    private static List<ExpectedResult> EXPECTED_RESULTS_TABLE = List.of(
            new ExpectedResult("true", "true", IPV4),
            new ExpectedResult("true", "false", IPV4),
            new ExpectedResult("true", "system", IPV4),
            new ExpectedResult("true", "", IPV4),
            new ExpectedResult("true", null, IPV4),

            new ExpectedResult("false", "true", IPV4 | IPV6 | IPV6_FIRST),
            new ExpectedResult("false", "false", IPV4 | IPV6 | IPV4_FIRST),
            new ExpectedResult("false", "system", IPV4 | IPV6),
            new ExpectedResult("false", "", IPV4 | IPV6 | IPV4_FIRST),
            new ExpectedResult("false", null, IPV4 | IPV6 | IPV4_FIRST),

            new ExpectedResult("", "true", IPV4 | IPV6 | IPV6_FIRST),
            new ExpectedResult("", "false", IPV4 | IPV6 | IPV4_FIRST),
            new ExpectedResult("", "system", IPV4 | IPV6),
            new ExpectedResult("", "", IPV4 | IPV6 | IPV4_FIRST),
            new ExpectedResult("", null, IPV4 | IPV6 | IPV4_FIRST),

            new ExpectedResult(null, "true", IPV4 | IPV6 | IPV6_FIRST),
            new ExpectedResult(null, "false", IPV4 | IPV6 | IPV4_FIRST),
            new ExpectedResult(null, "system", IPV4 | IPV6),
            new ExpectedResult(null, "", IPV4 | IPV6 | IPV4_FIRST),
            new ExpectedResult(null, null, IPV4 | IPV6 | IPV4_FIRST));

    private static final Map<String, Integer> EXPECTED_RESULTS_MAP = calculateExpectedCharacteristics();

    private static Map<String, Integer> calculateExpectedCharacteristics() {
        return EXPECTED_RESULTS_TABLE.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ExpectedResult::key,
                        ExpectedResult::characteristics)
                );
    }

    private static String calculateMapKey(String ipv4stack, String ipv6addresses) {
        return ipv4stack + "_" + ipv6addresses;
    }

    private static boolean characteristicsMatch(int actual, int expected) {
        System.err.printf("Comparing characteristics:%n\tActual: %s%n\tExpected: %s%n",
                Integer.toBinaryString(actual),
                Integer.toBinaryString(expected));
        return (actual & (IPV4 | IPV6 | IPV4_FIRST | IPV6_FIRST)) == expected;
    }
}
