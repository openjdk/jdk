/*
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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


/* @test
 * @summary Test chat server chatserver test
 *
 * @library ../../../src/share/sample/nio/chatserver
 * @build ChatTest ChatServer Client ClientReader DataReader MessageReader NameReader
 * @run main ChatTest
 */

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

public class ChatTest {
    public static int listeningPort = 0;

    public static void main(String[] args) throws Throwable {
        testStartStop();
        testPortOpen();
        testAsksForName();
        testUseName();
        testConnectDisconnectConnect();
        testUsernameAndMessage();
        testDontReceiveMessageInNameState();
    }

    private static ChatServer startServer() throws IOException {
        ChatServer server = new ChatServer(0);
        InetSocketAddress address = (InetSocketAddress) server.getSocketAddress();
        listeningPort = address.getPort();
        server.run();
        return server;
    }

    public static void testStartStop() throws Exception {
        ChatServer server = startServer();
        server.shutdown();
    }

    public static void testPortOpen() throws Exception {
        ChatServer server = startServer();
        try {
            Socket socket = new Socket("localhost", listeningPort);
            if (!socket.isConnected()) {
                throw new RuntimeException("Failed to connect to server: port not open");
            }
        } finally {
            server.shutdown();
        }
    }

    public static void testAsksForName() throws Exception {
        ChatServer server = startServer();
        try {
            Socket socket = new Socket("localhost", listeningPort);

            Reader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String string = readAvailableString(reader);
            if (!string.equals("Name: ")) {
                throw new RuntimeException("Server doesn't send Name: ");
            }
        } finally {
            server.shutdown();
        }
    }

    public static void testUseName() throws Throwable {
        ChatServer server = startServer();
        try {
            performTestUseName();
        } finally {
            server.shutdown();
        }
    }

    public static void testConnectDisconnectConnect() throws Exception {
        ChatServer server = startServer();
        try {
            performTestConnectDisconnectConnect();
        } finally {
            server.shutdown();
        }
    }

    public static void testUsernameAndMessage() throws Exception {
        ChatServer server = startServer();
        try {
            performTestUsernameAndMessage();
        } finally {
            server.shutdown();
        }
    }

    public static void testDontReceiveMessageInNameState() throws Exception {
        ChatServer server = startServer();
        try {
            performDontReceiveMessageInNameState();
        } finally {
            server.shutdown();
        }
    }

    private static void assertEqual(List<Exception> exception, Object value, Object expected) {
        if (expected == value) {
            return;
        }
        if (expected == null) {
            exception.add(new RuntimeException("Expected null, but was: " + value));
            return;
        }
        if (!expected.equals(value)) {
            exception.add(new RuntimeException("Expected: " + expected + " but was: " + value));
            return;
        }
    }

    private static void performDontReceiveMessageInNameState() throws Exception {
        final CyclicBarrier barrier1 = new CyclicBarrier(2);
        final CyclicBarrier barrier2 = new CyclicBarrier(2);
        final CyclicBarrier barrier3 = new CyclicBarrier(2);
        final List<Exception> exceptions = Collections.synchronizedList(new ArrayList<Exception>());

        ChatConnection chatConnection = new ChatConnection() {
            @Override
            public void run(Socket socket, BufferedReader reader, Writer writer) throws Exception {
                String string = readAvailableString(reader);
                assertEqual(exceptions, string, "Name: ");
                writer.write("testClient1\n");
                waitForJoin(reader, "testClient1");
                barrier1.await();
                writer.write("Ignore this!\n");
                barrier2.await();
                barrier3.await();
            }
        };

        Thread client2 = new Thread(new ChatConnection() {
            @Override
            public void run(Socket socket, BufferedReader reader, Writer writer) throws Exception {
                barrier1.await();
                barrier2.await();
                String string = readAvailableString(reader);
                assertEqual(exceptions, string, "Name: ");
                string = readAvailableString(reader, true);
                assertEqual(exceptions, string, null);
                writer.write("testClient2\n");
                barrier3.await();
            }
        });

        client2.start();
        chatConnection.run();
        if (!exceptions.isEmpty()) {
            throw exceptions.get(0);
        }

    }

    private static void waitForJoin(BufferedReader reader, String s) throws IOException {
        String joined;
        do {
            joined = readAvailableString(reader);
        } while (!(joined != null && joined.contains("Welcome " + s)));
    }

    private static void performTestUsernameAndMessage() throws Exception {
        final CyclicBarrier barrier1 = new CyclicBarrier(2);
        final CyclicBarrier barrier2 = new CyclicBarrier(2);
        final CyclicBarrier barrier3 = new CyclicBarrier(2);
        final List<Exception> exceptions = Collections.synchronizedList(new ArrayList<Exception>());

        ChatConnection chatConnection = new ChatConnection() {
            @Override
            public void run(Socket socket, BufferedReader reader, Writer writer) throws Exception {
                String string = readAvailableString(reader);
                assertEqual(exceptions, string, "Name: ");
                writer.write("testClient1\n");
                waitForJoin(reader, "testClient1");
                barrier1.await();
                barrier2.await();
                string = readAvailableString(reader);
                assertEqual(exceptions, string, "testClient2: Hello world!\n");
                barrier3.await();
            }
        };

        Thread client2 = new Thread(new ChatConnection() {
            @Override
            public void run(Socket socket, BufferedReader reader, Writer writer) throws Exception {
                String string = readAvailableString(reader);
                assertEqual(exceptions, string, "Name: ");
                barrier1.await();
                writer.write("testClient2\nHello world!\n");
                barrier2.await();
                barrier3.await();
            }
        });

        client2.start();
        chatConnection.run();
        if (!exceptions.isEmpty()) {
            throw exceptions.get(0);
        }
    }

    private static void performTestConnectDisconnectConnect() throws Exception {
        final CyclicBarrier barrier1 = new CyclicBarrier(2);
        final CyclicBarrier barrier2 = new CyclicBarrier(2);
        final CyclicBarrier barrier3 = new CyclicBarrier(2);
        final List<Exception> exceptions = new ArrayList<Exception>();

        ChatConnection chatConnection = new ChatConnection() {
            @Override
            public void run(Socket socket, BufferedReader reader, Writer writer) throws Exception {
                String string = readAvailableString(reader);
                assertEqual(exceptions, string, "Name: ");
                writer.write("testClient1\n");
            }
        };

        ChatConnection chatConnection2 = new ChatConnection() {
            @Override
            public void run(Socket socket, BufferedReader reader, Writer writer) throws Exception {
                readAvailableString(reader);
                writer.write("testClient1\n");
                waitForJoin(reader, "testClient1");
                barrier1.await();
                writer.write("Good morning!\n");
                barrier2.await();
                String string = readAvailableString(reader);
                assertEqual(exceptions, string, "testClient2: Hello world!\n");
                barrier3.await();
            }
        };

        Thread client2 = new Thread(new ChatConnection() {
            @Override
            public void run(Socket socket, BufferedReader reader, Writer writer) throws Exception {
                readAvailableString(reader);
                writer.write("testClient2\n");
                waitForJoin(reader, "testClient2");
                barrier1.await();
                writer.write("Hello world!\n");
                barrier2.await();
                String string = readAvailableString(reader);
                assertEqual(exceptions, string, "testClient1: Good morning!\n");
                barrier3.await();
            }
        });

        client2.start();
        chatConnection.run();
        chatConnection2.run();
        if (!exceptions.isEmpty()) {
            throw exceptions.get(0);
        }
    }

    private static void performTestUseName() throws Exception {
        final CyclicBarrier barrier1 = new CyclicBarrier(2);
        final CyclicBarrier barrier2 = new CyclicBarrier(2);
        final CyclicBarrier barrier3 = new CyclicBarrier(2);
        final List<Exception> exceptions = new ArrayList<Exception>();

        ChatConnection chatConnection = new ChatConnection() {
            @Override
            public void run(Socket socket, BufferedReader reader, Writer writer) throws Exception {
                String string = readAvailableString(reader);
                if (!"Name: ".equals(string)) {
                    exceptions.add(new RuntimeException("Expected Name: "));
                }
                writer.write("testClient1\n");
                waitForJoin(reader, "testClient1");
                barrier1.await();
                barrier2.await();
                string = readAvailableString(reader);
                if (!"testClient2: Hello world!\n".equals(string)) {
                    exceptions.add(new RuntimeException("testClient2: Hello world!\n"));
                }
                barrier3.await();
            }
        };

        Thread client2 = new Thread(new ChatConnection() {
            @Override
            public void run(Socket socket, BufferedReader reader, Writer writer) throws Exception {
                String string = readAvailableString(reader);
                if (!"Name: ".equals(string)) {
                    exceptions.add(new RuntimeException("Expected Name: "));
                }
                writer.write("testClient2\n");
                waitForJoin(reader, "testClient2");
                barrier1.await();
                writer.write("Hello world!\n");
                barrier2.await();
                barrier3.await();
            }
        });

        client2.start();
        chatConnection.run();
        if (!exceptions.isEmpty()) {
            throw exceptions.get(0);
        }
    }

    private static String readAvailableString(Reader reader) throws IOException {
        return readAvailableString(reader, false);
    }

    private static String readAvailableString(Reader reader, boolean now) throws IOException {
        StringBuilder builder = new StringBuilder();
        int bytes;
        if (now && !reader.ready()) {
            return null;
        }
        do {
            char[] buf = new char[256];
            bytes = reader.read(buf);
            builder.append(buf, 0, bytes);
        } while (bytes == 256);
        return builder.toString();
    }

    private abstract static class ChatConnection implements Runnable {
        public Exception exception;

        @Override
        public void run() {
            try (Socket socket = new Socket("localhost", listeningPort);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Writer writer = new FlushingWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                socket.setTcpNoDelay(true);

                run(socket, reader, writer);
            } catch (Exception e) {
                exception = e;
            }
        }

        public abstract void run(Socket socket, BufferedReader reader, Writer writer) throws Exception;
    }

    private static class FlushingWriter extends Writer {
        public final Writer delegate;

        private FlushingWriter(Writer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            delegate.write(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public void write(String str) throws IOException {
            super.write(str);
            flush();
        }
    }
}
