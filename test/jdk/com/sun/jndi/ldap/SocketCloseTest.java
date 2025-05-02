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

import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.net.SocketFactory;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8313657
 * @summary make sure socket is closed when the error happens for OutputStream flushing
 * The value of provider url can be random, not necessary to be the one in the code
 * @library /test/lib
 * @run main/othervm SocketCloseTest
 */

public class SocketCloseTest {
    public static String SOCKET_CLOSED_MSG = "The socket has been closed.";
    public static String SOCKET_NOT_CLOSED_MSG = "The socket was not closed.";
    public static String BAD_FLUSH = "Bad flush!";
    private static final byte[] BIND_RESPONSE = new byte[]{
            48, 12, 2, 1, 1, 97, 7, 10, 1, 0, 4, 0, 4, 0
    };

    public static void main(String[] args) throws Exception {
        SocketCloseTest scTest = new SocketCloseTest();
        scTest.runCloseSocketScenario();
    }

    public void runCloseSocketScenario() throws Exception {
        Hashtable<String, Object> props = new Hashtable<>();

        props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        props.put(Context.PROVIDER_URL, "ldap://localhost:1389/o=example");
        props.put("java.naming.ldap.factory.socket", CustomSocketFactory.class.getName());
        try {
            final DirContext ctx = new InitialDirContext(props);
        } catch (Exception e) {
            if (CustomSocketFactory.customSocket.closeMethodCalledCount() > 0) {
                System.out.println(SOCKET_CLOSED_MSG);
            } else {
                System.out.println(SOCKET_NOT_CLOSED_MSG);
                throw e;
            }
        }
    }

    public static class CustomSocketFactory extends SocketFactory {
        public static CustomSocket customSocket = new CustomSocket();

        public static CustomSocketFactory getDefault() {
            return new CustomSocketFactory();
        }

        @Override
        public Socket createSocket() {
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

    private static class LdapInputStream extends InputStream {
        private ByteArrayInputStream bos;

        public LdapInputStream() {
        }

        @Override
        public int read() throws IOException {
            bos = new ByteArrayInputStream(BIND_RESPONSE);
            return bos.read();
        }
    }

    private static class LdapOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            System.out.println("output stream writing");
        }

        @Override
        public void flush() throws IOException {
            System.out.println(BAD_FLUSH);
            throw new IOException(BAD_FLUSH);
        }
    }

    private static class CustomSocket extends Socket {
        private int closeMethodCalled = 0;
        private LdapOutputStream output = new LdapOutputStream();
        private LdapInputStream input = new LdapInputStream();

        public void connect(SocketAddress address, int timeout) {
        }

        public InputStream getInputStream() {
            return input;
        }

        public OutputStream getOutputStream() {
            return output;
        }

        public int closeMethodCalledCount() {
            return closeMethodCalled;
        }

        @Override
        public void close() throws IOException {
            closeMethodCalled++;
            super.close();
        }
    }
}
