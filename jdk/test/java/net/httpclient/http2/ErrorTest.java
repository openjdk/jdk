/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @key intermittent
 * @library /lib/testlibrary
 * @build jdk.testlibrary.SimpleSSLContext
 * @modules java.httpclient
 * @compile/module=java.httpclient java/net/http/BodyOutputStream.java
 * @compile/module=java.httpclient java/net/http/BodyInputStream.java
 * @compile/module=java.httpclient java/net/http/EchoHandler.java
 * @compile/module=java.httpclient java/net/http/Http2Handler.java
 * @compile/module=java.httpclient java/net/http/Http2TestExchange.java
 * @compile/module=java.httpclient java/net/http/Http2TestServerConnection.java
 * @compile/module=java.httpclient java/net/http/Http2TestServer.java
 * @compile/module=java.httpclient java/net/http/OutgoingPushPromise.java
 * @compile/module=java.httpclient java/net/http/TestUtil.java
 * @run testng/othervm -Djava.net.http.HttpClient.log=ssl,errors ErrorTest
 * @summary check exception thrown when bad TLS parameters selected
 */

import java.io.*;
import java.net.*;
import java.net.http.*;
import static java.net.http.HttpClient.Version.HTTP_2;
import javax.net.ssl.*;
import java.nio.file.*;
import java.util.concurrent.*;
import jdk.testlibrary.SimpleSSLContext;


import org.testng.annotations.Test;
import org.testng.annotations.Parameters;

/**
 * When selecting an unacceptable cipher suite the TLS handshake will fail.
 * But, the exception that was thrown was not being returned up to application
 * causing hang problems
 */
@Test
public class ErrorTest {
    static int httpsPort;
    static Http2TestServer httpsServer;
    static HttpClient client = null;
    static ExecutorService exec;
    static SSLContext sslContext;

    static String httpsURIString;

    static HttpClient getClient() {
        if (client == null) {
            client = HttpClient.create()
                .sslContext(sslContext)
                .sslParameters(new SSLParameters(
                    new String[]{"TLS_KRB5_WITH_3DES_EDE_CBC_SHA"}))
                .version(HTTP_2)
                .build();
        }
        return client;
    }

    static URI getURI() {
        return URI.create(httpsURIString);
    }

    static final String SIMPLE_STRING = "Hello world Goodbye world";

    @Test(timeOut=5000)
    static void test() throws Exception {
        try {
            SimpleSSLContext sslct = new SimpleSSLContext();
            sslContext = sslct.get();
            client = getClient();
            exec = client.executorService();

            httpsServer = new Http2TestServer(true, 0, new EchoHandler(),
                    exec, sslContext);

            httpsPort = httpsServer.getAddress().getPort();
            httpsURIString = "https://127.0.0.1:" +
                Integer.toString(httpsPort) + "/bar/";

            httpsServer.start();
            URI uri = getURI();
            System.err.println("Request to " + uri);

            HttpClient client = getClient();
            HttpRequest req = client.request(uri)
                    .body(HttpRequest.fromString(SIMPLE_STRING))
                    .POST();
            HttpResponse response = null;
            try {
                response = req.response();
                throw new RuntimeException("Expected exception");
            } catch (IOException e) {
                System.err.println("Expected IOException received " + e);
            }
            System.err.println("DONE");
        } finally {
            httpsServer.stop();
            exec.shutdownNow();
        }
    }
}
