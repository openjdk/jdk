/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4091859 8189366
 * @library /test/lib
 * @summary Test Socket.getInputStream().available()
 * @run main CloseAvailable
 * @run main/othervm -Djava.net.preferIPv4Stack=true CloseAvailable
 */

import java.net.*;
import java.io.*;
import jdk.test.lib.net.IPSupport;


public class CloseAvailable {

    public static void main(String[] args) throws Exception {
        IPSupport.throwSkippedExceptionIfNonOperational();

        testClose();

        testEOF(true);
        testEOF(false);
        testIOEOnClosed(true);
        testIOEOnClosed(false);
    }

    /*
     * Verifies that the Socket.getInputStream().available() throws an IOException
     * if invoked after the socket has been closed.
     */
    static void testClose() throws IOException {
        System.out.println("testClose");
        final InetAddress addr = InetAddress.getLoopbackAddress();
        final Socket acceptedSocket;
        try (final ServerSocket ss = new ServerSocket(0, 0, addr)) {
            System.out.println("created server socket: " + ss);
            final int port = ss.getLocalPort();
            // start a thread which initiates a socket connection to the server
            Thread.ofPlatform().name("Close-Available-1")
                    .start(() -> {
                        try {
                            final Socket s = new Socket(addr, port);
                            System.out.println("created socket: " + s);
                            s.close();
                            System.out.println("closed socket: " + s);
                        } catch (Exception e) {
                            System.err.println("exception in " + Thread.currentThread().getName()
                                    + ": " + e);
                            e.printStackTrace();
                        }
                    });
            // accept the client connect
            acceptedSocket = ss.accept();
            System.out.println(ss + " accepted connection " + acceptedSocket);
        } // (intentionally) close the ServerSocket

        final DataInputStream is = new DataInputStream(acceptedSocket.getInputStream());
        is.close(); // close the inputstream and thus the underlying socket
        System.out.println("closed inputstream of socket: " + acceptedSocket);
        try {
            final int av = is.available();
            // available() was expected to fail but didn't
            throw new AssertionError("Socket.getInputStream().available() was expected to fail on "
                    + acceptedSocket + " but returned " + av);
        } catch (IOException ex) {
            // expected IOException
            System.out.println("received the expected IOException: " + ex);
        }
    }

    /*
     * Verifies consistency of Socket.getInputStream().available() behaviour when EOF reached, both
     * explicitly and implicitly.
     */
    static void testEOF(boolean readUntilEOF) throws IOException {
        System.out.println("testEOF, readUntilEOF: " + readUntilEOF);
        final InetAddress addr = InetAddress.getLoopbackAddress();
        try (final ServerSocket ss = new ServerSocket()) {
            ss.bind(new InetSocketAddress(addr, 0), 0);
            System.out.println("server socket bound: " + ss);
            final int port = ss.getLocalPort();
            try (final Socket s = new Socket(addr, port)) {
                System.out.println("created socket: " + s);
                s.getOutputStream().write(0x42);
                s.shutdownOutput();

                try (final Socket soc = ss.accept()) {
                    System.out.println("accepted socket: " + soc);
                    ss.close();
                    System.out.println("closed server socket: " + ss);

                    final InputStream is = soc.getInputStream();
                    int b = is.read();
                    assert b == 0x42 : "unexpected byte read: " + b;
                    assert !s.isClosed() : "socket " + s + " is unexpectedly closed";
                    if (readUntilEOF) {
                        b = is.read();
                        assert b == -1 : "unexpected number of bytes read: " + b;
                    }

                    int a;
                    for (int i = 0; i < 100; i++) {
                        a = is.available();
                        System.out.print(a + ", ");
                        if (a != 0) {
                            throw new RuntimeException("Unexpected non-zero available: " + a);
                        }
                    }
                    assert !s.isClosed() : "socket " + s + " is unexpectedly closed";
                    final int more = is.read();
                    assert more == -1 : "unexpected byte read: " + more;
                }
            }
        }
        System.out.println("\ncomplete");
    }

    /*
     * Verifies IOException thrown by Socket.getInputStream().available(), on a closed input stream
     * that may, or may not, have reached EOF prior to closure.
     */
    static void testIOEOnClosed(boolean readUntilEOF) throws IOException {
        System.out.println("testIOEOnClosed, readUntilEOF: " + readUntilEOF);
        final InetAddress addr = InetAddress.getLoopbackAddress();
        try (final ServerSocket ss = new ServerSocket()) {
            ss.bind(new InetSocketAddress(addr, 0), 0);
            System.out.println("server socket bound: " + ss);
            final int port = ss.getLocalPort();

            try (final Socket s = new Socket(addr, port)) {
                System.out.println("created socket: " + s);
                s.getOutputStream().write(0x43);
                s.shutdownOutput();

                try (final Socket soc = ss.accept()) {
                    System.out.println("accepted socket: " + soc);
                    ss.close();
                    System.out.println("closed server socket: " + ss);

                    final InputStream is = soc.getInputStream();
                    int b = is.read();
                    assert b == 0x43 : "unexpected byte read: " + b;
                    assert !s.isClosed() : "socket " + s + " is unexpectedly closed";
                    if (readUntilEOF) {
                        b = is.read();
                        assert b == -1 : "unexpected byte read: " + b;
                    }
                    is.close();
                    System.out.println("closed inputstream of socket: " + soc);
                    try {
                        b = is.available();
                        throw new RuntimeException("UNEXPECTED successful read: " + b);
                    } catch (IOException expected) {
                        System.out.println("caught expected IOException:" + expected);
                    }
                }
            }
        }
        System.out.println("\ncomplete");
    }
}
