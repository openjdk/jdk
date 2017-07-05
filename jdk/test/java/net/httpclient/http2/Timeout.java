/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import jdk.incubator.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletionException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import static jdk.incubator.http.HttpRequest.BodyProcessor.fromString;
import static jdk.incubator.http.HttpResponse.BodyHandler.asString;

/*
 * @test
 * @bug 8156710
 * @summary Check if HttpTimeoutException is thrown if a server doesn't reply
 * @run main/othervm Timeout
 */
public class Timeout {

    private static final int RANDOM_PORT = 0;
    private static final int TIMEOUT = 3 * 1000; // in millis
    private static final String KEYSTORE = System.getProperty("test.src")
            + File.separator + "keystore.p12";
    private static final String PASSWORD = "password";

    // indicates if server is ready to accept connections
    private static volatile boolean ready = false;

    public static void main(String[] args) throws Exception {
        test(false);
        test(true);
    }

    public static void test(boolean async) throws Exception {
        System.setProperty("javax.net.ssl.keyStore", KEYSTORE);
        System.setProperty("javax.net.ssl.keyStorePassword", PASSWORD);
        System.setProperty("javax.net.ssl.trustStore", KEYSTORE);
        System.setProperty("javax.net.ssl.trustStorePassword", PASSWORD);

        SSLServerSocketFactory factory =
                (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();

        try (SSLServerSocket ssocket =
                (SSLServerSocket) factory.createServerSocket(RANDOM_PORT)) {

            // start server
            Thread server = new Thread(() -> {
                while (true) {
                    System.out.println("server: ready");
                    SSLParameters params = ssocket.getSSLParameters();
                    params.setApplicationProtocols(new String[]{"h2"});
                    ssocket.setSSLParameters(params);
                    ready = true;
                    try (SSLSocket socket = (SSLSocket) ssocket.accept()) {

                        // just read forever
                        System.out.println("server: accepted");
                        while (true) {
                            socket.getInputStream().read();
                        }
                    } catch (IOException e) {
                        // ignore exceptions on server side
                        System.out.println("server: exception: " + e);
                    }
                }
            });
            server.setDaemon(true);
            server.start();

            // wait for server is ready
            do {
                Thread.sleep(1000);
            } while (!ready);

            String uri = "https://localhost:" + ssocket.getLocalPort();
            if (async) {
                System.out.println(uri + ": Trying to connect asynchronously");
                connectAsync(uri);
            } else {
                System.out.println(uri + ": Trying to connect synchronously");
                connect(uri);
            }
        }
    }

    private static void connect(String server) throws Exception {
        try {
            HttpClient client = HttpClient.newBuilder()
                                          .version(HttpClient.Version.HTTP_2)
                                          .build();
            HttpRequest request = HttpRequest.newBuilder(new URI(server))
                                             .timeout(Duration.ofMillis(TIMEOUT))
                                             .POST(fromString("body"))
                                             .build();
            HttpResponse<String> response = client.send(request, asString());
            System.out.println("Received unexpected reply: " + response.statusCode());
            throw new RuntimeException("unexpected successful connection");
        } catch (HttpTimeoutException e) {
            System.out.println("expected exception: " + e);
        }
    }

    private static void connectAsync(String server) throws Exception {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(new URI(server))
                    .timeout(Duration.ofMillis(TIMEOUT))
                    .POST(fromString("body"))
                    .build();
            HttpResponse<String> response = client.sendAsync(request, asString()).join();
            System.out.println("Received unexpected reply: " + response.statusCode());
            throw new RuntimeException("unexpected successful connection");
        } catch (CompletionException e) {
            if (e.getCause() instanceof HttpTimeoutException) {
                System.out.println("expected exception: " + e.getCause());
            } else {
                throw new RuntimeException("Unexpected exception received: " + e.getCause(), e);
            }
        }
    }

}
