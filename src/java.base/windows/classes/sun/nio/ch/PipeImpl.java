/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.net.UnixDomainSocketAddress;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.spi.*;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.security.SecureRandom;
import java.util.Random;

import static java.net.StandardProtocolFamily.UNIX;

/**
 * A simple Pipe implementation based on a socket connection.
 */

class PipeImpl
    extends Pipe
{
    // Number of bytes in the secret handshake.
    private static final int NUM_SECRET_BYTES = 16;

    // Random object for handshake values
    private static final Random RANDOM_NUMBER_GENERATOR = new SecureRandom();

    // Source and sink channels
    private final SourceChannelImpl source;
    private final SinkChannelImpl sink;

    private static class Initializer
        implements PrivilegedExceptionAction<Void>
    {

        private final SelectorProvider sp;
        private final boolean preferUnixDomain;
        private IOException ioe;
        SourceChannelImpl source;
        SinkChannelImpl sink;

        private Initializer(SelectorProvider sp, boolean preferUnixDomain) {
            this.sp = sp;
            this.preferUnixDomain = preferUnixDomain;
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
                // Loopback address
                SocketAddress sa = null;

                try {
                    // Create secret with a backing array.
                    ByteBuffer secret = ByteBuffer.allocate(NUM_SECRET_BYTES);
                    ByteBuffer bb = ByteBuffer.allocate(NUM_SECRET_BYTES);

                    for(;;) {
                        // Bind ServerSocketChannel to a port on the loopback
                        // address
                        if (ssc == null || !ssc.isOpen()) {
                            ssc = createListener(preferUnixDomain);
                            sa = ssc.getLocalAddress();
                        }

                        // Establish connection (assume connections are eagerly
                        // accepted)
                        sc1 = SocketChannel.open(sa);
                        RANDOM_NUMBER_GENERATOR.nextBytes(secret.array());
                        do {
                            sc1.write(secret);
                        } while (secret.hasRemaining());
                        secret.rewind();

                        // Get a connection and verify it is legitimate
                        sc2 = ssc.accept();
                        do {
                            sc2.read(bb);
                        } while (bb.hasRemaining());
                        bb.rewind();

                        if (bb.equals(secret))
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
                        if (sa instanceof UnixDomainSocketAddress uaddr) {
                            Files.deleteIfExists(uaddr.getPath());
                        }
                    } catch (IOException e2) {}
                }
            }
        }
    }

    /**
     * Creates a (TCP) Pipe implementation that supports buffering.
     */
    PipeImpl(SelectorProvider sp) throws IOException {
        this(sp, true, false);
    }

    /**
     * Creates Pipe implementation that supports optionally buffering
     * and is TCP by default, but if Unix domain is supported and
     * preferAfUnix is true, then Unix domain sockets are used.
     *
     * @param preferAfUnix use Unix domain sockets if supported
     *
     * @param buffering if false set TCP_NODELAY on TCP sockets
     */
    @SuppressWarnings("removal")
    PipeImpl(SelectorProvider sp, boolean preferAfUnix, boolean buffering) throws IOException {
        Initializer initializer = new Initializer(sp, preferAfUnix);
        try {
            AccessController.doPrivileged(initializer);
            SinkChannelImpl sink = initializer.sink;
            if (sink.isNetSocket() && !buffering) {
                sink.setOption(StandardSocketOptions.TCP_NODELAY, true);
            }
        } catch (PrivilegedActionException pae) {
            throw (IOException) pae.getCause();
        }
        this.source = initializer.source;
        this.sink = initializer.sink;
    }

    public SourceChannelImpl source() {
        return source;
    }

    public SinkChannelImpl sink() {
        return sink;
    }

    private static ServerSocketChannel createListener(boolean preferUnixDomain) throws IOException {
        ServerSocketChannel listener = null;
        if (preferUnixDomain && UnixDomainSockets.isSupported()) {
            try {
                listener = ServerSocketChannel.open(UNIX);
                listener.bind(null);
                return listener;
            } catch (IOException | UnsupportedOperationException e) {
                if (listener != null)
                    listener.close();
            }
        }
        listener = ServerSocketChannel.open();
        InetAddress lb = InetAddress.getLoopbackAddress();
        listener.bind(new InetSocketAddress(lb, 0));
        return listener;
    }
}
