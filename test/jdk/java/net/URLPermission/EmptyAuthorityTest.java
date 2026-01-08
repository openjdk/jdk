/*
* Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
* @test
* @bug 8367049
* @summary URLPermission must reject empty/missing host authority with IAE (no SIOOBE)
* @run testng EmptyAuthorityTest
*/

import java.net.URLPermission;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class EmptyAuthorityTest {

    @DataProvider(name = "badUrls")
    public Object[][] badUrls() {
        return new Object[][]{
                { "http:///path" }, // empty authority
                { "https:///x" }, // empty authority
                { "http://@/x" }, // userinfo + empty host
                { "http://user@/x" }, // userinfo + empty host
                { "http://[]/x" } // empty IPv6 literal
        };
    }

    @DataProvider(name = "goodUrls")
    public Object[][] goodUrls() {
        return new Object[][]{
                { "http://example.com/x" },
                { "http://example.com:80/x" },
                { "http://[::1]/x" },
                { "http://[::1]:8080/x" }
        };
    }

    @Test(dataProvider = "badUrls")
    public void rejectsEmptyOrMalformedAuthority(String url) {
        Assert.expectThrows(IllegalArgumentException.class, () -> new URLPermission(url));
    }

    @Test(dataProvider = "goodUrls")
    public void acceptsValidAuthorities(String url) {
        new URLPermission(url); // should not throw
    }
}
