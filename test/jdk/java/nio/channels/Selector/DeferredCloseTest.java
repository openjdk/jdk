/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Stream;

import jdk.internal.access.JavaNioAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.access.foreign.UnmapperProxy;
import jdk.internal.misc.VM;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8334719
 * @summary verifies that if a registered channel has in-progress operations, then
 *          the Selector during its deferred close implementation won't prematurely release
 *          the channel's resources
 *
 * @modules java.base/jdk.internal.access
 *          java.base/jdk.internal.access.foreign
 *          java.base/jdk.internal.misc
 *
 * @run junit/othervm/timeout=240 DeferredCloseTest
 */
public class DeferredCloseTest {

    private static final int NUM_ITERATIONS = 50;

    private static final JavaNioAccess originalJavaNioAccess;

    /*
     * Sets up a JavaNioAccess implementation which introduces an artificial
     * delay in the implementation of certain method calls. JavaNioAccess itself plays no other role
     * other than it being used in the implementation of APIs of several of the SelectableChannel
     * classes. We override it in this test to introduce delays in some method calls. It thus
     * allows us to introduce a race between the Selector and the SelectableChannel operations,
     * effectively increasing the efficacy of this test.
     *
     * We use a static block, instead of @BeforeAll, to set our custom test specific
     * JavaNioAccess very early and thus reduce the chances of JavaNioAccess already
     * being looked up and cached by SelectableChannel implementations.
     */
    static {
        originalJavaNioAccess = SharedSecrets.getJavaNioAccess();
        SharedSecrets.setJavaNioAccess(new DelayInjectingNIOAccess());
    }

    @AfterAll
    public static void afterAll() {
        // reset back to the original
        if (originalJavaNioAccess != null) {
            SharedSecrets.setJavaNioAccess(originalJavaNioAccess);
        }
    }

    private static Stream<Arguments> dcOperations() {
        return Stream.of(
                Arguments.of(
                        // repeatedly do DC.send() till there's a ClosedChannelException
                        "DC.send()", (Function<DatagramChannel, Void>) (dc) -> {
                            ByteBuffer bb = ByteBuffer.allocate(100);
                            try {
                                // We send to ourselves. Target, content and
                                // receipt of the Datagram isn't of importance
                                // in this test.
                                SocketAddress target = dc.getLocalAddress();
                                System.out.println("DC: " + dc + " sending to " + target);
                                while (true) {
                                    bb.clear();
                                    dc.send(bb, target);
                                }
                            } catch (ClosedChannelException _) {
                            } catch (IOException ioe) {
                                throw new UncheckedIOException(ioe);
                            }
                            return null;
                        }
                ),
                Arguments.of(
                        // repeatedly do DC.receive() till there's a ClosedChannelException
                        "DC.receive()", (Function<DatagramChannel, Void>) (dc) -> {
                            ByteBuffer rcvBB = ByteBuffer.allocate(100);
                            try {
                                System.out.println("DC: " + dc + " receiving datagrams");
                                while (true) {
                                    rcvBB.clear();
                                    dc.receive(rcvBB);
                                }
                            } catch (ClosedChannelException _) {
                            } catch (IOException ioe) {
                                throw new UncheckedIOException(ioe);
                            }
                            return null;
                        }
                )
        );
    }

    /**
     * Runs the test for DatagramChannel.
     *
     * @see #runTest(ExecutorService, SelectionKey, Callable)
     */
    @ParameterizedTest
    @MethodSource("dcOperations")
    public void testDatagramChannel(String opName, Function<DatagramChannel, Void> dcOperation)
            throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int i = 1; i <= NUM_ITERATIONS; i++) {
                System.out.format("%s DatagramChannel - %d of %d ...%n",
                        Instant.now(), i, NUM_ITERATIONS);
                try (Selector sel = Selector.open();
                     DatagramChannel dc = DatagramChannel.open()) {
                    // create a non-blocking bound DatagramChannel
                    dc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                    dc.configureBlocking(false);
                    // register the DatagramChannel with a selector
                    // (doesn't matter the interestOps)
                    final var key = dc.register(sel, SelectionKey.OP_READ);
                    runTest(executor, key, () -> dcOperation.apply(dc));
                }
            }
        }
    }


    private static Stream<Arguments> scOperations() {
        return Stream.of(
                Arguments.of(
                        // repeatedly do SC.write() till there's a ClosedChannelException
                        "SC.write()", (Function<SocketChannel, Void>) (sc) -> {
                            ByteBuffer bb = ByteBuffer.allocate(100);
                            try {
                                System.out.println("SC: " + sc + " writing");
                                while (true) {
                                    bb.clear();
                                    sc.write(bb);
                                }
                            } catch (ClosedChannelException _) {
                            } catch (IOException ioe) {
                                throw new UncheckedIOException(ioe);
                            }
                            return null;
                        }
                ),
                Arguments.of(
                        // repeatedly do SC.read() till there's a ClosedChannelException
                        "SC.read()", (Function<SocketChannel, Void>) (sc) -> {
                            ByteBuffer bb = ByteBuffer.allocate(100);
                            try {
                                System.out.println("SC: " + sc + " reading");
                                while (true) {
                                    bb.clear();
                                    sc.read(bb);
                                }
                            } catch (ClosedChannelException _) {
                            } catch (IOException ioe) {
                                throw new UncheckedIOException(ioe);
                            }
                            return null;
                        }
                )
        );
    }

    /**
     * Runs the test for SocketChannel
     *
     * @see #runTest(ExecutorService, SelectionKey, Callable)
     */
    @ParameterizedTest
    @MethodSource("scOperations")
    public void testSocketChannel(String opName, Function<SocketChannel, Void> scOperation)
            throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            for (int i = 1; i <= NUM_ITERATIONS; i++) {
                System.out.format("%s SocketChannel - %d of %d ...%n",
                        Instant.now(), i, NUM_ITERATIONS);
                try (Selector sel = Selector.open();
                     SocketChannel sc = SocketChannel.open()) {
                    // create and bind a SocketChannel
                    sc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                    // stay in blocking mode till the SocketChannel is connected
                    sc.configureBlocking(true);
                    Future<SocketChannel> acceptedChannel;
                    SocketChannel conn;
                    // create a remote server and connect to it
                    try (ServerSocketChannel server = ServerSocketChannel.open()) {
                        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                        SocketAddress remoteAddr = server.getLocalAddress();
                        acceptedChannel = executor.submit(new ConnAcceptor(server));
                        System.out.println("connecting to " + remoteAddr);
                        sc.connect(remoteAddr);
                        conn = acceptedChannel.get();
                    }
                    try (conn) {
                        // switch to non-blocking
                        sc.configureBlocking(false);
                        System.out.println("switched to non-blocking: " + sc);
                        // register the SocketChannel with a selector
                        // (doesn't matter the interestOps)
                        final var key = sc.register(sel, SelectionKey.OP_READ);
                        runTest(executor, key, () -> scOperation.apply(sc));
                    }
                }
            }
        }
    }

    /**
     * Runs the test for ServerSocketChannel
     *
     * @see #runTest(ExecutorService, SelectionKey, Callable)
     */
    @Test
    public void testServerSocketChannel() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int i = 1; i <= NUM_ITERATIONS; i++) {
                System.out.format("%s ServerSocketChannel - %d of %d ...%n",
                        Instant.now(), i, NUM_ITERATIONS);
                try (Selector sel = Selector.open();
                     ServerSocketChannel ssc = ServerSocketChannel.open()) {
                    // create and bind a ServerSocketChannel
                    ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                    ssc.configureBlocking(false);
                    // register the ServerSocketChannel with a selector
                    final var key = ssc.register(sel, SelectionKey.OP_ACCEPT);
                    runTest(executor, key, () -> {
                        // repeatedly do SSC.accept() till there's a ClosedChannelException
                        try {
                            while (true) {
                                ssc.accept();
                            }
                        } catch (ClosedChannelException _) {
                        }
                        return null;
                    });
                }
            }
        }
    }

    /**
     * Runs the test for SinkChannel
     *
     * @see #runTest(ExecutorService, SelectionKey, Callable)
     */
    @Test
    public void testSinkChannel() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int i = 1; i <= NUM_ITERATIONS; i++) {
                System.out.format("%s SinkChannel - %d of %d ...%n",
                        Instant.now(), i, NUM_ITERATIONS);
                Pipe pipe = Pipe.open();
                try (Selector sel = Selector.open();
                     Pipe.SinkChannel sink = pipe.sink()) {
                    sink.configureBlocking(false);
                    final var key = sink.register(sel, SelectionKey.OP_WRITE);
                    runTest(executor, key, () -> {
                        // repeatedly do SC.write() till there's a ClosedChannelException
                        ByteBuffer bb = ByteBuffer.allocate(100);
                        try {
                            while (true) {
                                bb.clear();
                                sink.write(bb);
                            }
                        } catch (ClosedChannelException _) {
                        }
                        return null;
                    });
                }
            }
        }
    }

    /**
     * Runs the test for SourceChannel
     *
     * @see #runTest(ExecutorService, SelectionKey, Callable)
     */
    @Test
    public void testSourceChannel() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int i = 1; i <= NUM_ITERATIONS; i++) {
                System.out.format("%s SourceChannel - %d of %d ...%n",
                        Instant.now(), i, NUM_ITERATIONS);
                Pipe pipe = Pipe.open();
                try (Selector sel = Selector.open();
                     Pipe.SourceChannel source = pipe.source()) {
                    source.configureBlocking(false);
                    final var key = source.register(sel, SelectionKey.OP_READ);
                    runTest(executor, key, () -> {
                        // repeatedly do SC.read() till there's a ClosedChannelException
                        ByteBuffer bb = ByteBuffer.allocate(100);
                        try {
                            while (true) {
                                bb.clear();
                                source.read(bb);
                            }
                        } catch (ClosedChannelException _) {
                        }
                        return null;
                    });
                }
            }
        }
    }

    /**
     * SelectableChannel implementations internally have a deferred close implementation. When a
     * channel is registered with a Selector and close() is invoked on the channel from a certain
     * thread, then the implementation of close() defers the actual close if the channel has
     * in-progress operations (for example, read/write/send/receive and such) in some other thread.
     * A subsequent operation through the Selector (like Selector.select()) then completes the
     * deferred close (waiting for any in-progress operations to complete). This test method
     * verifies that the deferred close implementation doesn't prematurely close and release
     * the resources used by the channel, while there are in-progress operations.
     * <p>
     * Launches 2 threads, T1 and T2. When T1 and T2 are in progress, this method closes the
     * channel that is registered with the Selector.
     * T1 is running the channelOperation (which keeps running operations on the channel).
     * T2 is running a task which keeps invoking Selector.select(), until the channel is closed.
     * When T2 notices that the channel is closed, it cancels the selectionKey and then
     * invokes one last Selector.select() operation to finish the deferred close of the channel.
     */
    private static void runTest(ExecutorService executor, SelectionKey selectionKey,
                                Callable<Void> channelOperation) throws Exception {

        SelectableChannel channel = selectionKey.channel();
        assertFalse(channel.isBlocking(), "channel isn't non-blocking: " + channel);
        selectionKey.selector().selectNow();
        CountDownLatch tasksStartedLatch = new CountDownLatch(2);
        // run the channel operations
        Future<?> channelOpResult = executor.submit(() -> {
            // notify that the task has started
            tasksStartedLatch.countDown();
            return channelOperation.call();
        });
        // run the Selector.select() task
        Future<?> selectorTaskResult = executor.submit(
                new SelectorTask(selectionKey, tasksStartedLatch));
        // await for the 2 tasks to start
        tasksStartedLatch.await();
        // just a few more milliseconds before we initiate the channel close
        Thread.sleep(100);
        // close the channel while it's still registered with the Selector,
        // so that the close is deferred by the channel implementations.
        System.out.println("closing channel: " + channel);
        assertTrue(channel.isRegistered(), "channel isn't registered: " + channel);
        channel.close();
        // wait for the operation on the channel and the selector task to complete
        channelOpResult.get();
        selectorTaskResult.get();
    }

    /*
     * Keeps invoking Selector.select() until the channel is closed, after which
     * it cancels the SelectionKey and does one last Selector.select() to finish
     * the deferred close.
     */
    private static final class SelectorTask implements Callable<Void> {
        private final SelectionKey selectionKey;
        private final CountDownLatch startedLatch;

        private SelectorTask(SelectionKey selectionKey, CountDownLatch startedLatch) {
            this.selectionKey = Objects.requireNonNull(selectionKey);
            this.startedLatch = startedLatch;
        }

        @Override
        public Void call() throws Exception {
            try {
                // notify that the task has started
                startedLatch.countDown();
                Selector selector = selectionKey.selector();
                SelectableChannel channel = selectionKey.channel();
                while (true) {
                    selector.select(10);
                    if (!channel.isOpen()) {
                        // the channel is (defer) closed, cancel the registration and then
                        // issue a select() so that the Selector finishes the deferred
                        // close of the channel.
                        System.out.println("channel: " + channel + " isn't open," +
                                " now cancelling key: " + selectionKey);
                        selectionKey.cancel();
                        System.out.println("initiating select after key cancelled: " + selectionKey);
                        selector.select(5);
                        break;
                    }
                }
            } catch (ClosedSelectorException _) {
            }
            return null;
        }
    }

    private static final class ConnAcceptor implements Callable<SocketChannel> {
        private final ServerSocketChannel serverSocketChannel;

        private ConnAcceptor(ServerSocketChannel serverSocketChannel) {
            this.serverSocketChannel = serverSocketChannel;
        }

        @Override
        public SocketChannel call() throws Exception {
            SocketChannel accepted = serverSocketChannel.accept();
            System.out.println("Accepted connection: " + accepted);
            return accepted;
        }
    }


    private static final class DelayInjectingNIOAccess implements JavaNioAccess {
        private final JavaNioAccess realJavaNioAccess = SharedSecrets.getJavaNioAccess();

        @Override
        public VM.BufferPool getDirectBufferPool() {
            return realJavaNioAccess.getDirectBufferPool();
        }

        @Override
        public ByteBuffer newDirectByteBuffer(long addr, int cap, Object obj,
                                              MemorySegment segment) {
            return realJavaNioAccess.newDirectByteBuffer(addr, cap, obj, segment);
        }

        @Override
        public ByteBuffer newMappedByteBuffer(UnmapperProxy unmapperProxy, long addr,
                                              int cap, Object obj, MemorySegment segment) {
            return realJavaNioAccess.newMappedByteBuffer(unmapperProxy, addr, cap, obj, segment);
        }

        @Override
        public ByteBuffer newHeapByteBuffer(byte[] hb, int offset, int capacity,
                                            MemorySegment segment) {
            return realJavaNioAccess.newHeapByteBuffer(hb, offset, capacity, segment);
        }

        @Override
        public Object getBufferBase(Buffer bb) {
            return realJavaNioAccess.getBufferBase(bb);
        }

        @Override
        public long getBufferAddress(Buffer buffer) {
            return realJavaNioAccess.getBufferAddress(buffer);
        }

        @Override
        public UnmapperProxy unmapper(Buffer buffer) {
            return realJavaNioAccess.unmapper(buffer);
        }

        @Override
        public MemorySegment bufferSegment(Buffer buffer) {
            return realJavaNioAccess.bufferSegment(buffer);
        }

        @Override
        public void acquireSession(Buffer buffer) {
            // intentionally introduce an artificial delay
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            realJavaNioAccess.acquireSession(buffer);
        }

        @Override
        public void releaseSession(Buffer buffer) {
            realJavaNioAccess.releaseSession(buffer);
        }

        @Override
        public boolean isThreadConfined(Buffer buffer) {
            return realJavaNioAccess.isThreadConfined(buffer);
        }

        @Override
        public boolean hasSession(Buffer buffer) {
            return realJavaNioAccess.hasSession(buffer);
        }

        @Override
        public void force(FileDescriptor fd, long address, boolean isSync,
                          long offset, long size) {
            realJavaNioAccess.force(fd, address, isSync, offset, size);
        }

        @Override
        public void load(long address, boolean isSync, long size) {
            realJavaNioAccess.load(address, isSync, size);
        }

        @Override
        public void unload(long address, boolean isSync, long size) {
            realJavaNioAccess.unload(address, isSync, size);
        }

        @Override
        public boolean isLoaded(long address, boolean isSync, long size) {
            return realJavaNioAccess.isLoaded(address, isSync, size);
        }

        @Override
        public void reserveMemory(long size, long cap) {
            realJavaNioAccess.reserveMemory(size, cap);
        }

        @Override
        public void unreserveMemory(long size, long cap) {
            realJavaNioAccess.unreserveMemory(size, cap);
        }

        @Override
        public int pageSize() {
            return realJavaNioAccess.pageSize();
        }
    }
}
