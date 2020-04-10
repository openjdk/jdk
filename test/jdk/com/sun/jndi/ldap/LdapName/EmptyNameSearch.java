/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6997561
 * @summary A request for better error handling in JNDI
 */

import java.net.Socket;
import java.net.ServerSocket;
import java.io.*;
import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.*;
import java.util.Collections;
import java.util.Hashtable;

public class EmptyNameSearch {

    public static void main(String[] args) throws Exception {

        // Start the LDAP server
        Server s = new Server();
        s.start();
        Thread.sleep(3000);

        // Setup JNDI parameters
        Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://localhost:" + s.getPortNumber());

        try {

            // Create initial context
            System.out.println("Client: connecting...");
            DirContext ctx = new InitialDirContext(env);

            System.out.println("Client: performing search...");
            ctx.search(new LdapName(Collections.emptyList()), "cn=*", null);
            ctx.close();

            // Exit
            throw new RuntimeException();

        } catch (NamingException e) {
            System.err.println("Passed: caught the expected Exception - " + e);

        } catch (Exception e) {
            System.err.println("Failed: caught an unexpected Exception - " + e);
            throw e;
        }
    }

    static class Server extends Thread {

        private int serverPort = 0;
        private byte[] bindResponse = {
            0x30, 0x0C, 0x02, 0x01, 0x01, 0x61, 0x07, 0x0A,
            0x01, 0x00, 0x04, 0x00, 0x04, 0x00
        };
        private byte[] searchResponse = {
            0x30, 0x0C, 0x02, 0x01, 0x02, 0x65, 0x07, 0x0A,
            0x01, 0x02, 0x04, 0x00, 0x04, 0x00
        };

        Server() {
        }

        public int getPortNumber() {
            return serverPort;
        }

        public void run() {
            try {
                ServerSocket serverSock = new ServerSocket(0);
                serverPort = serverSock.getLocalPort();
                System.out.println("Server: listening on port " + serverPort);

                Socket socket = serverSock.accept();
                System.out.println("Server: connection accepted");

                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                // Read the LDAP BindRequest
                System.out.println("Server: reading request...");
                while (in.read() != -1) {
                    in.skip(in.available());
                    break;
                }

                // Write an LDAP BindResponse
                System.out.println("Server: writing response...");
                out.write(bindResponse);
                out.flush();

                // Read the LDAP SearchRequest
                System.out.println("Server: reading request...");
                while (in.read() != -1) {
                    in.skip(in.available());
                    break;
                }

                // Write an LDAP SearchResponse
                System.out.println("Server: writing response...");
                out.write(searchResponse);
                out.flush();

                in.close();
                out.close();
                socket.close();
                serverSock.close();

            } catch (IOException e) {
                // ignore
            }
        }
    }
}
