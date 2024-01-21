/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @modules java.base/sun.nio.ch
 * @key randomness
 * @run testng/othervm TestAsyncSocketChannels
 * @run testng/othervm -Dsun.nio.ch.disableSynchronousRead=true TestAsyncSocketChannels
 * @run testng/othervm -Dsun.nio.ch.disableSynchronousRead=false TestAsyncSocketChannels
 */

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.testng.annotations.*;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.testng.Assert.*;

/**
 * Tests consisting of buffer views with asynchronous NIO network channels.
 */
public class TestAsyncSocketChannels extends AbstractChannelsTest {

    static final Class<IOException> IOE = IOException.class;
    static final Class<ExecutionException> EE = ExecutionException.class;
    static final Class<IllegalStateException> ISE = IllegalStateException.class;

    /** Tests that confined sessions are not supported. */
    @Test(dataProvider = "confinedArenas")
    public void testWithConfined(Supplier<Arena> arenaSupplier)
        throws Throwable
    {
        try (var channel = AsynchronousSocketChannel.open();
             var server = AsynchronousServerSocketChannel.open();
             var connectedChannel = connectChannels(server, channel);
             var drop = arenaSupplier.get()) {
            Arena scope = drop;
            var segment = scope.allocate(10, 1);
            var bb = segment.asByteBuffer();
            var bba = new ByteBuffer[] { bb };
            List<ThrowingConsumer<TestHandler,?>> ioOps = List.of(
                    handler -> handler.propagateHandlerFromFuture(channel.write(bb)),
                    handler -> handler.propagateHandlerFromFuture(channel.read(bb)),
                    handler -> channel.write(bb, null, handler),
                    handler -> channel.read( bb, null, handler),
                    handler -> channel.write(bb , 0L, SECONDS, null, handler),
                    handler -> channel.read( bb,  0L, SECONDS, null, handler),
                    handler -> channel.write(bba, 0, bba.length, 0L, SECONDS, null, handler),
                    handler -> channel.read( bba, 0, bba.length, 0L, SECONDS, null, handler)
            );
            for (var ioOp : ioOps) {
                out.println("testAsyncWithConfined - op");
                var handler = new TestHandler();
                ioOp.accept(handler);
                handler.await()
                        .assertFailedWith(ISE)
                        .assertExceptionMessage("Confined session not supported");
            }
        }
    }

    /** Tests that I/O with a closed session throws a suitable exception. */
    @Test(dataProvider = "sharedArenasAndTimeouts")
    public void testIOWithClosedSharedSession(Supplier<Arena> arenaSupplier, int timeout)
        throws Exception
    {
        try (var channel = AsynchronousSocketChannel.open();
             var server = AsynchronousServerSocketChannel.open();
             var connectedChannel = connectChannels(server, channel)) {
            Arena drop = arenaSupplier.get();
            ByteBuffer bb = segmentBufferOfSize(drop, 64);
            ByteBuffer[] buffers = segmentBuffersOfSize(8, drop, 32);
            drop.close();
            {
                assertCauses(expectThrows(EE, () -> connectedChannel.read(bb).get()), IOE, ISE);
            }
            {
                var handler = new TestHandler<Integer>();
                connectedChannel.read(bb, null, handler);
                handler.await().assertFailedWith(ISE).assertExceptionMessage("Already closed");
            }
            {
                var handler = new TestHandler<Integer>();
                connectedChannel.read(bb, timeout, SECONDS, null, handler);
                handler.await().assertFailedWith(ISE).assertExceptionMessage("Already closed");
            }
            {
                var handler = new TestHandler<Long>();
                connectedChannel.read(buffers, 0, buffers.length, timeout, SECONDS, null, handler);
                handler.await().assertFailedWith(ISE).assertExceptionMessage("Already closed");
            }
            {
                assertCauses(expectThrows(EE, () -> connectedChannel.write(bb).get()), IOE, ISE);
            }
            {
                var handler = new TestHandler<Integer>();
                connectedChannel.write(bb, null, handler);
                handler.await().assertFailedWith(ISE).assertExceptionMessage("Already closed");
            }
            {
                var handler = new TestHandler<Integer>();
                connectedChannel.write(bb, timeout, SECONDS, null, handler);
                handler.await().assertFailedWith(ISE).assertExceptionMessage("Already closed");
            }
            {
                var handler = new TestHandler<Long>();
                connectedChannel.write(buffers, 0, buffers.length, timeout, SECONDS, null, handler);
                handler.await().assertFailedWith(ISE).assertExceptionMessage("Already closed");
            }
        }
    }

    /** Tests basic I/O operations work with views over implicit and shared sessions. */
    @Test(dataProvider = "sharedArenas")
    public void testBasicIOWithSupportedSession(Supplier<Arena> arenaSupplier)
        throws Exception
    {
        Arena drop;
        try (var asc1 = AsynchronousSocketChannel.open();
             var assc = AsynchronousServerSocketChannel.open();
             var asc2 = connectChannels(assc, asc1);
             var scp = drop = arenaSupplier.get()) {
            Arena scope1 = drop;
            MemorySegment segment1 = scope1.allocate(10, 1);
            Arena scope = drop;
            MemorySegment segment2 = scope.allocate(10, 1);
            for (int i = 0; i < 10; i++) {
                segment1.set(JAVA_BYTE, i, (byte) i);
            }
            {   // Future variants
                ByteBuffer bb1 = segment1.asByteBuffer();
                ByteBuffer bb2 = segment2.asByteBuffer();
                assertEquals((int)asc1.write(bb1).get(), 10);
                assertEquals((int)asc2.read(bb2).get(), 10);
                assertEquals(bb2.flip(), ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}));
            }
            {   // CompletionHandler variants
                ByteBuffer bb1 = segment1.asByteBuffer();
                ByteBuffer bb2 = segment2.asByteBuffer();
                var writeHandler = new TestHandler();
                asc1.write(new ByteBuffer[]{bb1}, 0, 1, 30L, SECONDS, null, writeHandler);
                writeHandler.await().assertCompleteWith(10L);
                var readHandler = new TestHandler();
                asc2.read(new ByteBuffer[]{bb2}, 0, 1, 30L, SECONDS, null, readHandler);
                readHandler.await().assertCompleteWith(10L);
                assertEquals(bb2.flip(), ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}));
            }
            {   // Gathering/Scattering variants
                var writeBuffers = mixedBuffersOfSize(16, drop, 32);
                var readBuffers = mixedBuffersOfSize(16, drop, 32);
                long expectedCount = remaining(writeBuffers);
                var writeHandler = new TestHandler();
                asc1.write(writeBuffers, 0, 16, 30L, SECONDS, null, writeHandler);
                writeHandler.await().assertCompleteWith(expectedCount);
                var readHandler = new TestHandler();
                asc2.read(readBuffers, 0, 16, 30L, SECONDS, null, readHandler);
                readHandler.await().assertCompleteWith(expectedCount);
                assertEquals(flip(readBuffers), clear(writeBuffers));
            }
        }
    }

    /** Tests that a session is not closeable when there is an outstanding read operation. */
    @Test(dataProvider = "sharedArenasAndTimeouts")
    public void testCloseWithOutstandingRead(Supplier<Arena> arenaSupplier, int timeout)
        throws Throwable
    {
        try (var asc1 = AsynchronousSocketChannel.open();
             var assc = AsynchronousServerSocketChannel.open();
             var asc2 = connectChannels(assc, asc1);
             var drop = arenaSupplier.get()) {
            Arena scope = drop;
            var segment = scope.allocate(10, 1);
            var bb = segment.asByteBuffer();
            var bba = new ByteBuffer[] { bb };
            List<ThrowingConsumer<TestHandler,?>> readOps = List.of(
                    handler -> handler.propagateHandlerFromFuture(asc1.read(bb)),
                    handler -> asc1.read(bb, null, handler),
                    handler -> asc1.read(bb,  timeout, SECONDS, null, handler),
                    handler -> asc1.read(bba, 0, bba.length, timeout, SECONDS, null, handler)
            );
            for (var ioOp : readOps) {
                out.println("testCloseWithOutstandingRead - op");
                var handler = new TestHandler<Long>();
                ioOp.accept(handler);
                assertFalse(handler.isDone());
                assertTrue(drop.scope().isAlive());

                // write to allow the blocking read complete, which will
                // in turn unlock the session and allow it to be closed.
                asc2.write(ByteBuffer.wrap(new byte[] { 0x01 })).get();
                handler.await().assertCompleteWith(1L);
                assertTrue(drop.scope().isAlive());
            }
        }
    }

    /** Tests that a session is not closeable when there is an outstanding write operation. */
    // Note: limited scenarios are checked, given the 5 sec sleep!
    @Test(dataProvider = "sharedArenasAndTimeouts")
    public void testCloseWithOutstandingWrite(Supplier<Arena> arenaSupplier, int timeout)
         throws Throwable
    {
        try (var asc1 = AsynchronousSocketChannel.open();
             var assc = AsynchronousServerSocketChannel.open();
             var asc2 = connectChannels(assc, asc1);
             var drop = arenaSupplier.get()) {

            // number of bytes written
            final AtomicLong bytesWritten = new AtomicLong(0);
            // set to true to signal that no more buffers should be written
            final AtomicBoolean continueWriting = new AtomicBoolean(true);
            final AtomicInteger outstandingWriteOps = new AtomicInteger(0);

            // write until socket buffer is full so as to create the conditions
            // for when a write does not complete immediately
            var bba = segmentBuffersOfSize(32, drop, 128);
            TestHandler<Long> handler;
            outstandingWriteOps.getAndIncrement();
            asc1.write(bba, 0, bba.length, timeout, SECONDS, null,
                    (handler = new TestHandler<>() {
                        public void completed(Long result, Void att) {
                            super.completed(result, att);
                            bytesWritten.addAndGet(result);
                            if (continueWriting.get()) {
                                var bba = segmentBuffersOfSize(32, drop, 128);
                                outstandingWriteOps.getAndIncrement();
                                asc1.write(bba, 0, bba.length, timeout, SECONDS, null, this);
                            }
                            outstandingWriteOps.getAndDecrement();
                        }
                    }));
            // give time for socket buffer to fill up.
            awaitNoFurtherWrites(bytesWritten);

            assertTrue(drop.scope().isAlive());

            // signal handler to stop further writing
            continueWriting.set(false);

            // read to allow the outstanding write complete, which will
            // in turn unlock the session and allow it to be closed.
            readNBytes(asc2, bytesWritten.get());
            assertTrue(drop.scope().isAlive());
            awaitOutstandingWrites(outstandingWriteOps);
            handler.await();
        }
    }

    /** Waits for outstandingWriteOps to complete (become 0). */
    static void awaitOutstandingWrites(AtomicInteger outstandingWriteOps) {
        boolean initial = true;
        while (outstandingWriteOps.get() > 0 )  {
            if (initial) {
                out.print("awaiting outstanding writes");
                initial = false;
            }
            out.print(".");
            Thread.onSpinWait();
        }
        out.println("outstanding writes: " + outstandingWriteOps.get());
    }

    /** Waits, at most 20secs, for bytesWritten to stabilize. */
    static void awaitNoFurtherWrites(AtomicLong bytesWritten) throws Exception {
        int i;
        long prevN = 0;
        for (i=0; i<10; i++) {
            long n = bytesWritten.get();
            Thread.sleep(2 * 1000);
            if (bytesWritten.get() == n && prevN == n) {
                break;
            }
            prevN = n;
        }
        out.println("awaitNoFurtherWrites: i=" + i +" , bytesWritten=" + bytesWritten.get());
    }

    /** Completion handler that exposes conveniences to assert results. */
    static class TestHandler<V extends Number> implements CompletionHandler<V, Void> {
        volatile V result;
        volatile Throwable throwable;
        final CountDownLatch latch = new CountDownLatch(1);

        /** Starts a thread that complete the handled with the Future result. */
        TestHandler propagateHandlerFromFuture(Future<Integer> future) {
            Runnable runnable = () -> {
                try {
                    this.completed((V)future.get(), null);
                } catch (Throwable t) {
                    // assert and unwrap exception added by Future
                    assertTrue(ExecutionException.class.isInstance(t));
                    t = t.getCause();
                    assertTrue(IOException.class.isInstance(t));
                    t = t.getCause();
                    this.failed(t, null);
                }
            };
            Thread t = new Thread(runnable);
            t.start();
            return this;
        }

        @Override
        public void completed(V result, Void att) {
            assert result.longValue() >= 0;
            this.result = result;
            latch.countDown();
        }
        @Override
        public void failed(Throwable exc, Void att){
            this.throwable = tolerateIOEOnWindows(exc);
            latch.countDown();
        }

        TestHandler await() throws InterruptedException{
            latch.await();
            return this;
        }

        TestHandler assertCompleteWith(V value) {
            assertEquals(result.longValue(), value.longValue());
            assertEquals(throwable, null);
            return this;
        }

        TestHandler assertFailedWith(Class<? extends Exception> expectedException) {
            assertTrue(expectedException.isInstance(throwable),
                       "Expected type:%s, got:%s".formatted(expectedException, throwable) );
            assertEquals(result, null, "Unexpected result: " + result);
            return this;
        }

        TestHandler assertExceptionMessage(String expectedMessage) {
            assertEquals(throwable.getMessage(), expectedMessage);
            return this;
        }

        boolean isDone() {
            return latch.getCount() == 0;
        }
    }

    static AsynchronousSocketChannel connectChannels(AsynchronousServerSocketChannel assc,
                                                     AsynchronousSocketChannel asc)
        throws Exception
    {
        setBufferSized(assc, asc);
        assc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        asc.connect(assc.getLocalAddress()).get();
        return assc.accept().get();
    }

    /** Sets the send/receive buffer sizes in an attempt/hint to limit the
     * accepted/connected socket buffer sizes. Actual buffer sizes in use will
     * likely be larger due to TCP auto-tuning, but the hint typically reduces
     * the overall scaled sizes. This is primarily to stabilize outstanding
     * write operations.
     */
    static void setBufferSized(AsynchronousServerSocketChannel assc,
                               AsynchronousSocketChannel asc)
        throws Exception
    {
        assc.setOption(StandardSocketOptions.SO_RCVBUF, 32 * 1024);
        asc.setOption(StandardSocketOptions.SO_SNDBUF, 32 * 1024);
        asc.setOption(StandardSocketOptions.SO_RCVBUF, 32 * 1024);
    }

    /** Tolerate the additional level of IOException wrapping of unchecked exceptions
     * On Windows, when completing the completion handler with a failure. */
    static Throwable tolerateIOEOnWindows(Throwable t) {
        if (System.getProperty("os.name").startsWith("Windows")) {
            if (t instanceof IOException)
                return t.getCause();
        }
        return t;
    }

    static void readNBytes(AsynchronousSocketChannel channel, long len)
        throws Exception
    {
        var buf = ByteBuffer.allocateDirect(4096);
        long total = 0L;
        do {
            int n = channel.read(buf).get();
            assertTrue(n > 0, "got:" + n);
            buf.clear();
            total += n;
        } while (total < len);
    }
}
