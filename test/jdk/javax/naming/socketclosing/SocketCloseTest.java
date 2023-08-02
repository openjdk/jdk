/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL.  Use is subject to license terms.
 */

import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.net.SocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Hashtable;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8311299
 * @summary make sure socket is closed when the error happens for flushing
 * @library /test/lib
 */

public class SocketCloseTest {
    public static String SOCKET_CLOSED_MSG = "The socket has been closed.";
    public static String SOCKET_NOT_CLOSED_MSG = "The socket was not closed.";
    public static String BAD_FLUSH = "Bad flush!";
    private static final int BIND_SIZE = 14;
    private static final byte[] BIND_RESPONSE = new byte[] {
            48, 12, 2, 1, 1, 97, 7, 10, 1, 0, 4, 0, 4, 0};
    private static final int SEARCH_SIZE = 87;
    private static final byte[] SEARCH_RESPONSE = new byte[] {
            48, -127, -71, 2, 1, 2, 100, -127, -77, 4, 19, 111, 61, 101, 120, 97, 109, 112, 108,
            101, 44, 111, 61, 101, 120, 97, 109, 112, 108, 101, 48, -127, -101, 48, 34, 4, 11,
            111, 98, 106, 101, 99, 116, 99, 108, 97, 115, 115, 49, 19, 4, 12, 111, 114, 103, 97,
            110, 105, 122, 97, 116, 105, 111, 110, 4, 3, 116, 111, 112, 48, 34, 4, 13, 106, 97,
            118, 97, 67, 108, 97, 115, 115, 78, 97, 109, 101, 49, 17, 4, 15, 69, 118, 105, 108,
            67, 108, 97, 115, 115, 76, 111, 97, 100, 101, 114, 48, 65, 4, 18, 106, 97, 118, 97,
            83, 101, 114, 105, 97, 108, 105, 122, 101, 100, 68, 97, 116, 97, 49, 43, 4, 41, -84,
            -19, 0, 5, 115, 114, 0, 20, 77, 97, 105, 110, 36, 69, 118, 105, 108, 67, 108, 97, 115,
            115, 76, 111, 97, 100, 101, 114, 0, 0, 0, 0, 0, 0, 0, 1, 2, 0, 0, 120, 112, 48, 14,
            4, 1, 111, 49, 9, 4, 7, 101, 120, 97, 109, 112, 108, 101, 48, 12, 2, 1, 2, 101, 7, 10,
            1, 0, 4, 0, 4, 0};


    private static boolean loaded = false;

    public static void main(String[] args) throws Exception {

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
            }
        }
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJvm("SocketCloseTest");

        outputAnalyzer.stdoutShouldContain(SOCKET_CLOSED_MSG);
        outputAnalyzer.stdoutShouldNotContain(SOCKET_NOT_CLOSED_MSG);
        outputAnalyzer.stdoutShouldContain(BAD_FLUSH);
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

    public static class LdapInputStream extends InputStream {

        private LdapOutputStream los;
        private ByteArrayInputStream bos;
        int pos = 0;

        public LdapInputStream(LdapOutputStream los) {
            this.los = los;
        }

        @Override
        public int read() throws IOException {
                bos = new ByteArrayInputStream(BIND_RESPONSE);
                int next = bos.read();
                return next;
        }
    }

    public static class LdapOutputStream extends OutputStream {

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

    public static class CustomSocket extends Socket {
        private int closeMethodCalled = 0;
        private LdapOutputStream output = new LdapOutputStream();

        private LdapInputStream input = new LdapInputStream(output);

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
            closeMethodCalled ++;
            super.close();
        }
    }
}
