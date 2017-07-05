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
 * @bug 4527345
 * @summary Unit test for AsynchronousDatagramChannel
 */

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class Basic {

    public static void main(String[] args) throws Exception {
        doReceiveTests();
        doReadTests();
        doSendTests();
        doWriteTests();
        doCancelTests();
        doMulticastTests();
    }

    // basic receive tests
    static void doReceiveTests() throws Exception {
        final byte[] msg = "hello".getBytes();

        AsynchronousDatagramChannel ch = AsynchronousDatagramChannel.open()
            .bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress)(ch.getLocalAddress())).getPort();
        InetAddress rh = InetAddress.getLocalHost();
        final SocketAddress sa = new InetSocketAddress(rh, port);

        DatagramChannel sender = DatagramChannel.open();
        ByteBuffer dst = ByteBuffer.allocateDirect(100);

        // Test: datagram packet received immediately
        sender.send(ByteBuffer.wrap(msg), sa);
        dst.clear();
        ch.receive(dst).get(1, TimeUnit.SECONDS);
        if (dst.flip().remaining() != msg.length)
            throw new RuntimeException("Unexpected number of bytes read");

        // Test: datagram packet not received immediately
        dst.clear();
        final CountDownLatch latch = new CountDownLatch(1);
        ch.receive(dst, (Void)null, new CompletionHandler<SocketAddress,Void>() {
            public void completed(SocketAddress source, Void att) {
                latch.countDown();
            }
            public void failed (Throwable exc, Void att) {
            }
            public void cancelled(Void att) {
            }
        });
        Thread.sleep(2000);
        sender.send(ByteBuffer.wrap(msg), sa);
        latch.await(2, TimeUnit.SECONDS);  // wait for completion handler

        // Test: timeout
        dst.clear();
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        ch.receive(dst, 2, TimeUnit.SECONDS, (Void)null, new CompletionHandler<SocketAddress,Void>() {
            public void completed(SocketAddress source, Void att) {
            }
            public void failed (Throwable exc, Void att) {
                exception.set(exc);
            }
            public void cancelled(Void att) {
            }
        });
        Throwable result;
        while ((result = exception.get()) == null) {
            Thread.sleep(100);
        }
        if (!(result instanceof InterruptedByTimeoutException))
            throw new RuntimeException("InterruptedByTimeoutException expected");

        // AsynchronousCloseException
        dst = ByteBuffer.allocateDirect(100);
        exception.set(null);
        ch.receive(dst, (Void)null, new CompletionHandler<SocketAddress,Void>() {
            public void completed(SocketAddress source, Void att) {
            }
            public void failed (Throwable exc, Void att) {
                exception.set(exc);
            }
            public void cancelled(Void att) {
            }
        });
        ch.close();
        while ((result = exception.get()) == null) {
            Thread.sleep(100);
        }
        if (!(result instanceof AsynchronousCloseException))
            throw new RuntimeException("AsynchronousCloseException expected");

        // done
        sender.close();
    }

    // basic read tests
    static void doReadTests() throws Exception {
        final byte[] msg = "hello".getBytes();

        AsynchronousDatagramChannel ch = AsynchronousDatagramChannel.open()
            .bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress)(ch.getLocalAddress())).getPort();
        InetAddress lh = InetAddress.getLocalHost();
        final SocketAddress sa = new InetSocketAddress(lh, port);

        DatagramChannel sender = DatagramChannel.open();
        ByteBuffer dst = ByteBuffer.allocateDirect(100);

        // Test: not connected
        try {
            ch.read(dst);
            throw new RuntimeException("NotYetConnectedException expected");
        } catch (NotYetConnectedException e) {
        }

        // connect the channel
        sender.bind(new InetSocketAddress(0));
        ch.connect(new InetSocketAddress(lh,
                ((InetSocketAddress)(sender.getLocalAddress())).getPort()));

        // Test: datagram packet received immediately
        sender.send(ByteBuffer.wrap(msg), sa);
        dst.clear();
        ch.read(dst).get(1, TimeUnit.SECONDS);
        if (dst.flip().remaining() != msg.length)
            throw new RuntimeException("Unexpected number of bytes read");

        // Test: datagram packet not received immediately
        dst.clear();
        final CountDownLatch l1 = new CountDownLatch(1);
        ch.read(dst, (Void)null, new CompletionHandler<Integer,Void>() {
            public void completed(Integer bytesRead, Void att) {
                l1.countDown();
            }
            public void failed (Throwable exc, Void att) {
            }
            public void cancelled(Void att) {
            }
        });
        Thread.sleep(2000);
        sender.send(ByteBuffer.wrap(msg), sa);
        l1.await(2, TimeUnit.SECONDS);

        // Test: timeout
        dst.clear();
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        ch.read(dst, 2, TimeUnit.SECONDS, (Void)null, new CompletionHandler<Integer,Void>() {
            public void completed(Integer bytesRead, Void att) {
            }
            public void failed (Throwable exc, Void att) {
                exception.set(exc);
            }
            public void cancelled(Void att) {
            }
        });
        Throwable result;
        while ((result = exception.get()) == null) {
            Thread.sleep(100);
        }
        if (!(result instanceof InterruptedByTimeoutException))
            throw new RuntimeException("InterruptedByTimeoutException expected");

        // AsynchronousCloseException
        dst.clear();
        exception.set(null);
        ch.read(dst, (Void)null, new CompletionHandler<Integer,Void>() {
            public void completed(Integer bytesRead, Void att) {
            }
            public void failed (Throwable exc, Void att) {
                exception.set(exc);
            }
            public void cancelled(Void att) {
            }
        });
        ch.close();
        while ((result = exception.get()) == null) {
            Thread.sleep(100);
        }
        if (!(result instanceof AsynchronousCloseException))
            throw new RuntimeException("AsynchronousCloseException expected");

        // done
        sender.close();
    }

    // basic send tests
    static void doSendTests() throws Exception {
        final byte[] msg = "hello".getBytes();

        DatagramChannel reader = DatagramChannel.open()
            .bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress)(reader.getLocalAddress())).getPort();
        InetAddress rh = InetAddress.getLocalHost();
        SocketAddress sa = new InetSocketAddress(rh, port);

        AsynchronousDatagramChannel ch = AsynchronousDatagramChannel.open();

        // Test: send datagram packet to reader
        int bytesSent = ch.send(ByteBuffer.wrap(msg), sa).get();
        if (bytesSent != msg.length)
            throw new RuntimeException("Unexpected number of bytes sent");

        // check received
        ByteBuffer dst = ByteBuffer.allocateDirect(100);
        reader.receive(dst);
        dst.flip();
        if (dst.remaining() != msg.length)
            throw new RuntimeException("Unexpected number of bytes received");

        // Test: send datagram packet to reader and check completion handler
        // is invoked
        final CountDownLatch l2 = new CountDownLatch(1);
        ch.send(ByteBuffer.wrap(msg), sa, (Void)null, new CompletionHandler<Integer,Void>() {
            public void completed(Integer bytesSent, Void att) {
                if (bytesSent != msg.length)
                    throw new RuntimeException("Unexpected number of bytes received");
                l2.countDown();
            }
            public void failed (Throwable exc, Void att) {
            }
            public void cancelled(Void att) {
            }
        });
        l2.await(5, TimeUnit.SECONDS);

        // check received
        dst.clear();
        reader.receive(dst);
        dst.flip();
        if (dst.remaining() != msg.length)
            throw new RuntimeException("Unexpected number of bytes received");

        // Test: check that failed method is invoked
        ch.close();
        final CountDownLatch l3 = new CountDownLatch(1);
        ch.send(ByteBuffer.wrap(msg), sa, (Void)null, new CompletionHandler<Integer,Void>() {
            public void completed(Integer bytesSent, Void att) {
                throw new RuntimeException("completed method invoked");
            }
            public void failed (Throwable exc, Void att) {
                if (exc instanceof ClosedChannelException) {
                    l3.countDown();
                } else {
                    throw new RuntimeException(exc);
                }
            }
            public void cancelled(Void att) {
            }
        });
        l3.await(5, TimeUnit.SECONDS);

        // done
        reader.close();
    }

    // basic write tests
    static void doWriteTests() throws Exception {
        final byte[] msg = "hello".getBytes();

        DatagramChannel reader = DatagramChannel.open()
            .bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress)(reader.getLocalAddress())).getPort();
        InetAddress rh = InetAddress.getLocalHost();
        SocketAddress sa = new InetSocketAddress(rh, port);

        AsynchronousDatagramChannel ch = AsynchronousDatagramChannel.open();

        // Test: unconnected
        try {
            ch.write(ByteBuffer.wrap(msg)).get();
            throw new RuntimeException("NotYetConnectedException expected");
        } catch (NotYetConnectedException e) {
        }

        // Test: connect, and write datagram
        ch.connect(sa);
        int bytesSent = ch.write(ByteBuffer.wrap(msg)).get();
        if (bytesSent != msg.length)
            throw new RuntimeException("Unexpected number of bytes sent");

        // check received
        ByteBuffer dst = ByteBuffer.allocateDirect(100);
        reader.receive(dst);
        dst.flip();
        if (dst.remaining() != msg.length)
            throw new RuntimeException("Unexpected number of bytes received");

        // Test: write datagram and check completion handler is invoked
        final CountDownLatch l2 = new CountDownLatch(1);
        ch.write(ByteBuffer.wrap(msg), (Void)null, new CompletionHandler<Integer,Void>() {
            public void completed(Integer bytesSent, Void att) {
                if (bytesSent != msg.length)
                    throw new RuntimeException("Unexpected number of bytes received");
                l2.countDown();
            }
            public void failed (Throwable exc, Void att) {
            }
            public void cancelled(Void att) {
            }
        });
        l2.await(5, TimeUnit.SECONDS);

        // check received
        dst.clear();
        reader.receive(dst);
        dst.flip();
        if (dst.remaining() != msg.length)
            throw new RuntimeException("Unexpected number of bytes received");

        // done
        ch.close();
        reader.close();
    }

    static void cancelAndCheck(Future<?> result, CountDownLatch latch)
        throws InterruptedException
    {
        boolean cancelled = result.cancel(false);
        if (!cancelled)
            throw new RuntimeException("Not cancelled");
        if (!result.isDone())
            throw new RuntimeException("Should be done");
        try {
            result.get();
            throw new RuntimeException("Result not expected");
        } catch (CancellationException e) {
            // expected
        } catch (ExecutionException e) {
            throw new RuntimeException("Should not fail");
        }

        // make sure that completion handler is invoked
        latch.await();
    }

    // basic cancel tests
    static void doCancelTests() throws Exception {
        InetAddress lh = InetAddress.getLocalHost();

        // timed and non-timed receive
        for (int i=0; i<2; i++) {
            AsynchronousDatagramChannel ch =
                AsynchronousDatagramChannel.open().bind(new InetSocketAddress(0));
            final CountDownLatch latch = new CountDownLatch(1);
            long timeout = (i == 0) ? 0L : 60L;
            Future<SocketAddress> remote = ch
                .receive(ByteBuffer.allocate(100), timeout, TimeUnit.SECONDS, (Void)null,
                    new CompletionHandler<SocketAddress,Void>() {
                        public void completed(SocketAddress source, Void att) {
                        }
                        public void failed (Throwable exc, Void att) {
                        }
                        public void cancelled(Void att) {
                            latch.countDown();
                        }
                    });
            cancelAndCheck(remote, latch);
            ch.close();
        }

        // timed and non-timed read
        for (int i=0; i<2; i++) {
            AsynchronousDatagramChannel ch =
                AsynchronousDatagramChannel.open().bind(new InetSocketAddress(0));
             ch.connect(new InetSocketAddress(lh,
                ((InetSocketAddress)(ch.getLocalAddress())).getPort()));
            final CountDownLatch latch = new CountDownLatch(1);
            long timeout = (i == 0) ? 0L : 60L;
            Future<Integer> result = ch
                .read(ByteBuffer.allocate(100), timeout, TimeUnit.SECONDS, (Void)null,
                    new CompletionHandler<Integer,Void>() {
                        public void completed(Integer bytesRead, Void att) {
                        }
                        public void failed (Throwable exc, Void att) {
                        }
                        public void cancelled(Void att) {
                            latch.countDown();
                        }
                    });
            cancelAndCheck(result, latch);
            ch.close();
        }
    }

    // basic multicast test
    static void doMulticastTests() throws Exception {
        final byte[] msg = "hello".getBytes();

        AsynchronousDatagramChannel ch = AsynchronousDatagramChannel
            .open(StandardProtocolFamily.INET, null)
            .setOption(StandardSocketOption.SO_REUSEADDR, true)
            .bind(new InetSocketAddress(0));

        InetAddress lh = InetAddress.getLocalHost();
        int port = ((InetSocketAddress)(ch.getLocalAddress())).getPort();

        // join group
        InetAddress group = InetAddress.getByName("225.4.5.6");
        NetworkInterface interf = NetworkInterface.getByInetAddress(lh);
        MembershipKey key = ch.join(group, interf);

        // check key
        if (key.channel() != ch)
            throw new RuntimeException("Not the expected channel");

        // send message to group
        DatagramChannel sender = DatagramChannel.open();
        sender.send(ByteBuffer.wrap(msg), new InetSocketAddress(group, port));
        sender.close();

        // check message received
        ByteBuffer dst = ByteBuffer.allocate(200);
        SocketAddress source = ch.receive(dst).get(2, TimeUnit.SECONDS);
        if (!((InetSocketAddress)source).getAddress().equals(lh))
            throw new RuntimeException("Unexpected source");

        // done
        ch.close();
    }
}
