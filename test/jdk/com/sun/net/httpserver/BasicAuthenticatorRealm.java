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

/*
 * @test
 * @bug 8275534
 * @summary  Check that ISO-8859-1 encoded realm strings are transported correctly
 *           with HttpURLConnection and HttpClient
 * @modules jdk.httpserver
 * @library /test/lib
 * @run testng/othervm BasicAuthenticatorRealm
 */

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandlers;
import com.sun.net.httpserver.HttpServer;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

import jdk.test.lib.net.URIBuilder;
import org.testng.annotations.Test;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public class BasicAuthenticatorRealm {

    static final String REALM = "U\u00ffU@realm";  // non-ASCII char
    static final String EXPECTED_AUTH_HEADER_VALUE = "Basic realm=\"U\u00ffU@realm\", charset=\"UTF-8\"";

    static final InetAddress LOOPBACK_ADDR = InetAddress.getLoopbackAddress();

    @Test
    public static void testURLConnection() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(LOOPBACK_ADDR, 0), 0);
        var handler = HttpHandlers.of(200, Headers.of(), "");
        var context = server.createContext("/test", handler);
        var auth = new ServerAuthenticator(REALM);

        context.setAuthenticator(auth);

        try {
            server.start();
            var url = uri(server).toURL();
            var connection = (HttpURLConnection)url.openConnection(Proxy.NO_PROXY);
            assertEquals(connection.getResponseCode(), 401);
            assertEquals(connection.getHeaderField("WWW-Authenticate"), EXPECTED_AUTH_HEADER_VALUE);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public static void testURLConnectionAuthenticated() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(LOOPBACK_ADDR, 0), 0);
        var handler = HttpHandlers.of(200, Headers.of(), "foo");
        var context = server.createContext("/test", handler);
        var auth = new ServerAuthenticator(REALM);

        context.setAuthenticator(auth);
        Authenticator.setDefault(new ClientAuthenticator());

        try {
            server.start();
            var url = uri(server).toURL();
            var connection = (HttpURLConnection)url.openConnection(Proxy.NO_PROXY);
            assertEquals(connection.getResponseCode(), 200);
            assertEquals(connection.getInputStream().readAllBytes(), "foo".getBytes(UTF_8));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public static void testHttpClient() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(LOOPBACK_ADDR, 0), 0);
        var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
        var request = HttpRequest.newBuilder(uri(server)).build();
        var handler = HttpHandlers.of(200, Headers.of(), "foo");
        var context = server.createContext("/test", handler);
        var authenticator = new ServerAuthenticator(REALM);

        context.setAuthenticator(authenticator);

        try {
            server.start();
            var response = client.send(request, BodyHandlers.ofString(UTF_8));
            assertEquals(response.statusCode(), 401);
            assertEquals(response.headers().firstValue("WWW-Authenticate").orElseThrow(), EXPECTED_AUTH_HEADER_VALUE);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public static void testHttpClientAuthenticated() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(LOOPBACK_ADDR, 0), 0);
        var request = HttpRequest.newBuilder(uri(server)).build();
        var handler = HttpHandlers.of(200, Headers.of(), "foo");
        var context = server.createContext("/test", handler);
        var auth = new ServerAuthenticator(REALM);
        var client = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .authenticator(new ClientAuthenticator())
                .build();

        context.setAuthenticator(auth);

        try {
            server.start();
            var response = client.send(request, BodyHandlers.ofString(UTF_8));
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "foo");
        } finally {
            server.stop(0);
        }
    }

    static class ServerAuthenticator extends BasicAuthenticator {
        ServerAuthenticator(String realm) {
            super(realm);
        }

        @Override
        public boolean checkCredentials(String username, String password) {
            if (!getRealm().equals(realm)) {
                return false;
            }
            return true;
        }
    }

    static class ClientAuthenticator extends java.net.Authenticator {
        @Override
        public PasswordAuthentication getPasswordAuthentication() {
            if (!getRequestingPrompt().equals(REALM)) {
                throw new RuntimeException("realm does not match");
            }
            return new PasswordAuthentication("username", "password".toCharArray());
        }
    }

    public static URI uri(HttpServer server) {
        return URIBuilder.newBuilder()
                .scheme("http")
                .host(server.getAddress().getAddress())
                .port(server.getAddress().getPort())
                .path("/test/")
                .buildUnchecked();
    }
}
