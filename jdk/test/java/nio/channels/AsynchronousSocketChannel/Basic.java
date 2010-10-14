/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4607272 6842687
 * @summary Unit test for AsynchronousSocketChannel
 * @run main/timeout=600 Basic
 */

import java.nio.ByteBuffer;
import java.nio.channels.*;
import static java.net.StandardSocketOption.*;
import java.net.*;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.IOException;

public class Basic {
    static final Random rand = new Random();

    public static void main(String[] args) throws Exception {
        testBind();
        testSocketOptions();
        testConnect();
        testCloseWhenPending();
        testCancel();
        testRead1();
        testRead2();
        testRead3();
        testWrite1();
        testWrite2();
        testTimeout();
        testShutdown();
    }

    static class Server {
        private final ServerSocketChannel ssc;
        private final InetSocketAddress address;

        Server() throws IOException {
            ssc = ServerSocketChannel.open().bind(new InetSocketAddress(0));

            InetAddress lh = InetAddress.getLocalHost();
            int port = ((InetSocketAddress)(ssc.getLocalAddress())).getPort();
            address = new InetSocketAddress(lh, port);
        }

        InetSocketAddress address() {
            return address;
        }

        SocketChannel accept() throws IOException {
            return ssc.accept();
        }

        void close() {
            try {
                ssc.close();
            } catch (IOException ignore) { }
        }

    }

    static void testBind() throws Exception {
        System.out.println("-- bind --");

        AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
        if (ch.getLocalAddress() != null)
            throw new RuntimeException("Local address should be 'null'");
        ch.bind(new InetSocketAddress(0));

        // check local address after binding
        InetSocketAddress local = (InetSocketAddress)ch.getLocalAddress();
        if (local.getPort() == 0)
            throw new RuntimeException("Unexpected port");
        if (!local.getAddress().isAnyLocalAddress())
            throw new RuntimeException("Not bound to a wildcard address");

        // try to re-bind
        try {
            ch.bind(new InetSocketAddress(0));
            throw new RuntimeException("AlreadyBoundException expected");
        } catch (AlreadyBoundException x) {
        }
        ch.close();

        // check ClosedChannelException
        ch = AsynchronousSocketChannel.open();
        ch.close();
        try {
            ch.bind(new InetSocketAddress(0));
            throw new RuntimeException("ClosedChannelException  expected");
        } catch (ClosedChannelException  x) {
        }
    }

    static void testSocketOptions() throws Exception {
        System.out.println("-- socket options --");

        AsynchronousSocketChannel ch = AsynchronousSocketChannel.open()
            .setOption(SO_RCVBUF, 128*1024)
            .setOption(SO_SNDBUF, 128*1024)
            .setOption(SO_REUSEADDR, true);

        // check SO_SNDBUF/SO_RCVBUF limits
        int before, after;
        before = ch.getOption(SO_SNDBUF);
        after = ch.setOption(SO_SNDBUF, Integer.MAX_VALUE).getOption(SO_SNDBUF);
        if (after < before)
            throw new RuntimeException("setOption caused SO_SNDBUF to decrease");
        before = ch.getOption(SO_RCVBUF);
        after = ch.setOption(SO_RCVBUF, Integer.MAX_VALUE).getOption(SO_RCVBUF);
        if (after < before)
            throw new RuntimeException("setOption caused SO_RCVBUF to decrease");

        ch.bind(new InetSocketAddress(0));

        // default values
        if ((Boolean)ch.getOption(SO_KEEPALIVE))
            throw new RuntimeException("Default of SO_KEEPALIVE should be 'false'");
        if ((Boolean)ch.getOption(TCP_NODELAY))
            throw new RuntimeException("Default of TCP_NODELAY should be 'false'");

        // set and check
        if (!(Boolean)ch.setOption(SO_KEEPALIVE, true).getOption(SO_KEEPALIVE))
            throw new RuntimeException("SO_KEEPALIVE did not change");
        if (!(Boolean)ch.setOption(TCP_NODELAY, true).getOption(TCP_NODELAY))
            throw new RuntimeException("SO_KEEPALIVE did not change");

        // read others (can't check as actual value is implementation dependent)
        ch.getOption(SO_RCVBUF);
        ch.getOption(SO_SNDBUF);

        ch.close();
    }

    static void testConnect() throws Exception {
        System.out.println("-- connect --");

        Server server = new Server();
        AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
        ch.connect(server.address()).get();

        // check local address
        if (ch.getLocalAddress() == null)
            throw new RuntimeException("Not bound to local address");

        // check remote address
        InetSocketAddress remote = (InetSocketAddress)ch.getRemoteAddress();
        if (remote.getPort() != server.address().getPort())
            throw new RuntimeException("Connected to unexpected port");
        if (!remote.getAddress().equals(server.address().getAddress()))
            throw new RuntimeException("Connected to unexpected address");

        // try to connect again
        try {
            ch.connect(server.address()).get();
            throw new RuntimeException("AlreadyConnectedException expected");
        } catch (AlreadyConnectedException x) {
        }
        ch.close();

        // check that connect fails with ClosedChannelException)
        ch = AsynchronousSocketChannel.open();
        ch.close();
        try {
            ch.connect(server.address()).get();
            throw new RuntimeException("ExecutionException expected");
        } catch (ExecutionException x) {
            if (!(x.getCause() instanceof ClosedChannelException))
                throw new RuntimeException("Cause of ClosedChannelException expected");
        }
        final AtomicReference<Throwable> connectException =
            new AtomicReference<Throwable>();
        ch.connect(server.address(), (Void)null, new CompletionHandler<Void,Void>() {
            public void completed(Void result, Void att) {
            }
            public void failed(Throwable exc, Void att) {
                connectException.set(exc);
            }
        });
        while (connectException.get() == null) {
            Thread.sleep(100);
        }
        if (!(connectException.get() instanceof ClosedChannelException))
            throw new RuntimeException("ClosedChannelException expected");

        System.out.println("-- connect to non-existent host --");

        // test that failure to connect closes the channel
        ch = AsynchronousSocketChannel.open();
        try {
            ch.connect(genSocketAddress()).get();
        } catch (ExecutionException x) {
            // failed to establish connection
            if (ch.isOpen())
                throw new RuntimeException("Channel should be closed");
        } finally {
            ch.close();
        }

        server.close();
    }

    static void testCloseWhenPending() throws Exception {
        System.out.println("-- asynchronous close when connecting --");

        AsynchronousSocketChannel ch;

        // asynchronous close while connecting
        ch = AsynchronousSocketChannel.open();
        Future<Void> connectResult = ch.connect(genSocketAddress());

        // give time to initiate the connect (SYN)
        Thread.sleep(50);

        // close
        ch.close();

        // check that exception is thrown in timely manner
        try {
            connectResult.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException x) {
            throw new RuntimeException("AsynchronousCloseException not thrown");
        } catch (ExecutionException x) {
            // expected
        }

        System.out.println("-- asynchronous close when reading --");

        Server server = new Server();
        ch = AsynchronousSocketChannel.open();
        ch.connect(server.address()).get();

        ByteBuffer dst = ByteBuffer.allocateDirect(100);
        Future<Integer> result = ch.read(dst);

        // attempt a second read - should fail with ReadPendingException
        ByteBuffer buf = ByteBuffer.allocateDirect(100);
        try {
            ch.read(buf);
            throw new RuntimeException("ReadPendingException expected");
        } catch (ReadPendingException x) {
        }

        // close channel (should cause initial read to complete)
        ch.close();

        // check that AsynchronousCloseException is thrown
        try {
            result.get();
            throw new RuntimeException("Should not read");
        } catch (ExecutionException x) {
            if (!(x.getCause() instanceof AsynchronousCloseException))
                throw new RuntimeException(x);
        }

        System.out.println("-- asynchronous close when writing --");

        ch = AsynchronousSocketChannel.open();
        ch.connect(server.address()).get();

        final AtomicReference<Throwable> writeException =
            new AtomicReference<Throwable>();

        // write bytes to fill socket buffer
        ch.write(genBuffer(), ch, new CompletionHandler<Integer,AsynchronousSocketChannel>() {
            public void completed(Integer result, AsynchronousSocketChannel ch) {
                ch.write(genBuffer(), ch, this);
            }
            public void failed(Throwable x, AsynchronousSocketChannel ch) {
                writeException.set(x);
            }
        });

        // give time for socket buffer to fill up.
        Thread.sleep(5*1000);

        //  attempt a concurrent write - should fail with WritePendingException
        try {
            ch.write(genBuffer());
            throw new RuntimeException("WritePendingException expected");
        } catch (WritePendingException x) {
        }

        // close channel - should cause initial write to complete
        ch.close();

        // wait for exception
        while (writeException.get() == null) {
            Thread.sleep(100);
        }
        if (!(writeException.get() instanceof AsynchronousCloseException))
            throw new RuntimeException("AsynchronousCloseException expected");

        server.close();
    }

    static void testCancel() throws Exception {
        System.out.println("-- cancel --");

        Server server = new Server();

        for (int i=0; i<2; i++) {
            boolean mayInterruptIfRunning = (i == 0) ? false : true;

            // establish loopback connection
            AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
            ch.connect(server.address()).get();
            SocketChannel peer = server.accept();

            // start read operation
            ByteBuffer buf = ByteBuffer.allocate(1);
            Future<Integer> res = ch.read(buf);

            // cancel operation
            boolean cancelled = res.cancel(mayInterruptIfRunning);

            // check post-conditions
            if (!res.isDone())
                throw new RuntimeException("isDone should return true");
            if (res.isCancelled() != cancelled)
                throw new RuntimeException("isCancelled not consistent");
            try {
                res.get();
                throw new RuntimeException("CancellationException expected");
            } catch (CancellationException x) {
            }
            try {
                res.get(1, TimeUnit.SECONDS);
                throw new RuntimeException("CancellationException expected");
            } catch (CancellationException x) {
            }

            // check that the cancel doesn't impact writing to the channel
            if (!mayInterruptIfRunning) {
                buf = ByteBuffer.wrap("a".getBytes());
                ch.write(buf).get();
            }

            ch.close();
            peer.close();
        }

        server.close();
    }

    static void testRead1() throws Exception {
        System.out.println("-- read (1) --");

        Server server = new Server();
        final AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
        ch.connect(server.address()).get();

        // read with 0 bytes remaining should complete immediately
        ByteBuffer buf = ByteBuffer.allocate(1);
        buf.put((byte)0);
        int n = ch.read(buf).get();
        if (n != 0)
            throw new RuntimeException("0 expected");

        // write bytes and close connection
        SocketChannel sc = server.accept();
        ByteBuffer src = genBuffer();
        sc.setOption(StandardSocketOption.SO_SNDBUF, src.remaining());
        while (src.hasRemaining())
            sc.write(src);
        sc.close();

        // reads should complete immediately
        final ByteBuffer dst = ByteBuffer.allocateDirect(src.capacity() + 100);
        final CountDownLatch latch = new CountDownLatch(1);
        ch.read(dst, (Void)null, new CompletionHandler<Integer,Void>() {
            public void completed(Integer result, Void att) {
                int n = result;
                if (n > 0) {
                    ch.read(dst, (Void)null, this);
                } else {
                    latch.countDown();
                }
            }
            public void failed(Throwable exc, Void att) {
            }
        });

        latch.await();

        // check buffers
        src.flip();
        dst.flip();
        if (!src.equals(dst)) {
            throw new RuntimeException("Contents differ");
        }

        // close channel
        ch.close();

        // check read fails with ClosedChannelException
        try {
            ch.read(dst).get();
            throw new RuntimeException("ExecutionException expected");
        } catch (ExecutionException x) {
            if (!(x.getCause() instanceof ClosedChannelException))
                throw new RuntimeException("Cause of ClosedChannelException expected");
        }

        server.close();
    }

    static void testRead2() throws Exception {
        System.out.println("-- read (2) --");

        Server server = new Server();

        final AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
        ch.connect(server.address()).get();
        SocketChannel sc = server.accept();

        ByteBuffer src = genBuffer();

        // read until the buffer is full
        final ByteBuffer dst = ByteBuffer.allocateDirect(src.capacity());
        final CountDownLatch latch = new CountDownLatch(1);
        ch.read(dst, (Void)null, new CompletionHandler<Integer,Void>() {
            public void completed(Integer result, Void att) {
                if (dst.hasRemaining()) {
                    ch.read(dst, (Void)null, this);
                } else {
                    latch.countDown();
                }
            }
            public void failed(Throwable exc, Void att) {
            }
        });

        // trickle the writing
        do {
            int rem = src.remaining();
            int size = (rem <= 100) ? rem : 50 + rand.nextInt(rem - 100);
            ByteBuffer buf = ByteBuffer.allocate(size);
            for (int i=0; i<size; i++)
                buf.put(src.get());
            buf.flip();
            Thread.sleep(50 + rand.nextInt(1500));
            while (buf.hasRemaining())
                sc.write(buf);
        } while (src.hasRemaining());

        // wait until ascynrhonous reading has completed
        latch.await();

        // check buffers
        src.flip();
        dst.flip();
        if (!src.equals(dst)) {
           throw new RuntimeException("Contents differ");
        }

        sc.close();
        ch.close();
        server.close();
    }

    // exercise scattering read
    static void testRead3() throws Exception {
        System.out.println("-- read (3) --");

        Server server = new Server();
        final AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
        ch.connect(server.address()).get();
        SocketChannel sc = server.accept();

        ByteBuffer[] dsts = new ByteBuffer[3];
        for (int i=0; i<dsts.length; i++) {
            dsts[i] = ByteBuffer.allocateDirect(100);
        }

        // scattering read that completes ascynhronously
        final CountDownLatch l1 = new CountDownLatch(1);
        ch.read(dsts, 0, dsts.length, 0L, TimeUnit.SECONDS, (Void)null,
            new CompletionHandler<Long,Void>() {
                public void completed(Long result, Void att) {
                    long n = result;
                    if (n <= 0)
                        throw new RuntimeException("No bytes read");
                    l1.countDown();
                }
                public void failed(Throwable exc, Void att) {
                }
        });

        // write some bytes
        sc.write(genBuffer());

        // read should now complete
        l1.await();

        // write more bytes
        sc.write(genBuffer());

        // read should complete immediately
        for (int i=0; i<dsts.length; i++) {
            dsts[i].rewind();
        }

        final CountDownLatch l2 = new CountDownLatch(1);
        ch.read(dsts, 0, dsts.length, 0L, TimeUnit.SECONDS, (Void)null,
            new CompletionHandler<Long,Void>() {
                public void completed(Long result, Void att) {
                    long n = result;
                    if (n <= 0)
                        throw new RuntimeException("No bytes read");
                    l2.countDown();
                }
                public void failed(Throwable exc, Void att) {
                }
        });
        l2.await();

        ch.close();
        sc.close();
        server.close();
    }

    static void testWrite1() throws Exception {
        System.out.println("-- write (1) --");

        Server server = new Server();
        final AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
        ch.connect(server.address()).get();
        SocketChannel sc = server.accept();

        // write with 0 bytes remaining should complete immediately
        ByteBuffer buf = ByteBuffer.allocate(1);
        buf.put((byte)0);
        int n = ch.write(buf).get();
        if (n != 0)
            throw new RuntimeException("0 expected");

        // write all bytes and close connection when done
        final ByteBuffer src = genBuffer();
        ch.write(src, (Void)null, new CompletionHandler<Integer,Void>() {
            public void completed(Integer result, Void att) {
                if (src.hasRemaining()) {
                    ch.write(src, (Void)null, this);
                } else {
                    try {
                        ch.close();
                    } catch (IOException ignore) { }
                }
            }
            public void failed(Throwable exc, Void att) {
            }
        });

        // read to EOF or buffer full
        ByteBuffer dst = ByteBuffer.allocateDirect(src.capacity() + 100);
        do {
            n = sc.read(dst);
        } while (n > 0);
        sc.close();

        // check buffers
        src.flip();
        dst.flip();
        if (!src.equals(dst)) {
            throw new RuntimeException("Contents differ");
        }

        // check write fails with ClosedChannelException
        try {
            ch.read(dst).get();
            throw new RuntimeException("ExecutionException expected");
        } catch (ExecutionException x) {
            if (!(x.getCause() instanceof ClosedChannelException))
                throw new RuntimeException("Cause of ClosedChannelException expected");
        }

        server.close();
    }

    // exercise gathering write
    static void testWrite2() throws Exception {
        System.out.println("-- write (2) --");

        Server server = new Server();
        final AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
        ch.connect(server.address()).get();
        SocketChannel sc = server.accept();

        // number of bytes written
        final AtomicLong bytesWritten = new AtomicLong(0);

        // write buffers (should complete immediately)
        ByteBuffer[] srcs = genBuffers(1);
        final CountDownLatch l1 = new CountDownLatch(1);
        ch.write(srcs, 0, srcs.length, 0L, TimeUnit.SECONDS, (Void)null,
            new CompletionHandler<Long,Void>() {
                public void completed(Long result, Void att) {
                    long n = result;
                    if (n <= 0)
                        throw new RuntimeException("No bytes read");
                    bytesWritten.addAndGet(n);
                    l1.countDown();
                }
                public void failed(Throwable exc, Void att) {
                }
        });
        l1.await();

        // set to true to signal that no more buffers should be written
        final AtomicBoolean continueWriting = new AtomicBoolean(true);

        // write until socket buffer is full so as to create the conditions
        // for when a write does not complete immediately
        srcs = genBuffers(1);
        ch.write(srcs, 0, srcs.length, 0L, TimeUnit.SECONDS, (Void)null,
            new CompletionHandler<Long,Void>() {
                public void completed(Long result, Void att) {
                    long n = result;
                    if (n <= 0)
                        throw new RuntimeException("No bytes written");
                    bytesWritten.addAndGet(n);
                    if (continueWriting.get()) {
                        ByteBuffer[] srcs = genBuffers(8);
                        ch.write(srcs, 0, srcs.length, 0L, TimeUnit.SECONDS,
                            (Void)null, this);
                    }
                }
                public void failed(Throwable exc, Void att) {
                }
        });

        // give time for socket buffer to fill up.
        Thread.sleep(5*1000);

        // signal handler to stop further writing
        continueWriting.set(false);

        // read until done
        ByteBuffer buf = ByteBuffer.allocateDirect(4096);
        long total = 0L;
        do {
            int n = sc.read(buf);
            if (n <= 0)
                throw new RuntimeException("No bytes read");
            buf.rewind();
            total += n;
        } while (total < bytesWritten.get());

        ch.close();
        sc.close();
        server.close();
    }

    static void testShutdown() throws Exception {
        System.out.println("-- shutdown--");

        Server server = new Server();
        AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
        ch.connect(server.address()).get();
        SocketChannel sc = server.accept();

        ByteBuffer buf = ByteBuffer.allocateDirect(1000);
        int n;

        // check read
        ch.shutdownInput();
        n = ch.read(buf).get();
        if (n != -1)
            throw new RuntimeException("-1 expected");
        // check full with full buffer
        buf.put(new byte[100]);
        n = ch.read(buf).get();
        if (n != -1)
            throw new RuntimeException("-1 expected");

        // check write
        ch.shutdownOutput();
        try {
            ch.write(buf).get();
            throw new RuntimeException("ClosedChannelException expected");
        } catch (ExecutionException x) {
            if (!(x.getCause() instanceof ClosedChannelException))
                throw new RuntimeException("ClosedChannelException expected");
        }

        sc.close();
        ch.close();
        server.close();
    }

    static void testTimeout() throws Exception {
        Server server = new Server();
        AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
        ch.connect(server.address()).get();

        System.out.println("-- timeout when reading --");

        ByteBuffer dst = ByteBuffer.allocate(512);

        final AtomicReference<Throwable> readException = new AtomicReference<Throwable>();

        // this read should timeout
        ch.read(dst, 3, TimeUnit.SECONDS, (Void)null,
            new CompletionHandler<Integer,Void>()
        {
            public void completed(Integer result, Void att) {
                throw new RuntimeException("Should not complete");
            }
            public void failed(Throwable exc, Void att) {
                readException.set(exc);
            }
        });
        // wait for exception
        while (readException.get() == null) {
            Thread.sleep(100);
        }
        if (!(readException.get() instanceof InterruptedByTimeoutException))
            throw new RuntimeException("InterruptedByTimeoutException expected");

        // after a timeout then further reading should throw unspecified runtime exception
        boolean exceptionThrown = false;
        try {
            ch.read(dst);
        } catch (RuntimeException x) {
            exceptionThrown = true;
        }
        if (!exceptionThrown)
            throw new RuntimeException("RuntimeException expected after timeout.");


        System.out.println("-- timeout when writing --");

        final AtomicReference<Throwable> writeException = new AtomicReference<Throwable>();

        final long timeout = 5;
        final TimeUnit unit = TimeUnit.SECONDS;

        // write bytes to fill socket buffer
        ch.write(genBuffer(), timeout, unit, ch,
            new CompletionHandler<Integer,AsynchronousSocketChannel>()
        {
            public void completed(Integer result, AsynchronousSocketChannel ch) {
                ch.write(genBuffer(), timeout, unit, ch, this);
            }
            public void failed(Throwable exc, AsynchronousSocketChannel ch) {
                writeException.set(exc);
            }
        });

        // wait for exception
        while (writeException.get() == null) {
            Thread.sleep(100);
        }
        if (!(writeException.get() instanceof InterruptedByTimeoutException))
            throw new RuntimeException("InterruptedByTimeoutException expected");

        // after a timeout then further writing should throw unspecified runtime exception
        exceptionThrown = false;
        try {
            ch.write(genBuffer());
        } catch (RuntimeException x) {
            exceptionThrown = true;
        }
        if (!exceptionThrown)
            throw new RuntimeException("RuntimeException expected after timeout.");

        ch.close();
        server.close();
    }

    // returns ByteBuffer with random bytes
    static ByteBuffer genBuffer() {
        int size = 1024 + rand.nextInt(16000);
        byte[] buf = new byte[size];
        rand.nextBytes(buf);
        boolean useDirect = rand.nextBoolean();
        if (useDirect) {
            ByteBuffer bb = ByteBuffer.allocateDirect(buf.length);
            bb.put(buf);
            bb.flip();
            return bb;
        } else {
            return ByteBuffer.wrap(buf);
        }
    }

    // return ByteBuffer[] with random bytes
    static ByteBuffer[] genBuffers(int max) {
        int len = 1;
        if (max > 1)
            len += rand.nextInt(max);
        ByteBuffer[] bufs = new ByteBuffer[len];
        for (int i=0; i<len; i++)
            bufs[i] = genBuffer();
        return bufs;
    }

    // return random SocketAddress
    static SocketAddress genSocketAddress() {
        StringBuilder sb = new StringBuilder("10.");
        sb.append(rand.nextInt(256));
        sb.append('.');
        sb.append(rand.nextInt(256));
        sb.append('.');
        sb.append(rand.nextInt(256));
        InetAddress rh;
        try {
            rh = InetAddress.getByName(sb.toString());
        } catch (UnknownHostException x) {
            throw new InternalError("Should not happen");
        }
        return new InetSocketAddress(rh, rand.nextInt(65535)+1);
    }
}
