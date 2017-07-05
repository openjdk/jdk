/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @bug 4607272 6842687
 * @summary Unit test for AsynchronousChannelGroup
 */

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Tests that the completion handler is invoked by a thread with
 * the expected identity.
 */

public class Identity {
    static final Random rand = new Random();
    static final CountDownLatch done = new CountDownLatch(1);
    static final AtomicBoolean failed = new AtomicBoolean(false);

    static void fail(String msg) {
        failed.set(true);
        done.countDown();
        throw new RuntimeException(msg);
    }

    // thread-local identifies the thread
    private static final ThreadLocal<Integer> myGroup =
        new ThreadLocal<Integer>() {
            @Override protected Integer initialValue() {
                return Integer.valueOf(-1);
            }
        };

    // creates a ThreadFactory that constructs groups with the given identity
    static final ThreadFactory createThreadFactory(final int groupId) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        myGroup.set(groupId);
                        r.run();
                    }});
                t.setDaemon(true);
                return t;
            }
        };
    }

    public static void main(String[] args) throws Exception {
        // create listener to accept connections
        final AsynchronousServerSocketChannel listener =
            AsynchronousServerSocketChannel.open()
                .bind(new InetSocketAddress(0));
        listener.accept((Void)null, new CompletionHandler<AsynchronousSocketChannel,Void>() {
            public void completed(final AsynchronousSocketChannel ch, Void att) {
                listener.accept((Void)null, this);

                final ByteBuffer buf = ByteBuffer.allocate(100);
                ch.read(buf, (Void)null, new CompletionHandler<Integer,Void>() {
                    public void completed(Integer bytesRead, Void att) {
                        buf.clear();
                        ch.read(buf, (Void)null, this);
                    }
                    public void failed(Throwable exc, Void att) {
                    }
                });
            }
            public void failed(Throwable exc, Void att) {
            }
        });
        int port = ((InetSocketAddress)(listener.getLocalAddress())).getPort();
        SocketAddress sa = new InetSocketAddress(InetAddress.getLocalHost(), port);

        // create 3-10 channels, each in its own group
        final int groupCount = 3 + rand.nextInt(8);
        final AsynchronousSocketChannel[] channel = new AsynchronousSocketChannel[groupCount];
        for (int i=0; i<groupCount; i++) {
            ThreadFactory factory = createThreadFactory(i);
            AsynchronousChannelGroup group;
            if (rand.nextBoolean()) {
                int nThreads = 1 + rand.nextInt(10);
                group = AsynchronousChannelGroup.withFixedThreadPool(nThreads, factory);
            } else {
                ExecutorService pool = Executors.newCachedThreadPool(factory);
                group = AsynchronousChannelGroup.withCachedThreadPool(pool, rand.nextInt(5));
            }

            // create channel in group and connect it to the server
            AsynchronousSocketChannel ch = AsynchronousSocketChannel.open(group);
            ch.connect(sa).get();
            channel[i] = ch;
        }

        // randomly write to each channel, ensuring that the completion handler
        // is always invoked by a thread with the right identity.
        final AtomicInteger writeCount = new AtomicInteger(100);
        channel[0].write(getBuffer(), 0, new CompletionHandler<Integer,Integer>() {
            public void completed(Integer bytesWritten, Integer groupId) {
                if (bytesWritten != 1)
                    fail("Expected 1 byte to be written");
                if (!myGroup.get().equals(groupId))
                    fail("Handler invoked by thread with the wrong identity");
                if (writeCount.decrementAndGet() > 0) {
                    int id = rand.nextInt(groupCount);
                    channel[id].write(getBuffer(), id, this);
                } else {
                    done.countDown();
                }
            }
            public void failed(Throwable exc, Integer groupId) {
                fail(exc.getMessage());
            }
        });

        // wait until
        done.await();
        if (failed.get())
            throw new RuntimeException("Test failed - see log for details");
    }

    static ByteBuffer getBuffer() {
        ByteBuffer buf;
        if (rand.nextBoolean()) {
            buf = ByteBuffer.allocateDirect(1);
        } else {
            buf = ByteBuffer.allocate(1);
        }
        buf.put((byte)0);
        buf.flip();
        return buf;
    }
}
