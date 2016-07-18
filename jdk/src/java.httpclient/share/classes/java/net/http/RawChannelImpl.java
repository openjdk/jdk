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
 * questions.
 */

package java.net.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/*
 * Each RawChannel corresponds to a TCP connection (SocketChannel) but is
 * connected to a Selector and an ExecutorService for invoking the send and
 * receive callbacks. Also includes SSL processing.
 */
final class RawChannelImpl implements RawChannel {

    private final HttpClientImpl client;
    private final HttpConnection connection;

    RawChannelImpl(HttpClientImpl client, HttpConnection connection)
            throws IOException {
        this.client = client;
        this.connection = connection;
        SocketChannel chan = connection.channel();
        client.cancelRegistration(chan);
        chan.configureBlocking(false);
    }

    private class NonBlockingRawAsyncEvent extends AsyncEvent {

        private final RawEvent re;

        NonBlockingRawAsyncEvent(RawEvent re) {
            super(0); // !BLOCKING & !REPEATING
            this.re = re;
        }

        @Override
        public SelectableChannel channel() {
            return connection.channel();
        }

        @Override
        public int interestOps() {
            return re.interestOps();
        }

        @Override
        public void handle() {
            re.handle();
        }

        @Override
        public void abort() { }
    }

    @Override
    public void registerEvent(RawEvent event) throws IOException {
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
    public long write(ByteBuffer[] src, int offset, int len) throws IOException {
        return connection.write(src, offset, len);
    }

    @Override
    public boolean isOpen() {
        return connection.isOpen();
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
