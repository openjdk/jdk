/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.thread.TestThread;
import jdk.test.lib.thread.XRun;

import static java.net.StandardProtocolFamily.UNIX;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.io.TestSocketChannelEvents inet
 * @run main/othervm jdk.jfr.event.io.TestSocketChannelEvents unix
 */
public class TestSocketChannelEvents {
    private static final int bufSizeA = 10;
    private static final int bufSizeB = 20;

    private List<IOEvent> expectedEvents = new ArrayList<>();

    private synchronized void addExpectedEvent(IOEvent event) {
        expectedEvents.add(event);
    }

    static ServerSocketChannel getListener(String arg) throws IOException {
        if (arg.equals("inet")) {
            return ServerSocketChannel.open();
        } else if (arg.equals("unix"))
            return ServerSocketChannel.open(UNIX);
        else
            throw new RuntimeException("invalid arg");
    }

    static IOEvent getReadEvent(int size, SocketChannel sc) throws IOException {
        SocketAddress addr = sc.getLocalAddress();
        if (addr instanceof InetSocketAddress) {
            return IOEvent.createSocketReadEvent(size, sc.socket());
        } else if (addr instanceof UnixDomainSocketAddress) {
            return IOEvent.createUnixSocketReadEvent(size, sc);
        } else
            throw new RuntimeException("unexpected channel type");
    }

    static IOEvent getWriteEvent(int size, SocketChannel sc) throws IOException {
        SocketAddress addr = sc.getLocalAddress();
        if (addr instanceof InetSocketAddress) {
            return IOEvent.createSocketWriteEvent(size, sc.socket());
        } else if (addr instanceof UnixDomainSocketAddress) {
            return IOEvent.createUnixSocketWriteEvent(size, sc);
        } else
            throw new RuntimeException("unexpected channel type");
    }

    static String getReadEventName(String mode) {
        if (mode.equals("inet"))
            return IOEvent.EVENT_SOCKET_READ;
        else if (mode.equals("unix"))
            return IOEvent.EVENT_UNIX_SOCKET_READ;
        else
            throw new RuntimeException();
    }

    static String getWriteEventName(String mode) {
        if (mode.equals("inet"))
            return IOEvent.EVENT_SOCKET_WRITE;
        else if (mode.equals("unix"))
            return IOEvent.EVENT_UNIX_SOCKET_WRITE;
        else
            throw new RuntimeException();
    }

    public static void main(String[] args) throws Throwable {
        new TestSocketChannelEvents().test(args[0]);
    }

    public void test(String mode) throws Throwable {
        try (Recording recording = new Recording()) {
            SocketAddress local = null;
            try (ServerSocketChannel ss = getListener(mode)) {
                recording.enable(getReadEventName(mode)).withThreshold(Duration.ofMillis(0));
                recording.enable(getWriteEventName(mode)).withThreshold(Duration.ofMillis(0));
                recording.start();

                ss.bind(null);
                local = ss.getLocalAddress();
                if (ss.getLocalAddress() instanceof InetSocketAddress) {
                    ss.socket().setReuseAddress(true);
                }

                TestThread readerThread = new TestThread(new XRun() {
                    @Override
                    public void xrun() throws IOException {
                        ByteBuffer bufA = ByteBuffer.allocate(bufSizeA);
                        ByteBuffer bufB = ByteBuffer.allocate(bufSizeB);
                        try (SocketChannel sc = ss.accept()) {
                            int readSize = sc.read(bufA);
                            assertEquals(readSize, bufSizeA, "Wrong readSize bufA");
                            addExpectedEvent(getReadEvent(bufSizeA, sc));

                            bufA.clear();
                            bufA.limit(1);
                            readSize = (int) sc.read(new ByteBuffer[] { bufA, bufB });
                            assertEquals(readSize, 1 + bufSizeB, "Wrong readSize 1+bufB");
                            addExpectedEvent(getReadEvent(readSize, sc));

                            // We try to read, but client have closed. Should
                            // get EOF.
                            bufA.clear();
                            bufA.limit(1);
                            readSize = sc.read(bufA);
                            assertEquals(readSize, -1, "Wrong readSize at EOF");
                            addExpectedEvent(getReadEvent(-1, sc));
                        }
                    }
                });
                readerThread.start();

                try (SocketChannel sc = SocketChannel.open(ss.getLocalAddress())) {
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
                    addExpectedEvent(getWriteEvent(bufSizeA, sc));

                    bufA.clear();
                    bufA.limit(1);
                    int bytesWritten = (int) sc.write(new ByteBuffer[] { bufA, bufB });
                    assertEquals(bytesWritten, 1 + bufSizeB, "Wrong bytesWritten 1+bufB");
                    addExpectedEvent(getWriteEvent(bytesWritten, sc));
                }

                readerThread.joinAndThrow();
                recording.stop();
                List<RecordedEvent> events = Events.fromRecording(recording);
                IOHelper.verifyEquals(events, expectedEvents);
            } finally {
                if (local instanceof UnixDomainSocketAddress) {
                    var ua = (UnixDomainSocketAddress)local;
                    Files.deleteIfExists(ua.getPath());
                }
            }
        }
    }
}
