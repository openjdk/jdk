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
 * @summary Unit test for AsynchronousServerSocketChannel
 * @run main/timeout=180 Basic
 */

import java.nio.channels.*;
import java.net.*;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class Basic {

    public static void main(String[] args) throws Exception {
        testBind();
        testAccept();
    }

    static void testBind() throws Exception {
        System.out.println("-- bind --");

        AsynchronousServerSocketChannel ch = AsynchronousServerSocketChannel.open();
        if (ch.getLocalAddress() != null)
            throw new RuntimeException("Local address should be 'null'");
        ch.bind(new InetSocketAddress(0), 20);

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
        ch = AsynchronousServerSocketChannel.open();
        ch.close();
        try {
            ch.bind(new InetSocketAddress(0));
            throw new RuntimeException("ClosedChannelException  expected");
        } catch (ClosedChannelException  x) {
        }
    }

    static void testAccept() throws Exception {
        System.out.println("-- accept --");

        final AsynchronousServerSocketChannel listener =
            AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(0));

        InetAddress lh = InetAddress.getLocalHost();
        int port = ((InetSocketAddress)(listener.getLocalAddress())).getPort();
        final InetSocketAddress isa = new InetSocketAddress(lh, port);

        // establish a few loopback connections
        for (int i=0; i<100; i++) {
            SocketChannel sc = SocketChannel.open(isa);
            AsynchronousSocketChannel ch = listener.accept().get();
            sc.close();
            ch.close();
        }

       final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        // start accepting
        listener.accept((Void)null, new CompletionHandler<AsynchronousSocketChannel,Void>() {
            public void completed(AsynchronousSocketChannel ch, Void att) {
                try {
                    ch.close();
                } catch (IOException ignore) { }
            }
            public void failed(Throwable exc, Void att) {
                exception.set(exc);
            }
        });

        // check AcceptPendingException
        try {
            listener.accept();
            throw new RuntimeException("AcceptPendingException expected");
        } catch (AcceptPendingException x) {
        }

        // asynchronous close
        listener.close();
        while (exception.get() == null)
            Thread.sleep(100);
        if (!(exception.get() instanceof AsynchronousCloseException))
            throw new RuntimeException("AsynchronousCloseException expected");

        // once closed when a further attemt should throw ClosedChannelException
        try {
            listener.accept().get();
            throw new RuntimeException("ExecutionException expected");
        } catch (ExecutionException x) {
            if (!(x.getCause() instanceof ClosedChannelException))
                throw new RuntimeException("Cause of ClosedChannelException expected");
        } catch (InterruptedException x) {
        }

    }
}
