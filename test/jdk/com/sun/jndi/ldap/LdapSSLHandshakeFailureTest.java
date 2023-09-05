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

import jdk.test.lib.net.URIBuilder;

import javax.naming.Context;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.net.SocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Hashtable;

/*
 * @test
 * @bug 8314063
 * @library /test/lib
 * @summary For LDAPs connection, if the value of com.sun.jndi.ldap.connect.timeout is
 * set too small or not an optimal value for the system, after the socket is created and
 * connected to the server, but the handshake between the client and server fails due to
 * socket time out, the opened socket is not closed properly. In this test case, the server
 * is forced to sleep ten seconds and connection time out for client is one second. This
 * will allow the socket opened and connected, and give the chance for the handshake to be
 * timed out. Before this fix, the socket is kept opened. Right now the exception will be
 * caught and the socket will be closed.
 *
 * @run main/othervm LdapSSLHandshakeFailureTest LdapSSLHandshakeFailureTest$CustomSocketFactory true 6000
 * @run main/othervm LdapSSLHandshakeFailureTest -1000 true 6000
 * @run main/othervm LdapSSLHandshakeFailureTest -1000 false 6000
 * @run main/othervm LdapSSLHandshakeFailureTest 2000 false 6000
 * @run main/othervm LdapSSLHandshakeFailureTest 0 true 6000
 * @run main/othervm LdapSSLHandshakeFailureTest 0 false 6000
 * @run main/othervm LdapSSLHandshakeFailureTest true
 * @run main/othervm LdapSSLHandshakeFailureTest false
 */

public class LdapSSLHandshakeFailureTest {
    private static String SOCKET_CLOSED_MSG = "The socket has been closed.";

    private static int serverSleepingTime = 5000;

    public static void main(String args[]) throws Exception {

        // Set the keystores
        setKeyStore();
        boolean serverSlowDown = Boolean.valueOf(args[0]);
        if (args.length == 2) {
            serverSlowDown = Boolean.valueOf(args[1]);
        }

        if (args.length == 3) {
            serverSleepingTime = Integer.valueOf(args[2]);
        }

        boolean hasCustomSocketFactory = args[0]
                .equals("LdapSSLHandshakeFailureTest$CustomSocketFactory");
        // start the test server first.
        try (TestServer server = new TestServer(serverSlowDown, serverSleepingTime)) {
            server.start();
            Hashtable<String, Object> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put("java.naming.ldap.version", "3");
            env.put(Context.PROVIDER_URL, URIBuilder.newBuilder()
                    .scheme("ldaps")
                    .loopback()
                    .port(server.getPortNumber())
                    .buildUnchecked().toString());

            if (hasCustomSocketFactory) {
                env.put("java.naming.ldap.factory.socket", args[0]);
                env.put("com.sun.jndi.ldap.connect.timeout", "1000");
            }

            if (args.length == 2 && !hasCustomSocketFactory) {
                env.put("com.sun.jndi.ldap.connect.timeout", args[0]);
            }

            env.put(Context.SECURITY_PROTOCOL, "ssl");
            env.put(Context.SECURITY_AUTHENTICATION, "Simple");
            env.put(Context.SECURITY_PRINCIPAL, "cn=principal");
            env.put(Context.SECURITY_CREDENTIALS, "123456");
            LdapContext ctx = null;
            try {
                ctx = new InitialLdapContext(env, null);
            } catch (Exception e) {
                if (CustomSocketFactory.customSocket.closeMethodCalledCount() > 0
                        && hasCustomSocketFactory
                        && Boolean.valueOf(args[1])) {
                    System.out.println(SOCKET_CLOSED_MSG);
                } else {
                    throw e;
                }
            } finally {
                if (ctx != null)
                    ctx.close();
            }
        }
    }

    public static class CustomSocketFactory extends SocketFactory {
        private static CustomSocket customSocket;

        public static CustomSocketFactory getDefault() {
            return new CustomSocketFactory();
        }

        @Override
        public Socket createSocket() throws SocketException {
            customSocket = new CustomSocket();
            return customSocket;
        }

        @Override
        public Socket createSocket(String s, int timeout) {
            return customSocket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost,
                                   int localPort) {
            return customSocket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) {
            return customSocket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port,
                                   InetAddress localAddress, int localPort) {
            return customSocket;
        }
    }

    private static class CustomSocket extends Socket {
        private int closeMethodCalled = 0;

        public CustomSocket() {
            closeMethodCalled = 0;
        }

        public int closeMethodCalledCount() {
            return closeMethodCalled;
        }

        @Override
        public void close() throws java.io.IOException {
            closeMethodCalled++;
            super.close();
        }
    }

    private static void setKeyStore() {

        String fileName = "ksWithSAN", dir = System.getProperty("test.src", ".") + File.separator;

        System.setProperty("javax.net.ssl.keyStore", dir + fileName);
        System.setProperty("javax.net.ssl.keyStorePassword", "welcome1");
        System.setProperty("javax.net.ssl.trustStore", dir + fileName);
        System.setProperty("javax.net.ssl.trustStorePassword", "welcome1");
    }

    static class TestServer extends Thread implements AutoCloseable {
        private boolean isForceToSleep;
        private int sleepingTime;
        private final ServerSocket serverSocket;
        private final int PORT;

        private TestServer(boolean isForceToSleep, int sleepingTime) {
            this.isForceToSleep = isForceToSleep;
            this.sleepingTime = sleepingTime;
            try {
                SSLServerSocketFactory socketFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
                serverSocket = socketFactory.createServerSocket(0, 0, InetAddress.getLoopbackAddress());
                PORT = serverSocket.getLocalPort();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            setDaemon(true);
        }

        public int getPortNumber() {
            return PORT;
        }

        @Override
        public void run() {
            try (Socket socket = serverSocket.accept();
                 InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {
                if (isForceToSleep) {
                    Thread.sleep(sleepingTime);
                }
                byte[] bindResponse = {0x30, 0x0C, 0x02, 0x01, 0x01, 0x61, 0x07, 0x0A,
                        0x01, 0x00, 0x04, 0x00, 0x04, 0x00};
                // read the bindRequest
                while (in.read() != -1) {
                    in.skip(in.available());
                    break;
                }
                out.write(bindResponse);
                out.flush();
                // ignore the further requests
                while (in.read() != -1) {
                    in.skip(in.available());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void close() throws Exception {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }
}


