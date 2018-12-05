/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary DNSName parsing tests
 * @bug 8213952
 * @modules java.base/sun.security.x509
 * @run testng DNSNameTest
 */

import java.io.IOException;
import sun.security.x509.DNSName;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class DNSNameTest {
    @DataProvider(name = "goodNames")
    public Object[][] goodNames() {
        Object[][] data = {
                {"abc.com"},
                {"ABC.COM"},
                {"a12.com"},
                {"a1b2c3.com"},
                {"1abc.com"},
                {"123.com"},
                {"abc.com-"}, // end with hyphen
                {"a-b-c.com"}, // hyphens
        };
        return data;
    }

    @DataProvider(name = "badNames")
    public Object[][] badNames() {
        Object[][] data = {
                {" 1abc.com"}, // begin with space
                {"1abc.com "}, // end with space
                {"1a bc.com "}, // no space allowed
                {"-abc.com"}, // begin with hyphen
                {"a..b"}, // ..
                {".a"}, // begin with .
                {"a."}, // end with .
                {""}, // empty
                {"  "},  // space only
        };
        return data;
    }

    @Test(dataProvider = "goodNames")
    public void testGoodDNSName(String dnsNameString) {
        try {
            DNSName dn = new DNSName(dnsNameString);
        } catch (IOException e) {
            fail("Unexpected IOException");
        }
    }

    @Test(dataProvider = "badNames")
    public void testBadDNSName(String dnsNameString) {
        try {
            DNSName dn = new DNSName(dnsNameString);
            fail("IOException expected");
        } catch (IOException e) {
            if (!e.getMessage().contains("DNSName"))
                fail("Unexpeceted message: " + e);
        }
    }
}
