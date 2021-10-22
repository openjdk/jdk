/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8133686
 * @summary Ensuring that multiple header values for a given field-name are returned in
 *          the order they were added for HttpURLConnection.getRequestProperties
 *          and HttpURLConnection.getHeaderFields
 * @library /test/lib
 * @run testng URLConnectionHeadersOrder
 */

import jdk.test.lib.net.URIBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;

public class URLConnectionHeadersOrder {
    @Test
    public void testRequestPropertiesOrder() throws Exception {
        var url = URIBuilder.newBuilder()
                .scheme("http")
                .host(InetAddress.getLoopbackAddress())
                .toURL();

        var conn = new DummyHttpURLConnection(url);
        conn.addRequestProperty("test", "a");
        conn.addRequestProperty("test", "b");
        conn.addRequestProperty("test", "c");
        conn.connect();

        var expectedRequestProps = Arrays.asList("a", "b", "c");
        var actualRequestProps = conn.getRequestProperties().get("test");

        Assert.assertNotNull(actualRequestProps);

        String errorMessageTemplate = "Expected Request Properties = %s, Actual Request Properties = %s";
        Assert.assertEquals(actualRequestProps, expectedRequestProps, String.format(errorMessageTemplate, expectedRequestProps.toString(), actualRequestProps.toString()));
    }
}

class DummyHttpURLConnection extends URLConnection {

    /**
     * Constructs a URL connection to the specified URL. A connection to
     * the object referenced by the URL is not created.
     *
     * @param url the specified URL.
     */
    protected DummyHttpURLConnection(URL url) {
        super(url);
    }

    @Override
    public void connect() throws IOException {
        var connected = true;
    }
}