/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary A simple smoke test which checks URLPermission implies,
 *          and verify that HttpURLConnection either succeeds or throws
 *          IOException (due to unknown host).
 * @run main/othervm -Djdk.net.hosts.file=LookupTestHosts LookupTest
 */

import java.io.BufferedWriter;
import java.io.FilePermission;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetPermission;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketPermission;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLPermission;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import static java.nio.charset.StandardCharsets.US_ASCII;

public class LookupTest {

    static LookupTestPermisions permissions;
    static volatile ServerSocket serverSocket;

    static void test(String url,
                     boolean throwsSecException,
                     boolean throwsIOException) {
        ProxySelector.setDefault(null);
        URL u;
        InputStream is = null;
        try {
            u = new URL(url);
            System.err.println("Connecting to " + u);
            URLPermission permission = new URLPermission(url, "GET");
            if (!permissions.implies(permission)) throw new SecurityException(permission.toString());
            URLConnection urlc = u.openConnection();
            is = urlc.getInputStream();
            System.err.println("Connection sucessful");
        } catch (SecurityException e) {
            if (!throwsSecException) {
                throw new RuntimeException("Unexpected SecurityException:", e);
            }
            return;
        } catch (IOException e) {
            if (!throwsIOException) {
                System.err.println("Unexpected IOException:" + e.getMessage());
                throw new RuntimeException(e);
            } else {
                System.err.println("Got expected exception: " + e);
            }
            return;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    System.err.println("Unexpected IOException:" + e.getMessage());
                    throw new RuntimeException(e);
                }
            }
        }

        if (throwsIOException) {
            System.err.printf("was expecting a %s\n", "IOException");
            throw new RuntimeException("was expecting an exception");
        }
    }

    static final String HOSTS_FILE_NAME = System.getProperty("jdk.net.hosts.file");

    public static void main(String args[]) throws Exception {
        addMappingToHostsFile("allowedAndFound.com",
                              InetAddress.getLoopbackAddress().getHostAddress(),
                              HOSTS_FILE_NAME,
                              false);
        addMappingToHostsFile("notAllowedButFound.com",
                              "99.99.99.99",
                              HOSTS_FILE_NAME,
                              true);
        // name "notAllowedAndNotFound.com" is not in map
        // name "allowedButNotfound.com" is not in map
        Server server = new Server();
        int port = server.getPort();
        permissions = new LookupTestPermisions(port);
        try {
            server.start();
            test("http://allowedAndFound.com:"       + port + "/foo", false, false);
            test("http://notAllowedButFound.com:"    + port + "/foo", true, false);
            test("http://allowedButNotfound.com:"    + port + "/foo", false, true);
            test("http://notAllowedAndNotFound.com:" + port + "/foo", true, false);
        } finally {
            server.terminate();
        }
    }

    static class Server extends Thread {
        private volatile boolean done;
        private final int port;

        public Server() throws IOException {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(loopback, 0));
            port = serverSocket.getLocalPort();
        }

        int getPort() {
            return port;
        }

        public void run() {
            try {
                while (!done) {
                    try (Socket s = serverSocket.accept()) {
                        readOneRequest(s.getInputStream());
                        OutputStream o = s.getOutputStream();
                        String rsp = "HTTP/1.1 200 Ok\r\n" +
                                     "Connection: close\r\n" +
                                     "Content-length: 0\r\n\r\n";
                        o.write(rsp.getBytes(US_ASCII));
                    }
                }
            } catch (IOException e) {
                if (!done)
                    e.printStackTrace();
            }
        }

        void terminate() {
            done = true;
            try { serverSocket.close(); }
            catch (IOException unexpected) { unexpected.printStackTrace(); }
        }

        static final byte[] requestEnd = new byte[] {'\r', '\n', '\r', '\n' };

        // Read until the end of a HTTP request
        void readOneRequest(InputStream is) throws IOException {
            int requestEndCount = 0, r;
            while ((r = is.read()) != -1) {
                if (r == requestEnd[requestEndCount]) {
                    requestEndCount++;
                    if (requestEndCount == 4) {
                        break;
                    }
                } else {
                    requestEndCount = 0;
                }
            }
        }
    }

    private static void addMappingToHostsFile(String host,
                                              String addr,
                                              String hostsFileName,
                                              boolean append)
        throws IOException
    {
        String mapping = addr + " " + host;
        try (FileWriter fr = new FileWriter(hostsFileName, append);
             PrintWriter hfPWriter = new PrintWriter(new BufferedWriter(fr))) {
            hfPWriter.println(mapping);
        }
    }

    static class LookupTestPermisions  {
        final PermissionCollection perms = new Permissions();

        LookupTestPermisions(int port) {
            perms.add(new SocketPermission("localhost:1024-", "resolve,accept"));
            perms.add(new URLPermission("http://allowedAndFound.com:" + port + "/-", "*:*"));
            perms.add(new URLPermission("http://allowedButNotfound.com:" + port + "/-", "*:*"));
            perms.add(new FilePermission("<<ALL FILES>>", "read,write,delete"));
        }


        public boolean implies(Permission perm) {
            return perms.implies(perm);
        }
    }
}
