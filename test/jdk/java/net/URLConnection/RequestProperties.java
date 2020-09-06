/*
 * Copyright (c) 2001, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4485208 8252767
 * @summary  Validate java.net.URLConnection#setRequestProperty throws NPE and IllegalStateException
 */

import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

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
            testSetRequestPropNPE(new URL(urlStr));

        if (failed != 0)
            throw new RuntimeException(failed + " errors") ;

        testRequestPropIllegalStateException();
    }

    static void testSetRequestPropNPE(URL url) throws Exception {
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

    /**
     * Test that various request property handling methods on {@link java.net.URLConnection}  throw
     * an {@link IllegalStateException} when already connected
     */
    private static void testRequestPropIllegalStateException() throws Exception {
        final URL url = Path.of(System.getProperty("java.io.tmpdir")).toUri().toURL();
        final URLConnection conn = url.openConnection();
        conn.connect();
        try {
            // test setRequestProperty
            expectIllegalStateException(
                    () -> {
                        conn.setRequestProperty("foo", "bar");
                        return null;
                    }, "setRequestProperty on " + conn.getClass().getName()
                            + " for " + url + " was expected to throw"
                            + " IllegalStateException, but didn't");
            // test addRequestProperty
            expectIllegalStateException(
                    () -> {
                        conn.addRequestProperty("foo", "bar");
                        return null;
                    }, "addRequestProperty on " + conn.getClass().getName()
                            + " for " + url + " was expected to throw"
                            + " IllegalStateException, but didn't");
            // test getRequestProperty
            expectIllegalStateException(
                    () -> {
                        conn.getRequestProperty("foo");
                        return null;
                    }, "getRequestProperty on " + conn.getClass().getName()
                            + " for " + url + " was expected to throw"
                            + " IllegalStateException, but didn't");
            // test getRequestProperties
            expectIllegalStateException(
                    () -> {
                        conn.getRequestProperties();
                        return null;
                    }, "getRequestProperties on " + conn.getClass().getName()
                            + " for " + url + " was expected to throw"
                            + " IllegalStateException, but didn't");
        } finally {
            try {
                conn.getInputStream().close();
            } catch (Exception e) {
                // ignore
            }
        }

    }

    /**
     * Calls the {@code operation} and expects it to throw an {@link IllegalStateException}.
     * If no such exception is thrown then this method throws a {@link RuntimeException}
     * with the passed {@code unmetExpectationErrorMessage} as the exception's message.
     *
     * @param operation                    The operation to invoke
     * @param unmetExpectationErrorMessage The error message to be set in the
     *                                     RuntimeException that will be thrown if the operation
     *                                     doesn't result in an IllegalStateException
     */
    private static void expectIllegalStateException(final Callable<Void> operation,
                                                    final String unmetExpectationErrorMessage)
            throws Exception {
        try {
            operation.call();
            // the expected IllegalStateException wasn't throw
            throw new RuntimeException(unmetExpectationErrorMessage);
        } catch (IllegalStateException ise) {
            // expected
        }
    }
}
