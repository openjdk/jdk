/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.assertEquals;
import static sun.security.x509.GeneralNameInterface.NAME_MATCH;
import static sun.security.x509.GeneralNameInterface.NAME_NARROWS;
import static sun.security.x509.GeneralNameInterface.NAME_SAME_TYPE;
import static sun.security.x509.GeneralNameInterface.NAME_WIDENS;

import java.io.IOException;
import java.util.List;
import sun.security.x509.IPAddressName;

/*
 * @test
 * @summary Verify IPAddressName.constrains
 * @bug 8267617
 * @library /test/lib
 * @modules java.base/sun.security.x509
 */

public class ConstrainsTest {

    IPAddressName ipv4Addr = new IPAddressName("127.0.0.1");
    IPAddressName ipv4Mask = new IPAddressName("127.0.0.0/255.0.0.0");
    IPAddressName ipv6Addr = new IPAddressName("2001:db8::1");
    IPAddressName ipv6Mask = new IPAddressName("2001:db8::/124");

    IPAddressName ipv4Addr2 = new IPAddressName("128.0.0.1");
    IPAddressName ipv4NarrowMask = new IPAddressName("127.0.0.0/255.255.0.0");
    IPAddressName ipv4DisjointMask = new IPAddressName("128.0.0.0/255.0.0.0");

    IPAddressName ipv6Addr2 = new IPAddressName("2001:db8:1::1");
    IPAddressName ipv6NarrowMask = new IPAddressName("2001:db8::/125");
    IPAddressName ipv6DisjointMask = new IPAddressName("2001:db8:1::/124");

    public ConstrainsTest() throws IOException {
    }

    private Object[][] names() {
        return new Object[][]{
                // Basic matching
                {ipv4Addr, ipv4Addr, NAME_MATCH},
                {ipv4Addr, ipv4Mask, NAME_WIDENS},
                {ipv4Addr, ipv6Addr, NAME_SAME_TYPE},
                {ipv4Addr, ipv6Mask, NAME_SAME_TYPE},
                {ipv4Mask, ipv4Addr, NAME_NARROWS},
                {ipv4Mask, ipv4Mask, NAME_MATCH},
                {ipv4Mask, ipv6Addr, NAME_SAME_TYPE},
                {ipv4Mask, ipv6Mask, NAME_SAME_TYPE},
                {ipv6Addr, ipv4Addr, NAME_SAME_TYPE},
                {ipv6Addr, ipv4Mask, NAME_SAME_TYPE},
                {ipv6Addr, ipv6Addr, NAME_MATCH},
                {ipv6Addr, ipv6Mask, NAME_WIDENS},
                {ipv6Mask, ipv4Addr, NAME_SAME_TYPE},
                {ipv6Mask, ipv4Mask, NAME_SAME_TYPE},
                {ipv6Mask, ipv6Addr, NAME_NARROWS},
                {ipv6Mask, ipv6Mask, NAME_MATCH},

                // Same-family host mismatches
                {ipv4Addr, ipv4Addr2, NAME_SAME_TYPE},
                {ipv6Addr, ipv6Addr2, NAME_SAME_TYPE},

                // Host to subnet match, nothing matches
                {ipv4Addr2, ipv4Mask, NAME_SAME_TYPE},
                {ipv6Addr2, ipv6Mask, NAME_SAME_TYPE},

                // Subnet to subnet match: narrow/widen/disjoint
                {ipv4Mask, ipv4NarrowMask, NAME_NARROWS},
                {ipv4NarrowMask, ipv4Mask, NAME_WIDENS},
                {ipv4Mask, ipv4DisjointMask, NAME_SAME_TYPE},

                {ipv6Mask, ipv6NarrowMask, NAME_NARROWS},
                {ipv6NarrowMask, ipv6Mask, NAME_WIDENS},
                {ipv6Mask, ipv6DisjointMask, NAME_SAME_TYPE},
            };
    }

    public static void main(String[] args) throws Exception {
        List.of(new ConstrainsTest().names()).forEach(v -> testNameContains(
                (IPAddressName) v[0], (IPAddressName) v[1], (int) v[2]));
    }

    private static void testNameContains(IPAddressName addr1,
            IPAddressName addr2, int result) {
        assertEquals(addr1.constrains(addr2), result);
    }
}
