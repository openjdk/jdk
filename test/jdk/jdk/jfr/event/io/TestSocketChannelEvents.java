/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.io;

import static jdk.test.lib.Asserts.assertEquals;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.thread.TestThread;
import jdk.test.lib.thread.XRun;

/**
 * @test
 * @summary test socket read/write events on SocketChannel
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.io.TestSocketChannelEvents
 */
public class TestSocketChannelEvents {
    private static final int bufSizeA = 10;
    private static final int bufSizeB = 20;

    private List<IOEvent> expectedEvents = new ArrayList<>();

    private synchronized void addExpectedEvent(IOEvent event) {
        expectedEvents.add(event);
    }

    public static void main(String[] args) throws Throwable {
        new TestSocketChannelEvents().test();
        new TestSocketChannelEvents().testNonBlockingConnect();
        testConnectException();
        testNonBlockingConnectException();
    }

    private void test() throws Throwable {
        try (Recording recording = new Recording()) {
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                recording.enable(IOEvent.EVENT_SOCKET_CONNECT);
                recording.enable(IOEvent.EVENT_SOCKET_READ);
                recording.enable(IOEvent.EVENT_SOCKET_WRITE);
                recording.start();

                InetAddress lb = InetAddress.getLoopbackAddress();
                ssc.bind(new InetSocketAddress(lb, 0));

                TestThread readerThread = new TestThread(new XRun() {
                    @Override
                    public void xrun() throws IOException {
                        ByteBuffer bufA = ByteBuffer.allocate(bufSizeA);
                        ByteBuffer bufB = ByteBuffer.allocate(bufSizeB);
                        try (SocketChannel sc = ssc.accept()) {
                            int readSize = sc.read(bufA);
                            assertEquals(bufSizeA, readSize, "Wrong readSize bufA");
                            addExpectedEvent(IOEvent.createSocketReadEvent(bufSizeA, sc.socket()));

                            bufA.clear();
                            bufA.limit(1);
                            readSize = (int) sc.read(new ByteBuffer[] { bufA, bufB });
                            assertEquals(1 + bufSizeB, readSize, "Wrong readSize 1+bufB");
                            addExpectedEvent(IOEvent.createSocketReadEvent(readSize, sc.socket()));

                            // We try to read, but client have closed. Should
                            // get EOF.
                            bufA.clear();
                            bufA.limit(1);
                            readSize = sc.read(bufA);
                            assertEquals(-1, readSize, "Wrong readSize at EOF");
                            addExpectedEvent(IOEvent.createSocketReadEvent(-1, sc.socket()));
                        }
                    }
                });
                readerThread.start();

                try (SocketChannel sc = SocketChannel.open(ssc.getLocalAddress())) {
                    addExpectedEvent(IOEvent.createSocketConnectEvent(sc.socket()));
                    ByteBuffer bufA = ByteBuffer.allocateDirect(bufSizeA);
                    ByteBuffer bufB = ByteBuffer.allocateDirect(bufSizeB);
                    for (int i = 0; i < bufSizeA; ++i) {
                        bufA.put((byte) ('a' + (i % 20)));
                    }
                    for (int i = 0; i < bufSizeB; ++i) {
                        bufB.put((byte) ('A' + (i % 20)));
                    }
                    bufA.flip();
                    bufB.flip();

                    sc.write(bufA);
                    addExpectedEvent(IOEvent.createSocketWriteEvent(bufSizeA, sc.socket()));

                    bufA.clear();
                    bufA.limit(1);
                    int bytesWritten = (int) sc.write(new ByteBuffer[] { bufA, bufB });
                    assertEquals(1 + bufSizeB, bytesWritten, "Wrong bytesWritten 1+bufB");
                    addExpectedEvent(IOEvent.createSocketWriteEvent(bytesWritten, sc.socket()));
                }

                readerThread.joinAndThrow();
                recording.stop();
                List<RecordedEvent> events = Events.fromRecording(recording);
                IOHelper.verifyEquals(events, expectedEvents);
            }
        }
    }

    private void testNonBlockingConnect() throws Throwable {
        try (Recording recording = new Recording()) {
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                recording.enable(IOEvent.EVENT_SOCKET_CONNECT);
                recording.start();

                InetAddress lb = InetAddress.getLoopbackAddress();
                ssc.bind(new InetSocketAddress(lb, 0));
                SocketAddress addr = ssc.getLocalAddress();

                try (SocketChannel sc = SocketChannel.open()) {
                    sc.configureBlocking(false);
                    sc.connect(addr);
                    try (SocketChannel serverSide = ssc.accept()) {
                        while (! sc.finishConnect()) {
                            Thread.sleep(1);
                        }
                    }
                    addExpectedEvent(IOEvent.createSocketConnectEvent(sc.socket()));
                }

                recording.stop();
                List<RecordedEvent> events = Events.fromRecording(recording);
                IOHelper.verifyEquals(events, expectedEvents);
            }
        }
    }

    private static void testConnectException() throws Throwable {
        try (Recording recording = new Recording()) {
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                recording.enable(IOEvent.EVENT_SOCKET_CONNECT_FAILED);
                recording.start();

                InetAddress lb = InetAddress.getLoopbackAddress();
                ssc.bind(new InetSocketAddress(lb, 0));
                SocketAddress addr = ssc.getLocalAddress();
                ssc.close();

                // try to connect, but the server will not accept
                IOException connectException = null;
                try (SocketChannel sc = SocketChannel.open(addr)) {
                } catch (IOException ioe) {
                    // we expect this
                    connectException = ioe;
                }

                recording.stop();
                List<RecordedEvent> events = Events.fromRecording(recording);
                Asserts.assertEquals(1, events.size());
                IOHelper.checkConnectEventException(events.get(0), connectException);
            }
        }
    }

    private static void testNonBlockingConnectException() throws Throwable {
        try (Recording recording = new Recording()) {
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                recording.enable(IOEvent.EVENT_SOCKET_CONNECT_FAILED);
                recording.start();

                InetAddress lb = InetAddress.getLoopbackAddress();
                ssc.bind(new InetSocketAddress(lb, 0));
                SocketAddress addr = ssc.getLocalAddress();
                ssc.close();

                IOException connectException = null;
                try (SocketChannel sc = SocketChannel.open()) {
                    sc.configureBlocking(false);
                    boolean connected = sc.connect(addr);
                    while (!connected) {
                        Thread.sleep(10);
                        connected = sc.finishConnect();
                    }
                } catch (IOException ioe) {
                    // we expect this
                    connectException = ioe;
                }

                recording.stop();
                List<RecordedEvent> events = Events.fromRecording(recording);
                Asserts.assertEquals(1, events.size());
                IOHelper.checkConnectEventException(events.get(0), connectException);
            }
        }
    }
}
