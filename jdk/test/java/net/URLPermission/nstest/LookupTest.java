/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @compile -XDignore.symbol.file=true SimpleNameService.java
 *                                     SimpleNameServiceDescriptor.java
 * @run main/othervm/timeout=200 -Dsun.net.spi.nameservice.provider.1=simple,sun LookupTest
 */

/**
 * This is a simple smoke test of the HttpURLPermission mechanism, which
 * checks for either IOException (due to unknown host) or SecurityException
 * due to lack of permission to connect
 */

import java.net.*;
import java.io.*;

public class LookupTest {

    static void test(
        String url, boolean throwsSecException, boolean throwsIOException)
    {
        try {
            URL u = new URL(url);
            System.err.println ("Connecting to " + u);
            URLConnection urlc = u.openConnection();
            InputStream is = urlc.getInputStream();
        } catch (SecurityException e) {
            if (!throwsSecException) {
                throw new RuntimeException ("(1) was not expecting " + e);
            }
            return;
        } catch (IOException ioe) {
            if (!throwsIOException) {
                throw new RuntimeException ("(2) was not expecting " + ioe);
            }
            return;
        }
        if (throwsSecException || throwsIOException) {
            System.err.printf ("was expecting a %s\n", throwsSecException ?
                "security exception" : "IOException");
            throw new RuntimeException("was expecting an exception");
        }
    }

    public static void main(String args[]) throws Exception {
        SimpleNameService.put("allowedAndFound.com", "127.0.0.1");
        SimpleNameService.put("notAllowedButFound.com", "99.99.99.99");
        // name "notAllowedAndNotFound.com" is not in map
        // name "allowedButNotfound.com" is not in map
        startServer();

        String policyFileName = "file://" + System.getProperty("test.src", ".") + "/policy";
        System.err.println ("policy = " + policyFileName);

        System.setProperty("java.security.policy", policyFileName);

        System.setSecurityManager(new SecurityManager());

        test("http://allowedAndFound.com:50100/foo", false, false);

        test("http://notAllowedButFound.com:50100/foo", true, false);

        test("http://allowedButNotfound.com:50100/foo", false, true);

        test("http://notAllowedAndNotFound.com:50100/foo", true, false);
    }

    static Thread server;
    static ServerSocket serverSocket;

    static class Server extends Thread {
        public void run() {
            byte[] buf = new byte[1000];
            try {
                while (true) {
                    Socket s = serverSocket.accept();
                    InputStream i = s.getInputStream();
                    i.read(buf);
                    OutputStream o = s.getOutputStream();
                    String rsp = "HTTP/1.1 200 Ok\r\n" +
                        "Connection: close\r\nContent-length: 0\r\n\r\n";
                    o.write(rsp.getBytes());
                    o.close();
                }
            } catch (IOException e) {
                return;
            }
            }
    }

    static void startServer() {
        try {
            serverSocket = new ServerSocket(50100);
            server = new Server();
            server.start();
        } catch (Exception e) {
            throw new RuntimeException ("Test failed to initialize");
        }
    }
}
