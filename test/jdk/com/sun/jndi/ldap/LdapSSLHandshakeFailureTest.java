/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.net.SocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

import jdk.test.lib.net.URIBuilder;

/*
 * @test
 * @bug 8314063 8325579
 * @library /test/lib
 * @summary Several scenarios for LDAP connection handshaking are tested here.
 * We test different combinations of com.sun.jndi.ldap.connect.timeout values
 * and server behavior, e.g. a server that replies immediately vs a server that
 * delays the initial answer. We also try to check whether the underlying Socket
 * object will be closed correctly.
 * We expect exceptions when using a custom SocketFactory that does not supply
 * SSL Sockets. In that case we instrument the supplied Socket object and check
 * if it was properly closed after the handshake failure.
 * When the value of com.sun.jndi.ldap.connect.timeout is set lower than the
 * server delay, we also expect an exception.
 * In all other cases a valid Context object shall be returned and we check
 * whether the socket is closed after closing the Context.
 *
 * @modules java.naming/javax.naming:+open java.naming/com.sun.jndi.ldap:+open
 * @run main/othervm LdapSSLHandshakeFailureTest
 * @run main/othervm LdapSSLHandshakeFailureTest true
 * @run main/othervm LdapSSLHandshakeFailureTest 0
 * @run main/othervm LdapSSLHandshakeFailureTest 0 true
 * @run main/othervm LdapSSLHandshakeFailureTest 2000
 * @run main/othervm LdapSSLHandshakeFailureTest 2000 true
 * @run main/othervm LdapSSLHandshakeFailureTest -1000
 * @run main/othervm LdapSSLHandshakeFailureTest LdapSSLHandshakeFailureTest$CustomSocketFactoryNoUnconnected
 * @run main/othervm LdapSSLHandshakeFailureTest LdapSSLHandshakeFailureTest$CustomSocketFactoryNoUnconnected 1000
 * @run main/othervm LdapSSLHandshakeFailureTest LdapSSLHandshakeFailureTest$CustomSocketFactoryNoUnconnected true
 * @run main/othervm LdapSSLHandshakeFailureTest LdapSSLHandshakeFailureTest$CustomSocketFactoryNoUnconnected 1000 true
 * @run main/othervm LdapSSLHandshakeFailureTest LdapSSLHandshakeFailureTest$CustomSocketFactory
 * @run main/othervm LdapSSLHandshakeFailureTest LdapSSLHandshakeFailureTest$CustomSocketFactory 1000
 * @run main/othervm LdapSSLHandshakeFailureTest LdapSSLHandshakeFailureTest$CustomSocketFactory true
 * @run main/othervm LdapSSLHandshakeFailureTest LdapSSLHandshakeFailureTest$CustomSocketFactory 1000 true
 */

public class LdapSSLHandshakeFailureTest {
    private static int SERVER_SLEEPING_TIME = 4000;
    private static String progArgs[];
    private static int curArg;
    private static String customSocketFactory;
    private static Integer connectTimeout;
    private static boolean serverSlowDown;

    private static String popArg() {
        if (curArg >= progArgs.length) {
            return null;
        }
        return progArgs[curArg++];
    }

    private static void parseArgs(String args[]) {
        progArgs = args;
        curArg = 0;

        String arg = popArg();
        if (arg == null)
            return;

        if (arg.startsWith("LdapSSLHandshakeFailureTest$CustomSocketFactory")) {
            customSocketFactory = arg;
            arg = popArg();
            if (arg == null)
                return;
        }

        try {
            connectTimeout = Integer.valueOf(arg);
            arg = popArg();
            if (arg == null)
                return;
        } catch (NumberFormatException e) {
            // then it must be the boolean arg for serverSlowDown
        }

        serverSlowDown = Boolean.valueOf(arg);
    }

    public static void main(String args[]) {
        parseArgs(args);

        System.out.println("Testing " +
            (customSocketFactory == null ? "without custom SocketFactory" : "with custom SocketFactory \"" + customSocketFactory + "\"") +
            ", " + (connectTimeout == null ? "no connectTimeout" : "connectTimeout=" + connectTimeout + "") +
            ", serverSlowDown=" + serverSlowDown);

        // Set the keystores
        setKeyStore();

        // start the test server first.
        try (TestServer server = new TestServer(serverSlowDown)) {
            server.start();
            Hashtable<String, Object> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put("java.naming.ldap.version", "3");
            env.put(Context.PROVIDER_URL, URIBuilder.newBuilder()
                    .scheme("ldaps")
                    .loopback()
                    .port(server.getPortNumber())
                    .buildUnchecked().toString());

            if (customSocketFactory != null) {
                env.put("java.naming.ldap.factory.socket", customSocketFactory);
            }

            if (connectTimeout != null) {
                env.put("com.sun.jndi.ldap.connect.timeout", connectTimeout.toString());
            }
            env.put(Context.SECURITY_PROTOCOL, "ssl");
            env.put(Context.SECURITY_AUTHENTICATION, "Simple");
            env.put(Context.SECURITY_PRINCIPAL, "cn=principal");
            env.put(Context.SECURITY_CREDENTIALS, "123456");
            LdapContext ctx = null;
            try {
                ctx = new InitialLdapContext(env, null);
            } catch (NamingException e) {
                if (customSocketFactory != null) {
                    System.out.println("Caught expected Exception with custom SocketFactory (no SSL Socket).");
                    if (CustomSocketFactory.customSocket.closeMethodCalledCount() <= 0) {
                        throw new RuntimeException("Custom Socket was not closed.");
                    }
                } else if (connectTimeout > 0) {
                    System.out.println("Caught expected Exception with connectTimeout > 0.");
                } else {
                    throw e;
                }
            } finally {
                if (ctx != null) {
                    System.out.println("Context was created, closing it.");
                    Socket sock = getSocket(ctx);
                    ctx.close();
                    if (!sock.isClosed()) {
                        throw new RuntimeException("Socket isn't closed");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static Socket getSocket(LdapContext ctx) throws Exception {
        Field defaultInitCtxField = ctx.getClass().getSuperclass().getSuperclass().getDeclaredField("defaultInitCtx");
        defaultInitCtxField.setAccessible(true);
        Object defaultInitCtx = defaultInitCtxField.get(ctx);
        Field clntField = defaultInitCtx.getClass().getDeclaredField("clnt");
        clntField.setAccessible(true);
        Object clnt = clntField.get(defaultInitCtx);
        Field connField = clnt.getClass().getDeclaredField("conn");
        connField.setAccessible(true);
        Object conn = connField.get(clnt);
        return (Socket)conn.getClass().getDeclaredField("sock").get(conn);
    }

    private static class CustomSocket extends Socket {
        private int closeMethodCalled;

        public CustomSocket() {
            super();
        }

        public CustomSocket(String s, int port) throws IOException {
            super(s, port);
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

    public static class CustomSocketFactoryNoUnconnected extends SocketFactory {
        static CustomSocket customSocket;

        public static SocketFactory getDefault() {
            return new CustomSocketFactoryNoUnconnected();
        }

        @Override
        public Socket createSocket(String s, int port) throws IOException {
            customSocket = new CustomSocket(s, port);
            return customSocket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException, UnknownHostException {
            return null;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return null;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return null;
        }
    }

    public static class CustomSocketFactory extends CustomSocketFactoryNoUnconnected {
        public static SocketFactory getDefault() {
            return new CustomSocketFactory();
        }

        @Override
        public Socket createSocket() throws SocketException {
            customSocket = new CustomSocket();
            return customSocket;
        }
    }

    private static void setKeyStore() {
        String keystore = System.getProperty("test.src", ".") + File.separator + "ksWithSAN";

        System.setProperty("javax.net.ssl.keyStore", keystore);
        System.setProperty("javax.net.ssl.keyStorePassword", "welcome1");
        System.setProperty("javax.net.ssl.trustStore", keystore);
        System.setProperty("javax.net.ssl.trustStorePassword", "welcome1");
    }

    static class TestServer extends Thread implements AutoCloseable {
        private boolean isForceToSleep;
        private final ServerSocket serverSocket;
        private final int PORT;

        private TestServer(boolean isForceToSleep) {
            this.isForceToSleep = isForceToSleep;
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
                    Thread.sleep(SERVER_SLEEPING_TIME);
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
                // e.printStackTrace();
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
