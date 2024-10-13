/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8144100
 * @summary checking token sent by client should be done in case-insensitive manner
 * @run main BasicAuthToken
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Base64;
import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpServer;

public class BasicAuthToken {
    private static final String CRLF = "\r\n";
    private static final String someContext = "/test";

    public static void main(String[] args) throws Exception {
        HttpServer server = server();
        try {
            client(server.getAddress().getPort());
        } finally {
            server.stop(0);
        }
    }

    static HttpServer server() throws Exception {
        String realm = "someRealm";
        ServerAuthenticator authenticator = new ServerAuthenticator(realm);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(someContext, exchange -> {
            if (authenticator.authenticate(exchange) instanceof Authenticator.Failure) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        }).setAuthenticator(authenticator);
        server.start();
        return server;
    }

    static void client(int port) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String credentials = "username:password";
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
            writer.write("GET " + someContext + " HTTP/1.1" + CRLF);
            writer.write("Host: localhost:" + port + CRLF);
            writer.write("User-Agent: Java/" + System.getProperty("java.version") + CRLF);
            writer.write("Authorization: BAsIc " + encodedCredentials + CRLF);
            writer.write(CRLF);
            writer.flush();

            System.err.println("Server response");
            String statusLine = reader.readLine();
            System.err.println(statusLine);

            if (!statusLine.startsWith("HTTP/1.1 200")) {
                throw new RuntimeException("unexpected status line: " + statusLine);
            }
            if (!ServerAuthenticator.wasChecked()) {
                throw new RuntimeException("Authenticator wasn't invoked");
            }
        }
    }


    static class ServerAuthenticator extends BasicAuthenticator {
        private static volatile boolean invoked = false;

        ServerAuthenticator(String realm) {
            super(realm);
        }

        public static boolean wasChecked() {
            return invoked;
        }

        @Override
        public boolean checkCredentials(String username, String password) {
            String validUsername = "username", validPassword = "password";
            invoked = true;
            return username.equals(validUsername) && password.equals(validPassword);
        }
    }
}
