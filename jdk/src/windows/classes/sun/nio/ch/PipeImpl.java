/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 */

package sun.nio.ch;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.util.Random;


/**
 * A simple Pipe implementation based on a socket connection.
 */

class PipeImpl
    extends Pipe
{

    // Source and sink channels
    private SourceChannel source;
    private SinkChannel sink;

    // Random object for handshake values
    private static final Random rnd;

    static {
        byte[] someBytes = new byte[8];
        boolean resultOK = IOUtil.randomBytes(someBytes);
        if (resultOK) {
            rnd = new Random(ByteBuffer.wrap(someBytes).getLong());
        } else {
            rnd = new Random();
        }
    }

    private class Initializer
        implements PrivilegedExceptionAction<Void>
    {

        private final SelectorProvider sp;

        private IOException ioe = null;

        private Initializer(SelectorProvider sp) {
            this.sp = sp;
        }

        @Override
        public Void run() throws IOException {
            LoopbackConnector connector = new LoopbackConnector();
            connector.run();
            if (ioe instanceof ClosedByInterruptException) {
                ioe = null;
                Thread connThread = new Thread(connector) {
                    @Override
                    public void interrupt() {}
                };
                connThread.start();
                for (;;) {
                    try {
                        connThread.join();
                        break;
                    } catch (InterruptedException ex) {}
                }
                Thread.currentThread().interrupt();
            }

            if (ioe != null)
                throw new IOException("Unable to establish loopback connection", ioe);

            return null;
        }

        private class LoopbackConnector implements Runnable {

            @Override
            public void run() {
                ServerSocketChannel ssc = null;
                SocketChannel sc1 = null;
                SocketChannel sc2 = null;

                try {
                    // Loopback address
                    InetAddress lb = InetAddress.getByName("127.0.0.1");
                    assert(lb.isLoopbackAddress());
                    InetSocketAddress sa = null;
                    for(;;) {
                        // Bind ServerSocketChannel to a port on the loopback
                        // address
                        if (ssc == null || !ssc.isOpen()) {
                            ssc = ServerSocketChannel.open();
                            ssc.socket().bind(new InetSocketAddress(lb, 0));
                            sa = new InetSocketAddress(lb, ssc.socket().getLocalPort());
                        }

                        // Establish connection (assume connections are eagerly
                        // accepted)
                        sc1 = SocketChannel.open(sa);
                        ByteBuffer bb = ByteBuffer.allocate(8);
                        long secret = rnd.nextLong();
                        bb.putLong(secret).flip();
                        sc1.write(bb);

                        // Get a connection and verify it is legitimate
                        sc2 = ssc.accept();
                        bb.clear();
                        sc2.read(bb);
                        bb.rewind();
                        if (bb.getLong() == secret)
                            break;
                        sc2.close();
                        sc1.close();
                    }

                    // Create source and sink channels
                    source = new SourceChannelImpl(sp, sc1);
                    sink = new SinkChannelImpl(sp, sc2);
                } catch (IOException e) {
                    try {
                        if (sc1 != null)
                            sc1.close();
                        if (sc2 != null)
                            sc2.close();
                    } catch (IOException e2) {}
                    ioe = e;
                } finally {
                    try {
                        if (ssc != null)
                            ssc.close();
                    } catch (IOException e2) {}
                }
            }
        }
    }

    PipeImpl(final SelectorProvider sp) throws IOException {
        try {
            AccessController.doPrivileged(new Initializer(sp));
        } catch (PrivilegedActionException x) {
            throw (IOException)x.getCause();
        }
    }

    public SourceChannel source() {
        return source;
    }

    public SinkChannel sink() {
        return sink;
    }

}
