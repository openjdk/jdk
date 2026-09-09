/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic test of jimage protocol handler
 * @run junit ${test.main.class}
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


public class Basic {

    public static Object[][] urls() {
        Object[][] data = {
                {"jrt:/java.base/java/lang/Object.class", true},
                // Valid resource with and without percent-encoding.
                {"jrt:/java.base/java/lang/Runtime$Version.class", true},
                {"jrt:/java.base/java%2Flang%2FRuntime%24Version.class", true},
                // Unnecessary percent encoding (just Object again).
                {"jrt:/java.base/%6a%61%76%61%2f%6c%61%6e%67%2f%4f%62%6a%65%63%74%2e%63%6c%61%73%73", true},
                // Query parameters and fragments are silently ignored.
                {"jrt:/java.base/java/lang/Object.class?yes=no", true},
                {"jrt:/java.base/java/lang/Object.class#anchor", true},

                // Missing resource (no such class).
                {"jrt:/java.base/java/lang/NoSuchClass.class", false},
                // Missing resource (wrong module).
                {"jrt:/java.desktop/java/lang/Object.class", false},
                // Entries in jimage which don't reference resources.
                {"jrt:/modules/java.base/java/lang", false},
                {"jrt:/packages/java.lang", false},
                // Invalid (incomplete/corrupt) URIs.
                {"jrt:/", false},
                {"jrt:/java.base", false},
                {"jrt:/java.base/", false},
                // Cannot escape anything in the module name.
                {"jrt:/java%2Ebase/java/lang/Object.class", false},
        };
        return data;
    }

    @ParameterizedTest
    @MethodSource("urls")
    public void testConnect(String urlString, boolean exists) throws Exception {
        URL url = new URL(urlString);
        URLConnection uc = url.openConnection();
        try {
            uc.connect();
            if (!exists) fail("IOException expected");
        } catch (IOException ioe) {
            if (exists) fail("IOException not expected");
        }
    }

    @ParameterizedTest
    @MethodSource("urls")
    public void testInputStream(String urlString, boolean exists) throws Exception {
        URL url = new URL(urlString);
        URLConnection uc = url.openConnection();
        try {
            int b = uc.getInputStream().read();
            assertNotEquals(-1, b);
            if (!exists) fail("IOException expected");
        } catch (IOException ioe) {
            if (exists) fail("IOException not expected");
        }
    }

    @ParameterizedTest
    @MethodSource("urls")
    public void testContentLength(String urlString, boolean exists) throws Exception {
        URL url = new URL(urlString);
        int len = url.openConnection().getContentLength();
        assertTrue((exists && len > 0) || (!exists && len == -1));
    }

    @ParameterizedTest
    @MethodSource("urls")
    public void testGetContent(String urlString, boolean exists) throws Exception {
        URL url = new URL(urlString);
        try {
            Object obj = url.getContent();
            assertNotNull(obj);
            if (!exists) fail("IOException expected");
        } catch (IOException ioe) {
            if (exists) fail("IOException not expected");
        }
    }
}
