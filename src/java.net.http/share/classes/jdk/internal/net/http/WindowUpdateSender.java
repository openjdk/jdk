/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import jdk.internal.net.http.common.FlowTube;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.frame.SettingsFrame;
import jdk.internal.net.http.frame.WindowUpdateFrame;
import jdk.internal.net.http.common.Utils;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A class that tracks the amount of flow controlled
 * data received on an HTTP/2 connection
 */
abstract class WindowUpdateSender {

    final Logger debug =
            Utils.getDebugLogger(this::dbgString, Utils.DEBUG);

    // The threshold at which window updates are sent in bytes
    final int limit;
    // The flow control window in bytes
    final int windowSize;
    final Http2Connection connection;
    // The amount of flow controlled data received and processed, in bytes,
    // since the start of the window.
    // The window is exhausted when received + unprocessed >= windowSize
    final AtomicLong received = new AtomicLong();
    // The amount of flow controlled data received and unprocessed, in bytes,
    // since the start of the window.
    // The window is exhausted when received + unprocessed >= windowSize
    final AtomicLong unprocessed = new AtomicLong();
    final ReentrantLock sendLock = new ReentrantLock();

    WindowUpdateSender(Http2Connection connection) {
        this(connection, connection.clientSettings.getParameter(SettingsFrame.INITIAL_WINDOW_SIZE));
    }

    WindowUpdateSender(Http2Connection connection, int initWindowSize) {
        this(connection, connection.getMaxReceiveFrameSize(), initWindowSize);
    }

    WindowUpdateSender(Http2Connection connection, int maxFrameSize, int initWindowSize) {
        this.connection = connection;
        this.windowSize = initWindowSize;
        int v0 = Math.max(0, initWindowSize - maxFrameSize);
        int v1 = (initWindowSize + (maxFrameSize - 1)) / maxFrameSize;
        v1 = v1 * maxFrameSize / 2;
        // send WindowUpdate heuristic:
        // - we got data near half of window size
        //   or
        // - remaining window size reached max frame size.
        limit = Math.min(v0, v1);
        if (debug.on())
            debug.log("maxFrameSize=%d, initWindowSize=%d, limit=%d",
                      maxFrameSize, initWindowSize, limit);
    }

    // O for the connection window, > 0 for a stream window
    abstract int getStreamId();


    /**
     * {@return {@code true} if buffering the given amount of
     * flow controlled data would not exceed the flow control
     * window}
     * <p>
     * This method is called before buffering and processing
     * a DataFrame. The count of unprocessed bytes is incremented
     * by the given amount, and checked against the number of
     * available bytes in the flow control window.
     * <p>
     * This method returns {@code true} if the bytes can be buffered
     * without exceeding the flow control window, {@code false}
     * if the flow control window is exceeded and corrective
     * action (close/reset) has been taken.
     * <p>
     * When this method returns true, either {@link #processed(int)}
     * or {@link #released(int)} must eventually be called to release
     * the bytes from the flow control window.
     *
     * @implSpec
     * an HTTP/2 endpoint may disable its own flow control
     * (see <a href="https://www.rfc-editor.org/rfc/rfc9113.html#section-5.2.1">
     *     RFC 9113, section 5.2.1</a>), in which case this
     * method may return true even if the flow control window would
     * be exceeded: that is, the flow control window is exceeded but
     * the endpoint decided to take no corrective action.
     *
     * @param  len a number of unprocessed bytes, which
     *             the caller wants to buffer.
     */
    boolean canBufferUnprocessedBytes(int len) {
        long buffered, processed;
        // get received before unprocessed in order to avoid counting
        // unprocessed bytes that might get unbuffered asynchronously
        // twice.
        processed = received.get();
        buffered = unprocessed.addAndGet(len);
        return !checkWindowSizeExceeded(processed, buffered);
    }

    // adds the provided amount to the amount of already
    // processed and processed bytes and checks whether the
    // flow control window is exceeded. If so, take
    // corrective actions and return true.
    private boolean checkWindowSizeExceeded(long processed, long len) {
        // because windowSize is bound by Integer.MAX_VALUE
        // we will never reach the point where received.get() + len
        // could overflow
        long rcv = processed + len;
        return rcv > windowSize && windowSizeExceeded(rcv);
    }

    /**
     * Called after unprocessed buffered bytes have been
     * processed, to release part of the flow control window
     *
     * @apiNote this method is called only when releasing bytes
     * that where buffered after calling
     * {@link #canBufferUnprocessedBytes(int)}.
     *
     * @param delta the amount of processed bytes to release
     */
    void processed(int delta) {
        assert delta >= 0 : delta;
        long rest = unprocessed.addAndGet(-delta);
        assert rest >= 0;
        update(delta);
    }

    /**
     * Called when it is desired to release unprocessed bytes
     * without processing them, or without triggering the
     * sending of a window update. This method can be called
     * instead of calling {@link #processed(int)}.
     * When this method is called instead of calling {@link #processed(int)},
     * it should generally be followed by a call to {@link #update(int)},
     * unless the stream or connection is being closed.
     *
     * @apiNote this method should only be called to release bytes that
     * have been buffered after calling {@link
     * #canBufferUnprocessedBytes(int)}.
     *
     * @param delta the amount of bytes to release from the window
     *
     * @return the amount of remaining unprocessed bytes
     */
    long released(int delta) {
        assert delta >= 0 : delta;
        long rest = unprocessed.addAndGet(-delta);
        assert rest >= 0;
        return rest;
    }

    /**
     * This method is called to update the flow control window,
     * and possibly send a window update
     *
     * @apiNote this method can be called directly if a frame is
     * dropped before calling {@link #canBufferUnprocessedBytes(int)}.
     * Otherwise, either {@link #processed(int)} or {@link #released(int)}
     * should be called, depending on whether sending a window update
     * is desired or not. It is typically not desired to send an update
     * if the stream or connection is being closed.
     *
     * @param delta the amount of bytes released from the window.
     */
    void update(int delta) {
        long rcv = received.addAndGet(delta);
        if (debug.on()) debug.log("update: %d, received: %d, limit: %d", delta, rcv, limit);
        if (rcv > windowSize && windowSizeExceeded(rcv)) {
            return;
        }
        if (rcv > limit) {
            sendLock.lock();
            try {
                int tosend = (int)Math.min(received.get(), Integer.MAX_VALUE);
                if (tosend > limit) {
                    received.addAndGet(-tosend);
                    sendWindowUpdate(tosend);
                }
            } finally {
                sendLock.unlock();
            }
        }
    }

    void sendWindowUpdate(int delta) {
        if (debug.on()) debug.log("sending window update: %d", delta);
        assert delta > 0 : "illegal window update delta: " + delta;
        connection.sendUnorderedFrame(new WindowUpdateFrame(getStreamId(), delta));
    }

    volatile String dbgString;
    String dbgString() {
        String dbg = dbgString;
        if (dbg != null) return dbg;
        FlowTube tube = connection.connection.getConnectionFlow();
        if (tube == null) {
            return "WindowUpdateSender(stream: " + getStreamId() + ")";
        } else {
            int streamId = getStreamId();
            dbg = connection.dbgString() + ":WindowUpdateSender(stream: " + streamId + ")";
            return streamId == 0 ? dbg : (dbgString = dbg);
        }
    }

    /**
     * Called when the flow control window size is exceeded
     * This method may return false if flow control is disabled
     * in this endpoint.
     *
     * @param received the amount of data received, which is greater
     *                 than {@code windowSize}
     * @return {@code true} if the error was reported to the peer
     *         and no further window update should be sent.
     */
    protected abstract boolean windowSizeExceeded(long received);

}
