/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4485208
 * @summary  file: and ftp: URL handlers need to throw NPE in setRequestProperty
 */

import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class RequestProperties {
    static int failed;

    public static void main (String args[]) throws Exception {
        List<String> urls = new ArrayList<>();
        urls.add("http://foo.com/bar/");
        urls.add("jar:http://foo.com/bar.html!/foo/bar");
        urls.add("file:/etc/passwd");
        if (hasFtp())
            urls.add("ftp://foo:bar@foobar.com/etc/passwd");

        for (String urlStr : urls)
            test(new URL(urlStr));

        if (failed != 0)
            throw new RuntimeException(failed + " errors") ;
    }

    static void test(URL url) throws Exception {
        URLConnection urlc = url.openConnection();
        try {
            urlc.setRequestProperty(null, null);
            System.out.println(url.getProtocol()
                               + ": setRequestProperty(null,) did not throw NPE");
            failed++;
        } catch (NullPointerException e) { /* Expected */ }
        try {
            urlc.addRequestProperty(null, null);
            System.out.println(url.getProtocol()
                               + ": addRequestProperty(null,) did not throw NPE");
            failed++;
        } catch (NullPointerException e)  { /* Expected */ }

        if (urlc.getRequestProperty(null) != null) {
            System.out.println(url.getProtocol()
                               + ": getRequestProperty(null,) did not return null");
            failed++;
        }
    }

    private static boolean hasFtp() {
        try {
            return new java.net.URL("ftp://") != null;
        } catch (java.net.MalformedURLException x) {
            System.out.println("FTP not supported by this runtime.");
            return false;
        }
    }
}
