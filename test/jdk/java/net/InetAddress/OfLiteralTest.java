/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8272215
 * @summary Test for ofLiteral API in InetAddress classes
 * @run junit/othervm -Djdk.net.hosts.file=nonExistingHostsFile.txt
 *                     OfLiteralTest
 * @run junit/othervm -Djdk.net.hosts.file=nonExistingHostsFile.txt
 *                    -Djava.net.preferIPv4Stack=true
 *                     OfLiteralTest
 * @run junit/othervm -Djdk.net.hosts.file=nonExistingHostsFile.txt
 *                    -Djava.net.preferIPv6Addresses=true
 *                     OfLiteralTest
 * @run junit/othervm -Djdk.net.hosts.file=nonExistingHostsFile.txt
 *                    -Djava.net.preferIPv6Addresses=false
 *                     OfLiteralTest
 */

import org.junit.Assert;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OfLiteralTest {

    @ParameterizedTest
    @MethodSource("validLiteralArguments")
    public void validLiteral(InetAddressClass inetAddressClass,
                             String addressLiteral,
                             byte[] expectedAddressBytes) throws Exception {
        InetAddress ofLiteralResult = switch (inetAddressClass) {
            case INET_ADDRESS -> InetAddress.ofLiteral(addressLiteral);
            case INET4_ADDRESS -> Inet4Address.ofLiteral(addressLiteral);
            case INET6_ADDRESS -> Inet6Address.ofLiteral(addressLiteral);
        };
        InetAddress getByNameResult = InetAddress.getByName(addressLiteral);
        Assert.assertArrayEquals(expectedAddressBytes, ofLiteralResult.getAddress());
        Assert.assertEquals(getByNameResult, ofLiteralResult);
    }

    private static Stream<Arguments> validLiteralArguments() throws Exception {
        // 1080:0:0:0:8:800:200C:417A address bytes
        byte[] ipv6AddressExpBytes = new byte[]{16, -128, 0, 0, 0, 0, 0, 0, 0,
                8, 8, 0, 32, 12, 65, 122};

        // ::129.144.52.38 address bytes
        byte[] ipv4CompIpv6ExpBytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, -127, -112, 52, 38};

        // 222.173.190.239 address bytes
        byte[] ipv4ExpBytes = new byte[]{(byte) 222, (byte) 173,
                (byte) 190, (byte) 239};

        // 222.173.190.39 address bytes
        byte[] ipv4ExpBytes2 = new byte[]{(byte) 222, (byte) 173,
                (byte) 190, 39};

        // 1.2.3.4 address bytes
        byte[] oneToFourAddressExpBytes = new byte[]{1, 2, 3, 4};

        // 6.7.8.9 address bytes
        byte[] sixtoNineAddressExpBytes = new byte[]{6, 7, 8, 9};

        // ::FFFF:129.144.52.38 address bytes
        byte[] ipv6Ipv4MappedAddressExpBytes = new byte[]{
                (byte) 129, (byte) 144, 52, 38};

        Stream<Arguments> validLiterals = Stream.of(
                // IPv6 address literals are parsable by Inet6Address.ofLiteral
                // and InetAddress.ofLiteral methods
                Arguments.of(InetAddressClass.INET6_ADDRESS,
                        "1080:0:0:0:8:800:200C:417A", ipv6AddressExpBytes),
                Arguments.of(InetAddressClass.INET_ADDRESS,
                        "[1080:0:0:0:8:800:200C:417A]", ipv6AddressExpBytes),
                // Compressed series of zeros with square brackets
                Arguments.of(InetAddressClass.INET6_ADDRESS,
                        "[1080::8:800:200C:417A]", ipv6AddressExpBytes),
                // Compressed series of zeros without square brackets
                Arguments.of(InetAddressClass.INET_ADDRESS,
                        "1080::8:800:200C:417A", ipv6AddressExpBytes),
                // IPv4-mapped IPv6 address literals are parsable by
                // InetAddress.ofLiteral and Inet6Address.ofLiteral methods
                Arguments.of(InetAddressClass.INET_ADDRESS,
                        "::FFFF:129.144.52.38", ipv6Ipv4MappedAddressExpBytes),
                Arguments.of(InetAddressClass.INET_ADDRESS,
                        "[::ffff:1.2.3.4]", oneToFourAddressExpBytes),
                Arguments.of(InetAddressClass.INET6_ADDRESS,
                        "::FFFF:129.144.52.38", ipv6Ipv4MappedAddressExpBytes),
                Arguments.of(InetAddressClass.INET6_ADDRESS,
                        "[::ffff:1.2.3.4]", oneToFourAddressExpBytes),

                // IPv4-compatible IPv6 address literals are parsable by
                // Inet6Address.ofLiteral and InetAddress.ofLiteral methods
                Arguments.of(InetAddressClass.INET6_ADDRESS,
                        "::129.144.52.38", ipv4CompIpv6ExpBytes),
                Arguments.of(InetAddressClass.INET6_ADDRESS,
                        "[::129.144.52.38]", ipv4CompIpv6ExpBytes),
                Arguments.of(InetAddressClass.INET_ADDRESS,
                        "::129.144.52.38", ipv4CompIpv6ExpBytes),
                Arguments.of(InetAddressClass.INET_ADDRESS,
                        "[::129.144.52.38]", ipv4CompIpv6ExpBytes),

                // Tests for IPv4 address literal forms
                //      form:'d.d.d.d' method:Inet4Address.ofLiteral
                Arguments.of(InetAddressClass.INET4_ADDRESS,
                        "222.173.190.239", ipv4ExpBytes),
                //      form:'d.d.d.d' with decimal octet with leading zero
                Arguments.of(InetAddressClass.INET4_ADDRESS,
                        "222.173.190.039", ipv4ExpBytes2),
                Arguments.of(InetAddressClass.INET4_ADDRESS,
                        "06.07.08.09", sixtoNineAddressExpBytes),
                //      form:'d.d.d.d' method:InetAddress.ofLiteral
                Arguments.of(InetAddressClass.INET_ADDRESS,
                        "222.173.190.239", ipv4ExpBytes),
                //      form:'d.d.d' method:Inet4Address.ofLiteral
                Arguments.of(InetAddressClass.INET4_ADDRESS,
                        "222.173.48879", ipv4ExpBytes),
                //      form:'d.d.d' method:InetAddress.ofLiteral
                Arguments.of(InetAddressClass.INET_ADDRESS,
                        "222.173.48879", ipv4ExpBytes),
                //      form:'d.d' method:Inet4Address.ofLiteral
                Arguments.of(InetAddressClass.INET4_ADDRESS,
                        "222.11386607", ipv4ExpBytes),
                //      form:'d.d' method:InetAddress.ofLiteral
                Arguments.of(InetAddressClass.INET_ADDRESS,
                        "222.11386607", ipv4ExpBytes),
                //      form:'d' method:Inet4Address.ofLiteral
                Arguments.of(InetAddressClass.INET4_ADDRESS,
                        "3735928559", ipv4ExpBytes),
                //      form:'d' method:InetAddress.ofLiteral
                Arguments.of(InetAddressClass.INET_ADDRESS,
                        "3735928559", ipv4ExpBytes),
                //      form:'d' method:InetAddress.ofLiteral -
                //      with leading 0 that is discarded and address
                //      parsed as decimal
                Arguments.of(InetAddressClass.INET_ADDRESS,
                        "03735928559", ipv4ExpBytes)
        );

        // Generate addresses for loopback and wildcard address test cases
        var loopbackAndWildcardAddresses = List.of(
                // Loopback address
                InetAddress.getLoopbackAddress(),
                // IPv6 wildcard address
                InetAddress.getByName("::"),
                // IPv4 wildcard address
                InetAddress.getByName("0.0.0.0"));

        // Get addresses for all network interfaces available,
        // and construct a test case for each. And then combine
        // them with loopback/wildcard test cases.
        Stream<Arguments> hostAddressArguments = Stream.concat(
                        NetworkInterface.networkInterfaces()
                                .flatMap(NetworkInterface::inetAddresses),
                        loopbackAndWildcardAddresses.stream())
                .flatMap(OfLiteralTest::addressToValidTestCases);
        return Stream.concat(validLiterals, hostAddressArguments);
    }

    @ParameterizedTest
    @MethodSource("invalidLiteralArguments")
    public void invalidLiteral(InetAddressClass inetAddressClass,
                               String addressLiteral) {
        var executable = constructExecutable(inetAddressClass, addressLiteral);
        var exception = assertThrows(IllegalArgumentException.class, executable);
        System.err.println("Expected exception observed: " + exception);
    }

    @ParameterizedTest
    @EnumSource(InetAddressClass.class)
    public void nullLiteral(InetAddressClass inetAddressClass) {
        var executable = constructExecutable(inetAddressClass, null);
        assertThrows(NullPointerException.class, executable);
    }

    private static Stream<Arguments> invalidLiteralArguments() {
        Stream<Arguments> argumentsStream = Stream.of(
                // IPv4 address wrapped in square brackets
                Arguments.of(InetAddressClass.INET_ADDRESS, "[1.2.3.4]"),
                Arguments.of(InetAddressClass.INET4_ADDRESS, "[1.2.3.4]"),
                Arguments.of(InetAddressClass.INET6_ADDRESS, "[1.2.3.4]"),

                // IPv4 address literal with BSD-formatting
                Arguments.of(InetAddressClass.INET_ADDRESS, "1.2.3.0256"),
                Arguments.of(InetAddressClass.INET4_ADDRESS, "1.2.3.0256"),

                // Invalid IPv4-mapped IPv6 address forms
                //      ::FFFF:d.d.d
                Arguments.of(InetAddressClass.INET_ADDRESS, "::FFFF:1.2.3"),
                Arguments.of(InetAddressClass.INET6_ADDRESS, "::FFFF:1.2.3"),

                //      ::FFFF:d.d
                Arguments.of(InetAddressClass.INET_ADDRESS, "::FFFF:1.2"),
                Arguments.of(InetAddressClass.INET6_ADDRESS, "::FFFF:1.2"),
                //      ::d.d.d
                Arguments.of(InetAddressClass.INET_ADDRESS, "::1.2.3"),
                Arguments.of(InetAddressClass.INET6_ADDRESS, "::1.2.3"),
                //      ::d.d
                Arguments.of(InetAddressClass.INET_ADDRESS, "::1.2"),
                Arguments.of(InetAddressClass.INET6_ADDRESS, "::1.2"),

                // IPv4-mapped IPv6 address with scope-id
                Arguments.of(InetAddressClass.INET6_ADDRESS,
                        "::FFFF:129.144.52.38%1"),

                // IPv4-mapped IPv6 addresses cannot be parsed by Inet4Address.ofLiteral
                Arguments.of(InetAddressClass.INET4_ADDRESS,
                        "::FFFF:129.144.52.38"),
                Arguments.of(InetAddressClass.INET4_ADDRESS,
                        "[::ffff:1.2.3.4]"),

                // IPv4 literals in BSD form
                Arguments.of(InetAddressClass.INET_ADDRESS, "0256.1.2.3"),
                Arguments.of(InetAddressClass.INET4_ADDRESS, "1.2.0256.3"),
                Arguments.of(InetAddressClass.INET_ADDRESS, "0x1.2.3.4"),
                Arguments.of(InetAddressClass.INET4_ADDRESS, "1.2.0x3.4"),
                Arguments.of(InetAddressClass.INET_ADDRESS, "0xFFFFFFFF"),
                Arguments.of(InetAddressClass.INET4_ADDRESS, "0xFFFFFFFF")
        );
        // Construct arguments for a test case with IPv6-scoped address with scope-id
        // specified as a string with non-existing network interface name
        String ifName = generateNonExistingIfName();
        Stream<Arguments> nonExistingIFinScope = ifName.isBlank() ? Stream.empty() :
                Stream.of(Arguments.of(InetAddressClass.INET6_ADDRESS,
                        "2001:db8:a0b:12f0::1%" + ifName));
        return Stream.concat(argumentsStream, nonExistingIFinScope);
    }

    private static Stream<Arguments> addressToValidTestCases(InetAddress inetAddress) {
        String addressLiteral = inetAddress.getHostAddress();
        byte[] expectedAddressBytes = inetAddress.getAddress();

        InetAddressClass addressClass = switch (inetAddress) {
            case Inet4Address i4 -> InetAddressClass.INET4_ADDRESS;
            case Inet6Address i6 -> InetAddressClass.INET6_ADDRESS;
            case InetAddress ia -> InetAddressClass.INET_ADDRESS;
        };
        return Stream.of(
                Arguments.of(InetAddressClass.INET_ADDRESS, addressLiteral, expectedAddressBytes),
                Arguments.of(addressClass, addressLiteral, expectedAddressBytes));
    }

    private static String generateNonExistingIfName() {
        try {
            // At least two network interfaces are required to generate
            // a non-existing interface name
            if (NetworkInterface.networkInterfaces().count() < 2) {
                return "";
            }
            return NetworkInterface
                    .networkInterfaces()
                    .map(NetworkInterface::getName)
                    .collect(Collectors.joining())
                    .strip();
        } catch (SocketException e) {
            return "";
        }
    }

    private static Executable constructExecutable(InetAddressClass inetAddressClass, String input) {
        return switch (inetAddressClass) {
            case INET_ADDRESS -> () -> InetAddress.ofLiteral(input);
            case INET4_ADDRESS -> () -> Inet4Address.ofLiteral(input);
            case INET6_ADDRESS -> () -> Inet6Address.ofLiteral(input);
        };
    }

    enum InetAddressClass {
        INET_ADDRESS,
        INET4_ADDRESS,
        INET6_ADDRESS
    }
}

