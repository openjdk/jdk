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

/*
 * @test
 * @bug 8310902
 * @summary Test async close and interrupt during FileChannel transferTo/transferFrom
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run junit CloseDuringTransfer
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.Stream;
import static java.nio.file.StandardOpenOption.*;
import static java.util.concurrent.TimeUnit.*;

import jdk.test.lib.RandomFactory;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class CloseDuringTransfer {
    private static final int SOURCE_SIZE = 1024 * 1024;
    private static final Random RAND = RandomFactory.getRandom();

    // used for schedule close and interrupt
    private static ScheduledExecutorService scheduler;

    @BeforeAll
    static void setup() throws Exception {
        ThreadFactory factory = Executors.defaultThreadFactory();
        scheduler = Executors.newScheduledThreadPool(8, factory);
    }

    @AfterAll
    static void finish() {
        scheduler.shutdown();
    }

    /**
     * Channels that may be used as a transferTo target.
     */
    static Stream<WritableByteChannel> targets() throws Exception {
        return Stream.of(
                fileChannelTarget(),
                socketChannelTarget(),
                pipeSink(),
                arbitraryTarget()
        );
    }

    /**
     * Channels that may be used as a transferFrom source.
     */
    static Stream<ReadableByteChannel> sources() throws Exception {
        return Stream.of(
                fileChannelSource(SOURCE_SIZE),
                socketChannelSource(SOURCE_SIZE),
                pipeSource(SOURCE_SIZE),
                arbitrarySource(SOURCE_SIZE)
        );
    }

    /**
     * Close source file channel during transferTo.
     */
    @ParameterizedTest
    @MethodSource("targets")
    void testCloseSourceDuringTransferTo(WritableByteChannel target) throws Exception {
        try (FileChannel src = fileChannelSource(SOURCE_SIZE); target) {
            scheduleClose(src);
            try {
                long n = src.transferTo(0, Long.MAX_VALUE, target);
                assertTrue(n > 0);
            } catch (ClosedChannelException e) {
                assertFalse(src.isOpen());
            }
            assertTrue(target.isOpen());
        }
    }

    /**
     * Close target channel during transferTo.
     */
    @ParameterizedTest
    @MethodSource("targets")
    void testCloseTargetDuringTransferTo(WritableByteChannel target) throws Exception {
        try (FileChannel src = fileChannelSource(SOURCE_SIZE); target) {
            scheduleClose(target);
            try {
                long n = src.transferTo(0, Long.MAX_VALUE, target);
                assertTrue(n > 0);
            } catch (ClosedChannelException e) {
                assertFalse(target.isOpen());
            }
            assertTrue(src.isOpen());
        }
    }

    /**
     * Interrupt thread during transferTo.
     */
    @ParameterizedTest
    @MethodSource("targets")
    void testInterruptDuringTransferTo(WritableByteChannel target) throws Exception {
        try (FileChannel src = fileChannelSource(SOURCE_SIZE); target) {
            Future<?> interrupter = scheduleInterrupt();
            try {
                long n = src.transferTo(0, Long.MAX_VALUE, target);
                assertTrue(n > 0);
            } catch (ClosedByInterruptException e) {
                assertTrue(Thread.currentThread().isInterrupted());
                assertFalse(src.isOpen());
                assertFalse(target.isOpen());
            } finally {
                finishInterrupt(interrupter);
            }
        }
    }

    /**
     * Close source channel during transferFrom.
     */
    @ParameterizedTest
    @MethodSource("sources")
    void testCloseSourceDuringTransferFrom(ReadableByteChannel src) throws Exception {
        try (src; FileChannel target = fileChannelTarget()) {
            scheduleClose(src);
            try {
                long n = target.transferFrom(src, 0, Long.MAX_VALUE);
                assertTrue(n > 0);
            } catch (ClosedChannelException e) {
                assertFalse(src.isOpen());
            }
            assertTrue(target.isOpen());
        }
    }

    /**
     * Close target file channel during transferFrom.
     */
    @ParameterizedTest
    @MethodSource("sources")
    void testCloseTargetDuringTransferFrom(ReadableByteChannel src) throws Exception {
        try (src;  FileChannel target = fileChannelTarget()) {
            scheduleClose(target);
            try {
                long n = target.transferFrom(src, 0, Long.MAX_VALUE);
                assertTrue(n > 0);
            } catch (ClosedChannelException e) {
                assertFalse(target.isOpen());
            }
            assertTrue(src.isOpen());
        }
    }

    /**
     * Interrupt thread during transferFrom.
     */
    @ParameterizedTest
    @MethodSource("sources")
    void testInterruptTransferDuringTransferFrom(ReadableByteChannel src) throws Exception {
        try (src; FileChannel target = fileChannelTarget()) {
            Future<?> interrupter = scheduleInterrupt();
            try {
                long n = target.transferFrom(src, 0, Long.MAX_VALUE);
                assertTrue(n > 0);
            } catch (ClosedByInterruptException e) {
                assertTrue(Thread.currentThread().isInterrupted());
                assertFalse(src.isOpen());
                assertFalse(target.isOpen());
            } finally {
                finishInterrupt(interrupter);
            }
        }
    }

    /**
     * Schedules a channel to be closed after a random delay.
     */
    private Future<?> scheduleClose(Channel channel) {
        int delay = RAND.nextInt(10);
        return scheduler.schedule(() -> {
            channel.close();
            return null;
        }, delay, MILLISECONDS);
    }

    /**
     * Schedules the caller thread to be interrupted after a random delay.
     */
    private Future<?> scheduleInterrupt() {
        Thread thread = Thread.currentThread();
        int delay = RAND.nextInt(10);
        return scheduler.schedule(() -> {
            thread.interrupt();
            return null;
        }, delay, MILLISECONDS);
    }

    /**
     * Waits for the interrupt task submitted by scheduleInterrupt, and clears the
     * current thread's interrupt status.
     */
    private void finishInterrupt(Future<?> interrupter) throws Exception {
        boolean done = false;
        while (!done) {
            try {
                interrupter.get();
                done = true;
            } catch (InterruptedException e) { }
        }
        Thread.interrupted();
    }

    /**
     * Return a FileChannel to a file that reads up to given number of bytes.
     */
    private static FileChannel fileChannelSource(int size) throws Exception {
        Path here = Path.of(".");
        Path source = Files.createTempFile(here, "source", "dat");
        Files.write(source, new byte[size]);
        return FileChannel.open(source);
    }

    /**
     * Return a FileChannel to a file opened for writing.
     */
    private static FileChannel fileChannelTarget() throws Exception {
        Path here = Path.of(".");
        Path target = Files.createTempFile(here, "target", "dat");
        return FileChannel.open(target, CREATE, TRUNCATE_EXISTING, WRITE);
    }

    /**
     * Return a SocketChannel to a socket that reads up to given number of bytes.
     */
    private static SocketChannel socketChannelSource(int size) throws Exception {
        var lb = InetAddress.getLoopbackAddress();
        try (var listener = ServerSocketChannel.open()) {
            listener.bind(new InetSocketAddress(lb, 0));
            SocketChannel sc1 = SocketChannel.open();
            SocketChannel sc2 = null;
            try {
                sc1.socket().connect(listener.getLocalAddress(), 10_000);
                sc2 = listener.accept();
            } catch (IOException ioe) {
                sc1.close();
                throw ioe;
            }
            SocketChannel peer = sc2;
            scheduler.submit(() -> {
                try (peer) {
                    ByteBuffer bb = ByteBuffer.allocate(size);
                    while (bb.hasRemaining()) {
                        peer.write(bb);
                    }
                }
                return null;
            });
            return sc1;
        }
    }

    /**
     * Return a SocketChannel with the channel's socket ready for writing.
     */
    private static SocketChannel socketChannelTarget() throws Exception {
        var lb = InetAddress.getLoopbackAddress();
        try (var listener = ServerSocketChannel.open()) {
            listener.bind(new InetSocketAddress(lb, 0));
            SocketChannel sc1 = SocketChannel.open();
            SocketChannel sc2 = null;
            try {
                sc1.socket().connect(listener.getLocalAddress(), 10_000);
                sc2 = listener.accept();
            } catch (IOException ioe) {
                sc1.close();
                throw ioe;
            }
            SocketChannel peer = sc2;
            scheduler.submit(() -> {
                ByteBuffer bb = ByteBuffer.allocate(8192);
                try {
                    int n;
                    do {
                        bb.clear();
                        n = peer.read(bb);
                    } while (n > 0);
                } catch (IOException ioe) {
                    if (peer.isOpen()) {
                        ioe.printStackTrace();
                    }
                }
            });
            return sc1;
        }
    }

    /**
     * Return a Pipe.SourceChannel that reads up to given number of bytes.
     */
    private static Pipe.SourceChannel pipeSource(int size) throws Exception {
        Pipe pipe = Pipe.open();
        Pipe.SourceChannel source = pipe.source();
        Pipe.SinkChannel sink = pipe.sink();
        scheduler.submit(() -> {
            try (sink) {
                ByteBuffer bb = ByteBuffer.allocate(size);
                while (bb.hasRemaining()) {
                    sink.write(bb);
                }
            }
            return null;
        });
        return source;
    }

    /**
     * Return a Pipe.SinkChannel with the channel's pipe ready for writing.
     */
    private static Pipe.SinkChannel pipeSink() throws Exception {
        Pipe pipe = Pipe.open();
        Pipe.SourceChannel source = pipe.source();
        Pipe.SinkChannel sink = pipe.sink();
        scheduler.submit(() -> {
            ByteBuffer bb = ByteBuffer.allocate(8192);
            try {
                int n;
                do {
                    bb.clear();
                    n = source.read(bb);
                } while (n > 0);
            } catch (IOException ioe) {
                if (source.isOpen()) {
                    ioe.printStackTrace();
                }
            }
        });
        return sink;
    }

    /**
     * Return a ReadableByteChannel that reads up thte given number of bytes.
     */
    private static ReadableByteChannel arbitrarySource(int size) throws Exception {
        ReadableByteChannel delegate = fileChannelSource(size);
        return new ReadableByteChannel() {
            @Override
            public int read(ByteBuffer bb) throws IOException {
                return delegate.read(bb);
            }
            @Override
            public boolean isOpen() {
                return delegate.isOpen();
            }
            @Override
            public void close() throws IOException {
                delegate.close();;
            }
        };
    }

    /**
     * Return a WritableByteChannel that is ready for writing.
     */
    private static WritableByteChannel arbitraryTarget() throws Exception {
        WritableByteChannel delegate = fileChannelTarget();
        return new WritableByteChannel() {
            @Override
            public int write(ByteBuffer bb) throws IOException {
                return delegate.write(bb);
            }
            @Override
            public boolean isOpen() {
                return delegate.isOpen();
            }
            @Override
            public void close() throws IOException {
                delegate.close();;
            }
        };
    }
}
