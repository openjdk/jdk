/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Simple blocking Send Window Flow-Controller that is used to control
 * outgoing Connection and Stream flows, per HTTP/2 connection.
 *
 * A Http2Connection has its own unique single instance of a WindowController
 * that it shares with its Streams. Each stream must acquire the appropriate
 * amount of Send Window from the controller before sending data.
 *
 * WINDOW_UPDATE frames, both connection and stream specific, must notify the
 * controller of their increments. SETTINGS frame's INITIAL_WINDOW_SIZE must
 * notify the controller so that it can adjust the active stream's window size.
 */
final class WindowController {

    /**
     * Default initial connection Flow-Control Send Window size, as per HTTP/2.
     */
    private static final int DEFAULT_INITIAL_WINDOW_SIZE = 64 * 1024 - 1;

    /** The connection Send Window size. */
    private int connectionWindowSize;
    /** A Map of the active streams, where the key is the stream id, and the
     *  value is the stream's Send Window size, which may be negative. */
    private final Map<Integer,Integer> streams = new HashMap<>();

    private final ReentrantLock controllerLock = new ReentrantLock();

    private final Condition notExhausted = controllerLock.newCondition();

    /** A Controller with the default initial window size. */
    WindowController() {
        connectionWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
    }

    /** A Controller with the given initial window size. */
    WindowController(int initialConnectionWindowSize) {
        connectionWindowSize = initialConnectionWindowSize;
    }

    /** Registers the given stream with this controller. */
    void registerStream(int streamid, int initialStreamWindowSize) {
        controllerLock.lock();
        try {
            Integer old = streams.put(streamid, initialStreamWindowSize);
            if (old != null)
                throw new InternalError("Unexpected entry [" + old + "] for streamid: " + streamid);
        } finally {
            controllerLock.unlock();
        }
    }

    /** Removes/De-registers the given stream with this controller. */
    void removeStream(int streamid) {
        controllerLock.lock();
        try {
            Integer old = streams.remove(streamid);
            // Odd stream numbers (client streams) should have been registered.
            // Even stream numbers (server streams - aka Push Streams) should
            // not be registered
            final boolean isClientStream = (streamid % 2) == 1;
            if (old == null && isClientStream) {
                throw new InternalError("Expected entry for streamid: " + streamid);
            } else if (old != null && !isClientStream) {
                throw new InternalError("Unexpected entry for streamid: " + streamid);
            }
        } finally {
            controllerLock.unlock();
        }
    }

    /**
     * Attempts to acquire the requested amount of Send Window for the given
     * stream.
     *
     * The actual amount of Send Window available may differ from the requested
     * amount. The actual amount, returned by this method, is the minimum of,
     * 1) the requested amount, 2) the stream's Send Window, and 3) the
     * connection's Send Window.
     *
     * This method ( currently ) blocks until some positive amount of Send
     * Window is available.
     */
    int tryAcquire(int requestAmount, int streamid) throws InterruptedException {
        controllerLock.lock();
        try {
            int x = 0;
            Integer streamSize = 0;
            while (x <= 0) {
                streamSize = streams.get(streamid);
                if (streamSize == null)
                    throw new InternalError("Expected entry for streamid: " + streamid);
                x = Math.min(requestAmount,
                             Math.min(streamSize, connectionWindowSize));

                if (x <= 0)  // stream window size may be negative
                    notExhausted.await();
            }

            streamSize -= x;
            streams.put(streamid, streamSize);
            connectionWindowSize -= x;
            return x;
        } finally {
            controllerLock.unlock();
        }
    }

    /**
     * Increases the Send Window size for the connection.
     *
     * @return false if, and only if, the addition of the given amount would
     *         cause the Send Window to exceed 2^31-1 (overflow), otherwise true
     */
    boolean increaseConnectionWindow(int amount) {
        controllerLock.lock();
        try {
            int size = connectionWindowSize;
            size += amount;
            if (size < 0)
                return false;
            connectionWindowSize = size;
            notExhausted.signalAll();
        } finally {
            controllerLock.unlock();
        }
        return true;
    }

    /**
     * Increases the Send Window size for the given stream.
     *
     * @return false if, and only if, the addition of the given amount would
     *         cause the Send Window to exceed 2^31-1 (overflow), otherwise true
     */
    boolean increaseStreamWindow(int amount, int streamid) {
        controllerLock.lock();
        try {
            Integer size = streams.get(streamid);
            if (size == null)
                throw new InternalError("Expected entry for streamid: " + streamid);
            size += amount;
            if (size < 0)
                return false;
            streams.put(streamid, size);
            notExhausted.signalAll();
        } finally {
            controllerLock.unlock();
        }
        return true;
    }

    /**
     * Adjusts, either increases or decreases, the active streams registered
     * with this controller.  May result in a stream's Send Window size becoming
     * negative.
     */
    void adjustActiveStreams(int adjustAmount) {
        assert adjustAmount != 0;

        controllerLock.lock();
        try {
            for (Map.Entry<Integer,Integer> entry : streams.entrySet()) {
                int streamid = entry.getKey();
                // the API only supports sending on Streams initialed by
                // the client, i.e. odd stream numbers
                if (streamid != 0 && (streamid % 2) != 0) {
                    Integer size = entry.getValue();
                    size += adjustAmount;
                    streams.put(streamid, size);
                }
            }
        } finally {
            controllerLock.unlock();
        }
    }

    /** Returns the Send Window size for the connection. */
    int connectionWindowSize() {
        controllerLock.lock();
        try {
            return connectionWindowSize;
        } finally {
            controllerLock.unlock();
        }
    }

    /** Returns the Send Window size for the given stream. */
    int streamWindowSize(int streamid) {
        controllerLock.lock();
        try {
            Integer size = streams.get(streamid);
            if (size == null)
                throw new InternalError("Expected entry for streamid: " + streamid);
            return size;
        } finally {
            controllerLock.unlock();;
        }
    }
}
