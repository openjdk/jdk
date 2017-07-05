/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */
package java.net.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

//
// Used to implement WebSocket. Each RawChannel corresponds to a TCP connection
// (SocketChannel) but is connected to a Selector and an ExecutorService for
// invoking the send and receive callbacks. Also includes SSL processing.
//
final class RawChannel implements ByteChannel, GatheringByteChannel {

    private final HttpClientImpl client;
    private final HttpConnection connection;
    private volatile boolean closed;

    private interface RawEvent {

        /** must return the selector interest op flags OR'd. */
        int interestOps();

        /** called when event occurs. */
        void handle();
    }

    interface NonBlockingEvent extends RawEvent { }

    RawChannel(HttpClientImpl client, HttpConnection connection) {
        this.client = client;
        this.connection = connection;
    }

    private class RawAsyncEvent extends AsyncEvent {

        private final RawEvent re;

        RawAsyncEvent(RawEvent re) {
            super(AsyncEvent.BLOCKING); // BLOCKING & !REPEATING
            this.re = re;
        }

        RawAsyncEvent(RawEvent re, int flags) {
            super(flags);
            this.re = re;
        }

        @Override
        public SelectableChannel channel() {
            return connection.channel();
        }

        // must return the selector interest op flags OR'd
        @Override
        public int interestOps() {
            return re.interestOps();
        }

        // called when event occurs
        @Override
        public void handle() {
            re.handle();
        }

        @Override
        public void abort() { }
    }

    private class NonBlockingRawAsyncEvent extends RawAsyncEvent {

        NonBlockingRawAsyncEvent(RawEvent re) {
            super(re, 0); // !BLOCKING & !REPEATING
        }
    }

    /*
     * Register given event whose callback will be called once only.
     * (i.e. register new event for each callback)
     */
    public void registerEvent(RawEvent event) throws IOException {
        if (!(event instanceof NonBlockingEvent)) {
            throw new InternalError();
        }
        if ((event.interestOps() & SelectionKey.OP_READ) != 0
                && connection.buffer.hasRemaining()) {
            // FIXME: a hack to deal with leftovers from previous reads into an
            // internal buffer (works in conjunction with change in
            // java.net.http.PlainHttpConnection.readImpl(java.nio.ByteBuffer)
            connection.channel().configureBlocking(false);
            event.handle();
        } else {
            client.registerEvent(new NonBlockingRawAsyncEvent(event));
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        assert !connection.channel().isBlocking();
        return connection.read(dst);
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        connection.close();
    }

    @Override
    public long write(ByteBuffer[] src) throws IOException {
        return connection.write(src, 0, src.length);
    }

    @Override
    public long write(ByteBuffer[] src, int offset, int len)
            throws IOException {
        return connection.write(src, offset, len);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return (int) connection.write(src);
    }
}
