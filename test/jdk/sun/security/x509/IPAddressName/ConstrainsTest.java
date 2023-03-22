/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import sun.security.x509.GeneralNameInterface;
import sun.security.x509.IPAddressName;

import java.io.IOException;

import static org.testng.Assert.assertEquals;

/*
 * @test
 * @summary Verify IPAddressName.constrains
 * @bug 8267617
 * @modules java.base/sun.security.x509
 * @run testng ConstrainsTest
 */
public class ConstrainsTest {

    IPAddressName ipv4Addr = new IPAddressName("127.0.0.1");
    IPAddressName ipv4Mask = new IPAddressName("127.0.0.0/255.0.0.0");
    IPAddressName ipv6Addr = new IPAddressName("::1");
    IPAddressName ipv6Mask = new IPAddressName("::/0");

    public ConstrainsTest() throws IOException {
    }

    @DataProvider(name = "names")
    public Object[][] names() {
        Object[][] data = {
                {ipv4Addr, ipv4Addr, GeneralNameInterface.NAME_MATCH},
                {ipv4Addr, ipv4Mask, GeneralNameInterface.NAME_WIDENS},
                {ipv4Addr, ipv6Addr, GeneralNameInterface.NAME_SAME_TYPE},
                {ipv4Addr, ipv6Mask, GeneralNameInterface.NAME_SAME_TYPE},
                {ipv4Mask, ipv4Addr, GeneralNameInterface.NAME_NARROWS},
                {ipv4Mask, ipv4Mask, GeneralNameInterface.NAME_MATCH},
                {ipv4Mask, ipv6Addr, GeneralNameInterface.NAME_SAME_TYPE},
                {ipv4Mask, ipv6Mask, GeneralNameInterface.NAME_SAME_TYPE},
                {ipv6Addr, ipv4Addr, GeneralNameInterface.NAME_SAME_TYPE},
                {ipv6Addr, ipv4Mask, GeneralNameInterface.NAME_SAME_TYPE},
                {ipv6Addr, ipv6Addr, GeneralNameInterface.NAME_MATCH},
                {ipv6Addr, ipv6Mask, GeneralNameInterface.NAME_WIDENS},
                {ipv6Mask, ipv4Addr, GeneralNameInterface.NAME_SAME_TYPE},
                {ipv6Mask, ipv4Mask, GeneralNameInterface.NAME_SAME_TYPE},
                {ipv6Mask, ipv6Addr, GeneralNameInterface.NAME_NARROWS},
                {ipv6Mask, ipv6Mask, GeneralNameInterface.NAME_MATCH},
        };
        return data;
    }

    @Test(dataProvider = "names")
    public void testNameContains(IPAddressName addr1, IPAddressName addr2, int result) throws IOException {
        assertEquals(addr1.constrains(addr2), result);
    }

}
