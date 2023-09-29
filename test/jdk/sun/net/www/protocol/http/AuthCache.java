/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304818
 * @modules java.base/sun.net.www.protocol.http
 * @library /test/lib
 * @build jdk.test.lib.util.ForceGC
 * @run main/othervm AuthCache
 */

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.lang.ref.PhantomReference;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import jdk.test.lib.util.ForceGC;

public class AuthCache {
    static class ClientAuth extends Authenticator {
        private final String realm;
        private final String username;
        private final String password;
        private AtomicBoolean wasCalled = new AtomicBoolean();

        private String errorMsg;

        ClientAuth(String realm, String username, String password) {
            this.realm = realm;
            this.username = username;
            this.password = password;
        }

        /**
         * returns true if getPasswordAuthentication() was called
         * since the last time this method was called. The wasCalled
         * flag is cleared after each call.
         * If an error occurred, a RuntimeException is thrown
         * @return
         */
        public synchronized boolean wasCalled() {
            if (errorMsg != null)
                throw new RuntimeException(errorMsg);

            return wasCalled.getAndSet(false);
        }
        protected synchronized PasswordAuthentication getPasswordAuthentication() {
            if (!getRequestingPrompt().equals(realm)) {
                errorMsg = String.format("Error: %s expected as realm, received %s", realm, getRequestingPrompt());
            }
            wasCalled.set(true);
            return new PasswordAuthentication(username, password.toCharArray());
        }
    }

    static final HttpHandler handler = (HttpExchange exch) -> {
        exch.sendResponseHeaders(200, -1);
        exch.close();
    };

    static class ServerAuth extends BasicAuthenticator {
        private final String user, pass;

        ServerAuth(String realm, String user, String pass) {
            super(realm);
            this.user = user;
            this.pass = pass;
        }

        @Override
        public boolean checkCredentials(String username, String password) {
            return username.equals(user) && password.equals(pass);
        }
    }

    /**
     * Creates two Authenticators and two realms ("r1" and "r2")
     * "r1" uses context "/path1" credentials = user1/pass1
     * "r2" uses context "/path2" credentials = user2/pass2
     *
     * 1) Send request to "r1" and "r2" expect both authenticators to be called
     *    cache size should be 4
     *
     * 2) Send request to "r1" and "r2". Authenticators should not be called (cache)
     *
     * 3) Clear reference to "r1" and call gc.
     *    cache size should be 2
     *
     * 4) Send request to "r1" and "r2". "r1" auth should be called, but not "r2"
     *    cache size should be 4
     */
    public static void main(String[] args) throws IOException {
        var clauth1 = new ClientAuth("r1", "user1", "pass1");
        PhantomReference<Authenticator> ref = new PhantomReference<>(clauth1, null);
        var clauth2 = new ClientAuth("r2", "user2", "pass2");
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        var ctx1 = server.createContext("/path1", handler);
        ctx1.setAuthenticator(new ServerAuth("r1", "user1", "pass1"));

        var ctx2 = server.createContext("/path2", handler);
        ctx2.setAuthenticator(new ServerAuth("r2", "user2", "pass2"));
        var addr = server.getAddress();
        var url1 = URI.create("http://" + addr.getHostName() + ":" + addr.getPort() + "/path1/").toURL();
        var url2 = URI.create("http://" + addr.getHostName() + ":" + addr.getPort() + "/path2/").toURL();
        server.start();

        sendRequest(url1, url2, clauth1, clauth2, true, true);
        sendRequest(url1, url2, clauth1, clauth2, false, false);
        clauth1 = null;
        ForceGC.wait(() -> ref.refersTo(null));
        delay(1);
        clauth1 = new ClientAuth("r1", "user1", "pass1");
        sendRequest(url1, url2, clauth1, clauth2, true, false);
        System.out.println("Passed");
        server.stop(0);
    }

    static void delay(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
        }
    }

    static void sendRequest(URL u1, URL u2, ClientAuth a1, ClientAuth a2, boolean auth1Called, boolean auth2Called) throws IOException {
        var urlc1 = (HttpURLConnection)u1.openConnection();
        urlc1.setAuthenticator(a1);
        var urlc2 = (HttpURLConnection)u2.openConnection();
        urlc2.setAuthenticator(a2);

        var is1 = urlc1.getInputStream();
        is1.readAllBytes();
        is1.close();
        var is2 = urlc2.getInputStream();
        is2.readAllBytes();
        is2.close();
        urlc1 = urlc2 = null;

        boolean a1Called = a1.wasCalled();
        boolean a2Called = a2.wasCalled();
        if (a1Called && !auth1Called)
            throw new RuntimeException("a1Called && !auth1Called");
        if (!a1Called && auth1Called)
            throw new RuntimeException("!a1Called && auth1Called");
        if (a2Called && !auth2Called)
            throw new RuntimeException("a2Called && !auth2Called");
        if (!a2Called && auth2Called)
            throw new RuntimeException("!a2Called && auth2Called");
    }
}
