/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test socket-channel connection-state transitions
 * @library ..
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;


public class ConnectState {

    static PrintStream log = System.err;

    static String REMOTE_HOST = TestUtil.HOST;
    static int REMOTE_PORT = 7;                         // echo
    static InetSocketAddress remote;

    final static int ST_UNCONNECTED = 0;
    final static int ST_PENDING = 1;
    final static int ST_CONNECTED = 2;
    final static int ST_CLOSED = 3;

    static abstract class Test {

        abstract String go(SocketChannel sc) throws Exception;

        static void check(boolean test, String desc) throws Exception {
            if (!test)
                throw new Exception("Incorrect state: " + desc);
        }

        static void check(SocketChannel sc, int state) throws Exception {
            switch (state) {
            case ST_UNCONNECTED:
                check(!sc.isConnected(), "!isConnected");
                check(!sc.isConnectionPending(), "!isConnectionPending");
                check(sc.isOpen(), "isOpen");
                break;
            case ST_PENDING:
                check(!sc.isConnected(), "!isConnected");
                check(sc.isConnectionPending(), "isConnectionPending");
                check(sc.isOpen(), "isOpen");
                break;
            case ST_CONNECTED:
                check(sc.isConnected(), "isConnected");
                check(!sc.isConnectionPending(), "!isConnectionPending");
                check(sc.isOpen(), "isOpen");
                break;
            case ST_CLOSED:
                check(sc.isConnected(), "isConnected");
                check(!sc.isConnectionPending(), "!isConnectionPending");
                check(sc.isOpen(), "isOpen");
                break;
            }
        }

        Test(String name, Class exception, int state) throws Exception {
            SocketChannel sc = SocketChannel.open();
            String note = null;
            try {
                try {
                    note = go(sc);
                } catch (Exception x) {
                    if (exception != null) {
                        if (exception.isInstance(x)) {
                            log.println(name + ": As expected: "
                                        + x);
                            check(sc, state);
                            return;
                        } else {
                            throw new Exception(name
                                                + ": Incorrect exception",
                                                x);
                        }
                    } else {
                        throw new Exception(name
                                            + ": Unexpected exception",
                                            x);
                    }
                }
                if (exception != null)
                    throw new Exception(name
                                        + ": Expected exception not thrown: "
                                        + exception);
                check(sc, state);
                log.println(name + ": Returned normally"
                            + ((note != null) ? ": " + note : ""));
            } finally {
                if (sc.isOpen())
                    sc.close();
            }
        }

    }

    static void tests() throws Exception {
        log.println(remote);

        new Test("Read unconnected", NotYetConnectedException.class,
                 ST_UNCONNECTED) {
                String go(SocketChannel sc) throws Exception {
                    ByteBuffer b = ByteBuffer.allocateDirect(1024);
                    sc.read(b);
                    return null;
                }};

        new Test("Write unconnected", NotYetConnectedException.class,
                 ST_UNCONNECTED) {
                String go(SocketChannel sc) throws Exception {
                    ByteBuffer b = ByteBuffer.allocateDirect(1024);
                    sc.write(b);
                    return null;
                }};

        new Test("Simple connect", null, ST_CONNECTED) {
                String go(SocketChannel sc) throws Exception {
                    sc.connect(remote);
                    return null;
                }};

        new Test("Simple connect & finish", null, ST_CONNECTED) {
                String go(SocketChannel sc) throws Exception {
                    sc.connect(remote);
                    if (!sc.finishConnect())
                        throw new Exception("finishConnect returned false");
                    return null;
                }};

        new Test("Double connect",
                 AlreadyConnectedException.class, ST_CONNECTED) {
                String go(SocketChannel sc) throws Exception {
                    sc.connect(remote);
                    sc.connect(remote);
                    return null;
                }};

        new Test("Finish w/o start",
                 NoConnectionPendingException.class, ST_UNCONNECTED) {
                String go(SocketChannel sc) throws Exception {
                    sc.finishConnect();
                    return null;
                }};

        new Test("NB simple connect", null, ST_CONNECTED) {
                String go(SocketChannel sc) throws Exception {
                    sc.configureBlocking(false);
                    sc.connect(remote);
                    int n = 0;
                    while (!sc.finishConnect()) {
                        Thread.sleep(10);
                        n++;
                    }
                    sc.finishConnect();         // Check redundant invocation
                    return ("Tries to finish = " + n);
                }};

        new Test("NB double connect",
                 ConnectionPendingException.class, ST_PENDING) {
                String go(SocketChannel sc) throws Exception {
                    sc.configureBlocking(false);
                    sc.connect(remote);
                    sc.connect(remote);
                    return null;
                }};

        new Test("NB finish w/o start",
                 NoConnectionPendingException.class, ST_UNCONNECTED) {
                String go(SocketChannel sc) throws Exception {
                    sc.configureBlocking(false);
                    sc.finishConnect();
                    return null;
                }};

        new Test("NB connect, B finish", null, ST_CONNECTED) {
                String go(SocketChannel sc) throws Exception {
                    sc.configureBlocking(false);
                    sc.connect(remote);
                    sc.configureBlocking(true);
                    sc.finishConnect();
                    return null;
                }};

    }

    public static void main(String[] args) throws Exception {
        remote = new InetSocketAddress(InetAddress.getByName(REMOTE_HOST),
                                       REMOTE_PORT);
        tests();
    }

}
