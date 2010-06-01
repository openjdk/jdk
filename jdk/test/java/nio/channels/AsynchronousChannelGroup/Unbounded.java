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
 * @summary Unit test for AsynchronousChannelGroup
 */

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.*;
import java.util.concurrent.*;
import java.io.IOException;

public class Unbounded {
    // number of concurrent completion handlers
    static final int CONCURRENCY_COUNT = 256;

    public static void main(String[] args) throws Exception {
        // all accepted connections are added to a queue
        final ArrayBlockingQueue<AsynchronousSocketChannel> queue =
            new ArrayBlockingQueue<AsynchronousSocketChannel>(CONCURRENCY_COUNT);

        // create listener to accept connections
        final AsynchronousServerSocketChannel listener =
            AsynchronousServerSocketChannel.open()
                .bind(new InetSocketAddress(0));
        listener.accept((Void)null, new CompletionHandler<AsynchronousSocketChannel,Void>() {
            public void completed(AsynchronousSocketChannel ch, Void att) {
                queue.add(ch);
                listener.accept((Void)null, this);
            }
            public void failed(Throwable exc, Void att) {
            }
        });
        System.out.println("Listener created.");

        // establish lots of connections
        int port = ((InetSocketAddress)(listener.getLocalAddress())).getPort();
        SocketAddress sa = new InetSocketAddress(InetAddress.getLocalHost(), port);
        AsynchronousSocketChannel[] channels =
            new AsynchronousSocketChannel[CONCURRENCY_COUNT];
        for (int i=0; i<CONCURRENCY_COUNT; i++) {
            int attempts = 0;
            for (;;) {
                try {
                    channels[i] = AsynchronousSocketChannel.open();
                    channels[i].connect(sa).get();
                    break;
                } catch (IOException x) {
                    // probably resource issue so back off and retry
                    if (++attempts >= 3)
                        throw x;
                    Thread.sleep(50);
                }
            }
        }
        System.out.println("All connection established.");

        // the barrier where all threads (plus the main thread) wait
        final CyclicBarrier barrier = new CyclicBarrier(CONCURRENCY_COUNT+1);

        // initiate a read operation on each channel.
        for (int i=0; i<CONCURRENCY_COUNT; i++) {
            ByteBuffer buf = ByteBuffer.allocateDirect(100);
            channels[i].read( buf, channels[i],
                new CompletionHandler<Integer,AsynchronousSocketChannel>() {
                    public void completed(Integer bytesRead, AsynchronousSocketChannel ch) {
                        try {
                            ch.close();
                            barrier.await();
                        } catch (Exception x) {
                            throw new AssertionError(x);
                        }
                    }
                    public void failed(Throwable exc, AsynchronousSocketChannel ch) {
                    }
                });
        }
        System.out.println("All read operations outstanding.");

        // write data to each of the accepted connections
        int remaining = CONCURRENCY_COUNT;
        while (remaining > 0) {
            AsynchronousSocketChannel ch = queue.take();
            ch.write(ByteBuffer.wrap("welcome".getBytes())).get();
            ch.close();
            remaining--;
        }

        // wait for all threads to reach the barrier
        System.out.println("Waiting for all threads to reach barrier");
        barrier.await();
        listener.close();
    }
}
