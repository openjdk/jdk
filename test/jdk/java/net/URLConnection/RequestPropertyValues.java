/*
 * Copyright (c) 2002, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4644775 6230836 8133686
 * @summary Test URLConnection Request Properties
 * @run main RequestPropertyValues
 */

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

/**
 * Part1:
 *   bug 4644775: Unexpected NPE in setRequestProperty(key, null) call
 * Part2:
 *   bug 6230836: A few methods of class URLConnection implemented incorrectly
 * Part3:
 *   bug 8133686: Preserving the insertion-order of getRequestProperties
 */

public class RequestPropertyValues {

    public static void main(String[] args) throws Exception {
        part1();
        part2();
        part3();
    }

    public static void part1() throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(new URL("http://localhost:8088"));
        urls.add(new URL("file:/etc/passwd"));
        urls.add(new URL("jar:http://foo.com/bar.html!/foo/bar"));
        if (hasFtp())
            urls.add(new URL("ftp://foo:bar@foobar.com/etc/passwd"));

        boolean failed = false;

        for (URL url : urls) {
            URLConnection uc = url.openConnection();
            try {
                uc.setRequestProperty("TestHeader", null);
            } catch (NullPointerException npe) {
                System.out.println("setRequestProperty is throwing NPE" +
                                " for url: " + url);
                failed = true;
            }
            try {
                uc.addRequestProperty("TestHeader", null);
            } catch (NullPointerException npe) {
                System.out.println("addRequestProperty is throwing NPE" +
                                " for url: " + url);
                failed = true;
            }
        }
        if (failed) {
            throw new Exception("RequestProperty setting/adding is" +
                                " throwing NPE for null values");
        }
    }

    public static void part2() throws Exception {
        URL url = null;
        String[] goodKeys = {"", "$", "key", "Key", " ", "    "};
        String[] goodValues = {"", "$", "value", "Value", " ", "    "};

        URLConnection conn = getConnection(url);

        for (int i = 0; i < goodKeys.length; ++i) {
            for (int j = 0; j < goodValues.length; ++j) {
                // If a property with the key already exists, overwrite its value with the new value
                conn.setRequestProperty(goodKeys[i], goodValues[j]);
                String value = conn.getRequestProperty(goodKeys[i]);

                if (!((goodValues[j] == null && value == null) || (value != null && value.equals(goodValues[j]))))
                    throw new RuntimeException("Method setRequestProperty(String,String) works incorrectly");
            }
        }
    }

     private static void part3() throws Exception{
        List<URL> urls = new ArrayList<>();

        urls.add(new URL("http://localhost:8088"));
        urls.add(new URL("jar:http://foo.com/bar.html!/foo/bar"));

        for(URL url : urls) {
            System.out.println("Testing " + url.toString().split(":")[0]);
            URLConnection urlConnection = url.openConnection();
            addCustomRequestProperties(urlConnection);
            testRequestPropertiesOrder(urlConnection);
        }
    }

    private static void addCustomRequestProperties(URLConnection urlConnection) {
        urlConnection.addRequestProperty("Testprop", "val1");
        urlConnection.addRequestProperty("Testprop", "val2");
        urlConnection.addRequestProperty("Testprop", "val3");
    }

    private static void testRequestPropertiesOrder(URLConnection con) {
        List<String> expectedTestRequestProperties = Arrays.asList("val1", "val2", "val3");

        Map<String, List<String>> requestProperties = con.getRequestProperties();

        List<String> actualTestRequestProperties = requestProperties.get("Testprop");
        Objects.requireNonNull(actualTestRequestProperties);

        if (!actualTestRequestProperties.equals(expectedTestRequestProperties)) {
            System.out.println("expectedTestRequestHeaders = " + expectedTestRequestProperties.toString());
            System.out.println("actualTestRequestHeaders = " + actualTestRequestProperties.toString());
            String errorMessageTemplate = "expectedTestRequestProperties = %s, actualTestRequestProperties = %s";
            throw new RuntimeException("expectedTestRequestProperties != actualTestRequestProperties for URL = " + con.getURL().toString() + String.format(errorMessageTemplate, expectedTestRequestProperties.toString(), actualTestRequestProperties.toString()));
        }
    }

    static URLConnection getConnection(URL url) {
        return new DummyURLConnection(url);
    }
    static class DummyURLConnection extends URLConnection {

        DummyURLConnection(URL url) {
            super(url);
        }
        public void connect() {
            connected = true;
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
