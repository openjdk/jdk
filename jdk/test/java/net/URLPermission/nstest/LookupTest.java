/*
 * Copyright (c) 2013, 2016 Oracle and/or its affiliates. All rights reserved.
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
 * This is a simple smoke test of the HttpURLPermission mechanism, which
 * checks for either IOException (due to unknown host) or SecurityException
 * due to lack of permission to connect
 */

import java.net.*;
import java.io.*;
import jdk.testlibrary.Utils;

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
                throw new RuntimeException ("(1) was not expecting ", e);
            }
            return;
        } catch (IOException ioe) {
            if (!throwsIOException) {
                throw new RuntimeException ("(2) was not expecting ", ioe);
            }
            return;
        }
        if (throwsSecException || throwsIOException) {
            System.err.printf ("was expecting a %s\n", throwsSecException ?
                "security exception" : "IOException");
            throw new RuntimeException("was expecting an exception");
        }
    }

    static int port;
    static ServerSocket serverSocket;

    public static void main(String args[]) throws Exception {


        String cmd = args[0];
        if (cmd.equals("-getport")) {
            port = Utils.getFreePort();
            System.out.print(port);
        } else if (cmd.equals("-runtest")) {
            port = Integer.parseInt(args[1]);
            String hostsFileName = System.getProperty("test.src", ".") + "/LookupTestHosts";
            System.setProperty("jdk.net.hosts.file", hostsFileName);
            addMappingToHostsFile("allowedAndFound.com", "127.0.0.1", hostsFileName, false);
            addMappingToHostsFile("notAllowedButFound.com", "99.99.99.99", hostsFileName, true);
            // name "notAllowedAndNotFound.com" is not in map
            // name "allowedButNotfound.com" is not in map
            try {
                startServer();

                System.setSecurityManager(new SecurityManager());

                test("http://allowedAndFound.com:" + port + "/foo", false, false);

                test("http://notAllowedButFound.com:" + port + "/foo", true, false);

                test("http://allowedButNotfound.com:" + port + "/foo", false, true);

                test("http://notAllowedAndNotFound.com:" + port + "/foo", true, false);
            } finally {
                serverSocket.close();
            }
        } else {
            throw new RuntimeException("Bad invocation: " + cmd);
        }
    }

    static Thread server;

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
            serverSocket = new ServerSocket(port);
            server = new Server();
            server.start();
        } catch (Exception e) {
            throw new RuntimeException ("Test failed to initialize", e);
        }
    }

    private static void addMappingToHostsFile (String host,
                                               String addr,
                                               String hostsFileName,
                                               boolean append)
                                             throws Exception {
        String mapping = addr + " " + host;
        try (PrintWriter hfPWriter = new PrintWriter(new BufferedWriter(
                new FileWriter(hostsFileName, append)))) {
            hfPWriter.println(mapping);
}
    }

}
