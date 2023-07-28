/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL.  Use is subject to license terms.
 */

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.*;

import javax.naming.*;
import javax.naming.directory.*;
import javax.net.SocketFactory;

/*
 * @test
 * @bug 8311299
 * @summary make sure socket is closed when the error happens for flushing
 * @library /test/lib
 * @modules jdk.compiler
 */

public class Main {
    public static String SOCKET_CLOSED_MSG = "The socket has been closed.";
    public static String SOCKET_NOT_CLOSED_MSG = "The socket was not closed.";

    public static String BAD_FLUSH = "Bad flush!";
    private static boolean loaded = false;

    public static void main(String[] args) throws Exception {

        Hashtable<String, Object> props = new Hashtable<>();

        props.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        props.put(javax.naming.Context.PROVIDER_URL, "ldap://localhost:1389/o=example");
        props.put("java.naming.ldap.factory.socket", EvilSocketFactory.class.getName());

        try {
            final DirContext ctx = new InitialDirContext(props);
        } catch (Exception e) {
            if (EvilSocketFactory.evilSocket.closeMethodCalledCount() > 0) {
                System.out.println(SOCKET_CLOSED_MSG);
            } else {
                System.out.println(SOCKET_NOT_CLOSED_MSG);
            }
        }
    }

    public static class EvilClassLoader extends ClassLoader implements
            Serializable {

        private static final long serialVersionUID = 1L;

        private void readObject(java.io.ObjectInputStream stream)
                throws IOException, ClassNotFoundException {
            loaded = true;
        }
    }

    public static class EvilSocketFactory extends SocketFactory {
        public static EvilSocket evilSocket = new EvilSocket();
        public static EvilSocketFactory getDefault() {
            return new EvilSocketFactory();
        }
        @Override
        public Socket createSocket() {
            return evilSocket;
        }

        @Override
        public Socket createSocket(String s, int timeout) {
            return evilSocket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost,
                                   int localPort) {
            return evilSocket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) {
            return evilSocket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port,
                                   InetAddress localAddress, int localPort) {
            return evilSocket;
        }
    }

    public static class LdapInputStream extends InputStream {

        private LdapOutputStream los;
        private State state = State.WAITING_FOR_LOGIN;
        private ByteArrayInputStream bos;
        int pos = 0;

        private static enum State {
            WAITING_FOR_LOGIN, LOGGED_IN, SEARCH, WAITING_FOR_SEARCH
        }

        public LdapInputStream(LdapOutputStream los) {
            this.los = los;
        }

        private void pause() {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int read() throws IOException {
            switch (state) {
            case WAITING_FOR_LOGIN:
                while (!los.loggedIn) {
                    pause();
                }
                bos = new ByteArrayInputStream(Data.BIND_RESPONSE);
                state = State.LOGGED_IN;
                int next = bos.read();
                return next;
            case LOGGED_IN:
                next = bos.read();
                if (next < 0) {
                    bos = null;
                    state = State.WAITING_FOR_SEARCH;
                }
                return next;
            case WAITING_FOR_SEARCH:
                while (!los.searched) {
                    pause();
                }
                bos = new ByteArrayInputStream(Data.SEARCH_RESPONSE);
                state = State.SEARCH;
                next = bos.read();
                return next;
            case SEARCH:
                next = bos.read();
                return next;
            }

            return -1;
        }
    }

    public static class LdapOutputStream extends OutputStream {
        private volatile boolean loggedIn;
        private volatile boolean searched;
        private int bytes = 0;

        @Override
        public void write(int b) throws IOException {
            bytes = bytes + 1;
            if (bytes == Data.BIND_SIZE) {
                loggedIn = true;
                System.out.println("loggedin");
            }

            if (bytes == Data.BIND_SIZE + Data.SEARCH_SIZE) {
                searched = true;
                System.out.println("searched");
            }
        }

        @Override
      public void flush() throws IOException {
            System.out.println(BAD_FLUSH);
            throw new IOException(BAD_FLUSH);
       }
    }

    public static class EvilSocket extends Socket {
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
