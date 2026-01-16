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

/*
 * @test
 * @bug 8157105
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run testng/othervm/timeout=60 -Djavax.net.debug=ssl -Djdk.httpclient.HttpClient.log=all BadCipherSuiteErrorTest
 * @summary check exception thrown when bad TLS parameters selected
 */

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import jdk.httpclient.test.lib.common.HttpServerAdapters;

import jdk.test.lib.Asserts;
import jdk.test.lib.net.SimpleSSLContext;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;

import org.testng.annotations.Test;

/**
 * When selecting an unacceptable cipher suite the TLS handshake will fail.
 * But, the exception that was thrown was not being returned up to application
 * causing hang problems
 */
public class BadCipherSuiteErrorTest implements HttpServerAdapters {

    static final String[] CIPHER_SUITES = new String[]{ "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384" };

    static final String SIMPLE_STRING = "Hello world Goodbye world";

    //@Test(timeOut=5000)
    @Test
    public void test() throws Exception {
        SSLContext sslContext = SimpleSSLContext.findSSLContext();
        ExecutorService exec = Executors.newCachedThreadPool();
        var builder = newClientBuilderForH3()
                                      .executor(exec)
                                      .sslContext(sslContext)
                                      .version(HTTP_3);
        var goodclient = builder.build();
        var badclient = builder
                .sslParameters(new SSLParameters(CIPHER_SUITES))
                .build();



        HttpTestServer httpsServer = null;
        try {
            SSLContext serverContext = SimpleSSLContext.findSSLContext();
            SSLParameters p = serverContext.getSupportedSSLParameters();
            p.setApplicationProtocols(new String[]{"h3"});
            httpsServer = HttpTestServer.create(HTTP_3_URI_ONLY, serverContext);
            httpsServer.addHandler(new HttpTestEchoHandler(), "/");
            String httpsURIString = "https://" + httpsServer.serverAuthority() + "/bar/";
            System.out.println("HTTP/3 Server started on: " + httpsServer.serverAuthority());

            httpsServer.start();
            URI uri = URI.create(httpsURIString);

            HttpRequest req = HttpRequest.newBuilder(uri)
                    .POST(BodyPublishers.ofString(SIMPLE_STRING))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .build();

            System.out.println("Sending request with good client to " + uri);
            HttpResponse<?> response = goodclient.send(req, BodyHandlers.ofString());
            Asserts.assertEquals(response.statusCode(), 200);
            Asserts.assertEquals(response.version(), HTTP_3);
            Asserts.assertEquals(response.body(), SIMPLE_STRING);
            System.out.println("Expected response successfully received");
            try {
                System.out.println("Sending request with bad client to " + uri);
                response = badclient.send(req, BodyHandlers.discarding());
                throw new RuntimeException("Unexpected response: " + response);
            } catch (IOException e) {
                System.out.println("Caught Expected IOException: " + e);
            }
            System.out.println("DONE");
        } finally {
            if (httpsServer != null )  { httpsServer.stop(); }
            exec.close();
        }
    }
}
