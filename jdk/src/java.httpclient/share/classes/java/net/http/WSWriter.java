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
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/*
 * Writes ByteBuffer[] to the channel in a non-blocking, asynchronous fashion.
 *
 * A client attempts to write data by calling
 *
 *     boolean tryWriteFully(ByteBuffer[] buffers)
 *
 * If the attempt was successful and all the data has been written, then the
 * method returns `true`.
 *
 * If the data has been written partially, then the method returns `false`, and
 * the writer (this object) attempts to complete the write asynchronously by
 * calling, possibly more than once
 *
 *     boolean tryCompleteWrite()
 *
 * in its own threads.
 *
 * When the write has been completed asynchronously, the callback is signalled
 * with `null`.
 *
 * If an error occurs in any of these stages it will NOT be thrown from the
 * method. Instead `false` will be returned and the exception will be signalled
 * to the callback. This is done in order to handle all exceptions in a single
 * place.
 */
final class WSWriter {

    private final RawChannel channel;
    private final RawChannel.RawEvent writeReadinessHandler;
    private final Consumer<Throwable> completionCallback;
    private ByteBuffer[] buffers;
    private int offset;

    WSWriter(RawChannel channel, Consumer<Throwable> completionCallback) {
        this.channel = channel;
        this.completionCallback = completionCallback;
        this.writeReadinessHandler = createHandler();
    }

    boolean tryWriteFully(ByteBuffer[] buffers) {
        synchronized (this) {
            this.buffers = requireNonNull(buffers);
            this.offset = 0;
        }
        return tryCompleteWrite();
    }

    private final boolean tryCompleteWrite() {
        try {
            return writeNow();
        } catch (IOException e) {
            completionCallback.accept(e);
            return false;
        }
    }

    private boolean writeNow() throws IOException {
        synchronized (this) {
            for (; offset != -1; offset = nextUnwrittenIndex(buffers, offset)) {
                long bytesWritten = channel.write(buffers, offset, buffers.length - offset);
                if (bytesWritten == 0) {
                    channel.registerEvent(writeReadinessHandler);
                    return false;
                }
            }
            return true;
        }
    }

    private static int nextUnwrittenIndex(ByteBuffer[] buffers, int offset) {
        for (int i = offset; i < buffers.length; i++) {
            if (buffers[i].hasRemaining()) {
                return i;
            }
        }
        return -1;
    }

    private RawChannel.RawEvent createHandler() {
        return new RawChannel.RawEvent() {

            @Override
            public int interestOps() {
                return SelectionKey.OP_WRITE;
            }

            @Override
            public void handle() {
                if (tryCompleteWrite()) {
                    completionCallback.accept(null);
                }
            }

            @Override
            public String toString() {
                return "Write readiness event [" + channel + "]";
            }
        };
    }
}
