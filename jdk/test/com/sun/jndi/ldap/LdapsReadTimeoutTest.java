/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7094377
 * @summary Com.sun.jndi.ldap.read.timeout doesn't work with ldaps.
 */

import java.net.Socket;
import java.net.ServerSocket;
import java.io.*;
import javax.naming.*;
import javax.naming.directory.*;
import java.util.Hashtable;

public class LdapsReadTimeoutTest {

    public static void main(String[] args) throws Exception {
        boolean passed = false;

        // create the server
        try (Server server = Server.create()) {
            // Set up the environment for creating the initial context
            Hashtable<String,Object> env = new Hashtable<>(11);
            env.put(Context.INITIAL_CONTEXT_FACTORY,
                "com.sun.jndi.ldap.LdapCtxFactory");
            env.put("com.sun.jndi.ldap.connect.timeout", "1000");
            env.put("com.sun.jndi.ldap.read.timeout", "1000");
            env.put(Context.PROVIDER_URL, "ldaps://localhost:" + server.port());


            // Create initial context
            DirContext ctx = new InitialDirContext(env);
            try {
                System.out.println("LDAP Client: Connected to the Server");

                SearchControls scl = new SearchControls();
                scl.setSearchScope(SearchControls.SUBTREE_SCOPE);
                System.out.println("Performing Search");
                NamingEnumeration<SearchResult> answer =
                    ctx.search("ou=People,o=JNDITutorial", "(objectClass=*)", scl);
            } finally {
                // Close the context when we're done
                ctx.close();
            }
        } catch (NamingException e) {
            passed = true;
            e.printStackTrace();
        }

        if (!passed) {
            throw new Exception("Read timeout test failed," +
                         " read timeout exception not thrown");
        }
        System.out.println("The test PASSED");
    }

    static class Server implements Runnable, Closeable {
        private final ServerSocket ss;
        private Socket sref;

        private Server(ServerSocket ss) {
            this.ss = ss;
        }

        static Server create() throws IOException {
            Server server = new Server(new ServerSocket(0));
            new Thread(server).start();
            return server;
        }

        int port() {
            return ss.getLocalPort();
        }

        public void run() {
            try (Socket s = ss.accept()) {
                sref = s;
                System.out.println("Server: Connection accepted");
                BufferedInputStream bis =
                    new BufferedInputStream(s.getInputStream());
                byte[] buf = new byte[100];
                int n;
                do {
                    n = bis.read(buf);
                } while (n > 0);
            } catch (IOException e) {
                // ignore
            }
        }

        public void close() throws IOException {
            ss.close();
            sref.close();
        }
    }
}
