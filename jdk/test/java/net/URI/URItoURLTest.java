/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug  4768755 4677045
 * @summary URL.equal(URL) is inconsistant for opaque URI.toURL()
 *                      and new URL(URI.toString)
 *          URI.toURL() does not always work as specified
 */

import java.net.*;

public class URItoURLTest {

    public static void main(String args[]) throws Exception {

        URItoURLTest testClass = new URItoURLTest();
        URL classUrl = testClass.getClass().
                                    getResource("/java/lang/Object.class");

        String[] uris = { "mailto:xyz@abc.de",
                        "file:xyz#ab",
                        "http:abc/xyz/pqr",
                        "file:/C:/v700/dev/unitTesting/tests/apiUtil/uri",
                        "http:///p",
                        classUrl.toExternalForm(),
                        };

        boolean isTestFailed = false;
        boolean isURLFailed = false;

        for (int i = 0; i < uris.length; i++) {
            URI uri = URI.create(uris[i]);

            URL url1 = new URL(uri.toString());
            URL url2 = uri.toURL();
            System.out.println("Testing URI " + uri);

            if (!url1.equals(url2)) {
                System.out.println("equals() FAILED");
                isURLFailed = true;
            }
            if (url1.hashCode() != url2.hashCode()) {
                System.out.println("hashCode() DIDN'T MATCH");
                isURLFailed = true;
            }
            if (!url1.sameFile(url2)) {
                System.out.println("sameFile() FAILED");
                isURLFailed = true;
            }

            if (!equalsComponents("getPath()", url1.getPath(),
                                            url2.getPath())) {
                isURLFailed = true;
            }
            if (!equalsComponents("getFile()", url1.getFile(),
                                            url2.getFile())) {
                isURLFailed = true;
            }
            if (!equalsComponents("getHost()", url1.getHost(),
                                            url2.getHost())) {
                isURLFailed = true;
            }
            if (!equalsComponents("getAuthority()",
                                url1.getAuthority(), url2.getAuthority())) {
                isURLFailed = true;
            }
            if (!equalsComponents("getRef()", url1.getRef(),
                                            url2.getRef())) {
                isURLFailed = true;
            }
            if (!equalsComponents("getUserInfo()", url1.getUserInfo(),
                                            url2.getUserInfo())) {
                isURLFailed = true;
            }
            if (!equalsComponents("toString()", url1.toString(),
                                            url2.toString())) {
                isURLFailed = true;
            }

            if (isURLFailed) {
                isTestFailed = true;
            } else {
                System.out.println("PASSED ..");
            }
            System.out.println();
            isURLFailed = false;
        }
        if (isTestFailed) {
            throw new Exception("URI.toURL() test failed");
        }
    }

    static boolean equalsComponents(String method, String comp1, String comp2) {
        if ((comp1 != null) && (!comp1.equals(comp2))) {
            System.out.println(method + " DIDN'T MATCH" +
                        "  ===>");
                System.out.println("    URL(URI.toString()) returns:" + comp1);
                System.out.println("    URI.toURL() returns:" + comp2);
                return false;
        }
        return true;
    }
}
