/*
* Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
* @run junit ${test.main.class}
*/

import java.net.URLPermission;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class EmptyAuthorityTest {

    public static Object[][] badUrls() {
        return new Object[][]{
                { "http:///path" }, // empty authority
                { "https:///x" }, // empty authority
                { "http://@/x" }, // userinfo + empty host
                { "http://user@/x" }, // userinfo + empty host
                { "http://[]/x" } // empty IPv6 literal
        };
    }

    public static Object[][] goodUrls() {
        return new Object[][]{
                { "http://example.com/x" },
                { "http://example.com:80/x" },
                { "http://[::1]/x" },
                { "http://[::1]:8080/x" }
        };
    }

    @ParameterizedTest
    @MethodSource("badUrls")
    public void rejectsEmptyOrMalformedAuthority(String url) {
        assertThrows(IllegalArgumentException.class, () -> new URLPermission(url));
    }

    @ParameterizedTest
    @MethodSource("goodUrls")
    public void acceptsValidAuthorities(String url) {
        new URLPermission(url); // should not throw
    }
}
