/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ConcurrentModificationException;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.event.EventContext;
import javax.naming.event.NamingEvent;
import javax.naming.event.NamingExceptionEvent;
import javax.naming.event.NamingListener;
import javax.naming.event.ObjectChangeListener;

/**
 * @test
 * @bug 8176192
 * @summary Incorrect usage of Iterator in Java 8 In com.sun.jndi.ldap.
 * EventSupport.removeNamingListener
 * @modules java.naming
 * @run main RemoveNamingListenerTest
 */
public class RemoveNamingListenerTest {

    private static volatile Exception exception;

    public static void main(String args[]) throws Exception {
        // start the LDAP server
        TestLDAPServer server = new TestLDAPServer();
        server.start();

        // Set up environment for creating initial context
        Hashtable<String, Object> env = new Hashtable<>(3);
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://localhost:" + server.getPort() + "/o=example");
        env.put("com.sun.jndi.ldap.connect.timeout", "2000");
        EventContext ctx = null;

        try {
            ctx = (EventContext) (new InitialContext(env).lookup(""));
            String target = "cn=Vyom Tewari";

            // Create listeners
            NamingListener oneListener = new SampleListener();
            NamingListener objListener = new SampleListener();
            NamingListener subListener = new SampleListener();

            // Register listeners using different scopes
            ctx.addNamingListener(target, EventContext.ONELEVEL_SCOPE, oneListener);
            ctx.addNamingListener(target, EventContext.OBJECT_SCOPE, objListener);
            ctx.addNamingListener(target, EventContext.SUBTREE_SCOPE, subListener);

            //remove a listener in different thread
            Thread t = new Thread(new RemoveNamingListener(ctx, subListener));
            t.start();
            t.join();

            if (exception != null) {
                throw exception;
            }
            System.out.println("Test run OK!!!");
        } finally {
            if (ctx != null) {
                ctx.close();
            }
            server.stopServer();
        }
    }

    /**
     * Helper thread that removes the naming listener.
     */
    static class RemoveNamingListener implements Runnable {

        final EventContext ctx;
        final NamingListener listener;

        RemoveNamingListener(EventContext ctx, NamingListener listener) {
            this.ctx = ctx;
            this.listener = listener;
        }

        @Override
        public void run() {
            try {
                ctx.removeNamingListener(listener);
            } catch (NamingException | ConcurrentModificationException ex) {
                exception = ex;
            }
        }
    }

    static class SampleListener implements ObjectChangeListener {

        @Override
        public void objectChanged(NamingEvent ne) {
            //do nothing
        }

        @Override
        public void namingExceptionThrown(NamingExceptionEvent nee) {
            //do nothing
        }
    }
}

class TestLDAPServer extends Thread {

    private final int LDAP_PORT;
    private final ServerSocket serverSocket;
    private volatile boolean isRunning;

    TestLDAPServer() throws IOException {
        serverSocket = new ServerSocket(0);
        isRunning = true;
        LDAP_PORT = serverSocket.getLocalPort();
        setDaemon(true);
    }

    public int getPort() {
        return LDAP_PORT;
    }

    public void stopServer() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                // this will cause ServerSocket.accept() to throw SocketException.
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void run() {
        try {
            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                Thread handler = new Thread(new LDAPServerHandler(clientSocket));
                handler.setDaemon(true);
                handler.start();
            }
        } catch (IOException iOException) {
            //do not throw exception if server is not running.
            if (isRunning) {
                throw new RuntimeException(iOException);
            }
        } finally {
            stopServer();
        }
    }
}

class LDAPServerHandler implements Runnable {

    private final Socket clientSocket;

    public LDAPServerHandler(final Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        BufferedInputStream in = null;
        PrintWriter out = null;
        byte[] bindResponse = {0x30, 0x0C, 0x02, 0x01, 0x01, 0x61, 0x07, 0x0A, 0x01, 0x00, 0x04, 0x00, 0x04, 0x00};
        byte[] searchResponse = {0x30, 0x0C, 0x02, 0x01, 0x02, 0x65, 0x07, 0x0A, 0x01, 0x00, 0x04, 0x00, 0x04, 0x00};
        try {
            in = new BufferedInputStream(clientSocket.getInputStream());
            out = new PrintWriter(new OutputStreamWriter(
                    clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
            while (true) {

                // Read the LDAP BindRequest
                while (in.read() != -1) {
                    in.skip(in.available());
                    break;
                }

                // Write an LDAP BindResponse
                out.write(new String(bindResponse));
                out.flush();

                // Read the LDAP SearchRequest
                while (in.read() != -1) {
                    in.skip(in.available());
                    break;
                }

                // Write an LDAP SearchResponse
                out.write(new String(searchResponse));
                out.flush();
            }
        } catch (IOException iOException) {
            throw new RuntimeException(iOException);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            if (out != null) {
                out.close();
            }
            if (clientSocket != null) {
                try {
                    clientSocket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
