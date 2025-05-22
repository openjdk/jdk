/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;

/*
 * @test
 * @summary Basic test to verify HTTP3 requests from HttpClient with security manager enabled
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run testng/othervm
 *              -Djdk.internal.httpclient.debug=true
 *              -Djdk.httpclient.HttpClient.log=requests,responses,errors
 *              H3SimpleTest
 * @run testng/othervm
 *              -Djdk.internal.httpclient.debug=true
 *              -Djdk.httpclient.HttpClient.log=requests,responses,errors
 *              -Djava.net.preferIPv6Addresses=true
 *              H3SimpleTest
 * @run testng/othervm
 *              -Djdk.internal.httpclient.debug=true
 *              -Djdk.httpclient.HttpClient.log=requests,responses,errors
 *              -Djava.net.preferIPv4Stack=true
 *              H3SimpleTest
 */
// -Djava.security.debug=all
public class H3SimpleTest implements HttpServerAdapters {

    private SSLContext sslContext;
    private HttpTestServer h3Server;
    private String requestURI;

    @BeforeClass
    public void beforeClass() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null) {
            throw new AssertionError("Unexpected null sslContext");
        }
        // create a H3 only server
        h3Server = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        h3Server.addHandler((exchange) -> exchange.sendResponseHeaders(200, 0), "/hello");
        h3Server.start();
        System.out.println("Server started at " + h3Server.getAddress());
        requestURI = "https://" + h3Server.serverAuthority() + "/hello";
    }

    @AfterClass
    public void afterClass() throws Exception {
        if (h3Server != null) {
            System.out.println("Stopping server " + h3Server.getAddress());
            h3Server.stop();
        }
    }

    /**
     * Issues various HTTP3 requests and verifies the responses are received
     */
    @Test
    public void testBasicRequests() throws Exception {
        final HttpClient client = newClientBuilderForH3()
                .proxy(NO_PROXY)
                .version(HTTP_3)
                .sslContext(sslContext).build();
        final URI reqURI = new URI(requestURI);
        final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(reqURI)
                .version(HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);

        // GET
        final HttpRequest req1 = reqBuilder.copy().GET().build();
        System.out.println("Issuing request: " + req1);
        final HttpResponse<Void> resp1 = client.send(req1, BodyHandlers.discarding());
        Assert.assertEquals(resp1.statusCode(), 200, "unexpected response code for GET request");

        // POST
        final HttpRequest req2 = reqBuilder.copy().POST(BodyPublishers.ofString("foo")).build();
        System.out.println("Issuing request: " + req2);
        final HttpResponse<Void> resp2 = client.send(req2, BodyHandlers.discarding());
        Assert.assertEquals(resp2.statusCode(), 200, "unexpected response code for POST request");

        // HEAD
        final HttpRequest req3 = reqBuilder.copy().HEAD().build();
        System.out.println("Issuing request: " + req3);
        final HttpResponse<Void> resp3 = client.send(req3, BodyHandlers.discarding());
        Assert.assertEquals(resp3.statusCode(), 200, "unexpected response code for HEAD request");
    }
}
