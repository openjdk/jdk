/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.fail;

import java.io.IOException;
import java.net.IDN;
import java.util.List;
import java.util.stream.Stream;
import sun.security.x509.DNSName;

/*
 * @test
 * @summary DNSName parsing tests
 * @bug 8213952 8186143 8381771
 * @library /test/lib
 * @modules java.base/sun.security.x509
 * @run main DNSNameTest
 */

public class DNSNameTest {

    private static final List<String> GOOD_NAMES = List.of(
            "abc",
            String.join(".", "a".repeat(63), "b".repeat(63),
                    "c".repeat(63), "d".repeat(61)),
            "abc.com",
            "tesT.Abc.com",
            "ABC.COM",
            "a12.com",
            "a1b2c3.com",
            "1abc.com",
            "123.com",
            "a-b-c.com", // hyphens
            IDN.toASCII("公司.江利子") // IDN punycode
    );

    private static final List<String> GOOD_SAN_NAMES = Stream.concat(
                    Stream.of(
                            "*.domain.com", // wildcard in 1st level subdomain
                            "*.com"),
                    GOOD_NAMES.stream())
            .toList();

    private static final List<String> BAD_NAMES = List.of(
            // DNSName too long
            String.join(".", "a".repeat(63), "b".repeat(63),
                    "c".repeat(63), "d".repeat(62)),
            // DNSName label too long
            "a".repeat(64),
            " 1abc.com", // begin with space
            "1abc.com ", // end with space
            "1a bc.com ", // no space allowed
            "-abc.com", // name begins with a hyphen
            "abc.com-", // name ends with a hyphen
            "abc.-com", // label begins with a hyphen
            "abc-.com", // label ends with a hyphen
            "a..b", // ..
            ".a", // begin with .
            "a.", // end with .
            "", // empty
            "  ",  // space only
            "*.domain.com", // wildcard not allowed
            "a*.com" // only allow letter, digit, or hyphen
    );

    private static final List<String> BAD_SAN_NAMES = Stream.concat(
                    Stream.of(
                            "*", //  wildcard only
                            "*.", //  wildcard with a period
                            "*a.com", // partial wildcard disallowed
                            "abc.*.com", // wildcard not allowed in 2nd level
                            "**.domain.com", // double wildcard not allowed
                            "*.domain.com*", // can't end with wildcard
                            "a*.com"), // only allow letter, digit, or hyphen
                    BAD_NAMES.stream().filter(n -> !n.contains("*")))
            .toList();


    public static void main(String[] args) {
        GOOD_NAMES.forEach(DNSNameTest::testGoodDNSName);
        GOOD_SAN_NAMES.forEach(DNSNameTest::testGoodSanDNSName);
        BAD_NAMES.forEach(DNSNameTest::testBadDNSName);
        BAD_SAN_NAMES.forEach(DNSNameTest::testBadSanDNSName);
    }

    private static void testGoodDNSName(String dnsNameString) {
        try {
            DNSName dn = new DNSName(dnsNameString);
        } catch (IOException e) {
            fail("Unexpected IOException with input " + dnsNameString + ": "
                    + e.getMessage());
        }
    }

    private static void testGoodSanDNSName(String dnsNameString) {
        try {
            DNSName dn = new DNSName(dnsNameString, true);
        } catch (IOException e) {
            fail("Unexpected IOException with input " + dnsNameString + ": "
                    + e.getMessage());
        }
    }

    private static void testBadDNSName(String dnsNameString) {
        try {
            DNSName dn = new DNSName(dnsNameString);
            fail("IOException expected with input " + dnsNameString);
        } catch (IOException e) {
            if (!e.getMessage().contains("DNSName")) {
                fail("Unexpected message: " + e);
            }
        }
    }

    private static void testBadSanDNSName(String dnsNameString) {
        try {
            DNSName dn = new DNSName(dnsNameString, true);
            fail("IOException expected with input " + dnsNameString);
        } catch (IOException e) {
            if (!e.getMessage().contains("DNSName")) {
                fail("Unexpected message: " + e);
            }
        }
    }
}
