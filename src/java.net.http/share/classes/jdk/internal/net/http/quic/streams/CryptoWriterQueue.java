/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.streams;

import jdk.internal.net.http.quic.frames.CryptoFrame;
import jdk.internal.net.http.quic.VariableLengthEncoder;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

/**
 * Class that buffers crypto data received from QuicTLSEngine.
 * Generates CryptoFrames of requested size.
 *
 * Normally the frames are produced sequentially. However, when the client
 * receives a Retry packet or a Version Negotiation packet, the client hello
 * needs to be replayed. In that case we need to keep the processed data
 * in the queues.
 */
public class CryptoWriterQueue {
    private final Queue<ByteBuffer> queue = new ArrayDeque<>();
    private long position = 0;
    // amount of bytes remaining across all the enqueued buffers
    private int totalRemaining = 0;
    private boolean keepReplayData;

    /**
     * Notify the writer to start keeping processed data. Can only be called on a fresh writer.
     * @throws IllegalStateException if some data was processed already
     */
    public synchronized void keepReplayData() {
        if (position > 0) {
            throw new IllegalStateException("Some data was processed already");
        }
        keepReplayData = true;
    }

    /**
     * Notify the writer to stop keeping processed data.
     */
    public synchronized void discardReplayData() {
        if (!keepReplayData) {
            return;
        }
        keepReplayData = false;
        for (Iterator<ByteBuffer> iterator = queue.iterator(); iterator.hasNext(); ) {
            ByteBuffer next = iterator.next();
            if (next.remaining() == 0) {
                iterator.remove();
            } else {
                return;
            }
        }
    }

    /**
     * Rewinds the enqueued buffer positions to allow for replaying the data
     * @throws IllegalStateException if replay data is not available
     */
    public synchronized void replayData() {
        if (!keepReplayData) {
            throw new IllegalStateException("Replay data not available");
        }
        if (position == 0) {
            return;
        }
        int rewound = 0;
        for (Iterator<ByteBuffer> iterator = queue.iterator(); iterator.hasNext(); ) {
            ByteBuffer next = iterator.next();
            if (next.position() != 0) {
                rewound += next.position();
                next.position(0);
            } else {
                break;
            }
        }
        assert rewound == position : rewound - position;
        position = 0;
        totalRemaining += rewound;
    }

    /**
     * Clears the queue and resets position back to zero
     */
    public synchronized void reset() {
        position = 0;
        totalRemaining = 0;
        queue.clear();
    }

    /**
     * Enqueues the provided crypto data
     * @param buffer data to enqueue
     */
    public synchronized void enqueue(ByteBuffer buffer) {
        queue.add(buffer.slice());
        totalRemaining += buffer.remaining();
    }

    /**
     * Stores the next portion of queued crypto data in a frame.
     * May return null if there's no data to enqueue or if
     * maxSize is too small to fit at least one byte of data.
     * The produced frame may be shorter than maxSize even if there are
     * remaining bytes.
     * @param maxSize maximum size of the returned frame, in bytes
     * @return frame with next portion of crypto data, or null
     * @throws IllegalArgumentException if maxSize < 0
     */
    public synchronized CryptoFrame produceFrame(int maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("negative maxSize");
        }
        if (totalRemaining == 0) {
            return null;
        }
        int posLength = VariableLengthEncoder.getEncodedSize(position);
        // 1 (type) + posLength (position) + 1 (length) + 1 (payload)
        if (maxSize < 3 + posLength) {
            return null;
        }
        int maxPayloadPlusLen = maxSize - 1 - posLength;
        int maxPayload;
        if (maxPayloadPlusLen <= 64) { //63 bytes + 1 byte for length
            maxPayload = maxPayloadPlusLen - 1;
        } else if (maxPayloadPlusLen <= 16385) { // 16383 bytes + 2 bytes for length
            maxPayload = maxPayloadPlusLen - 2;
        } else { // 4 bytes for length
            maxPayload = maxPayloadPlusLen - 4;
        }
        // the frame length that we decide upon
        final int computedFrameLength = Math.min(maxPayload, totalRemaining);
        assert computedFrameLength > 0 : computedFrameLength;
        ByteBuffer frameData = null;
        for (Iterator<ByteBuffer> iterator = queue.iterator(); iterator.hasNext(); ) {
            final ByteBuffer buffer = iterator.next();
            // amount of remaining bytes in the current bytebuffer being processed
            final int numRemainingInBuffer = buffer.remaining();
            if (numRemainingInBuffer == 0) {
                if (!keepReplayData) {
                    iterator.remove();
                }
                continue;
            }
            if (frameData == null) {
                frameData = ByteBuffer.allocate(computedFrameLength);
            }
            if (frameData.remaining() >= numRemainingInBuffer) {
                // frame data can accommodate the entire buffered data, so copy it over
                frameData.put(buffer);
                if (!keepReplayData) {
                    iterator.remove();
                }
            } else {
                // target frameData buffer cannot accommodate the entire buffered data,
                // so we copy over only that much that the target buffer can accommodate

                // amount of data available in the target buffer
                final int spaceAvail = frameData.remaining();
                // copy over the buffered data into the target frameData buffer
                frameData.put(frameData.position(), buffer, buffer.position(), spaceAvail);
                // manually move the position of the target buffer to account for the copied data
                frameData.position(frameData.position() + spaceAvail);
                // manually move the position of the (input) buffered data to account for
                // data that we just copied
                buffer.position(buffer.position() + spaceAvail);
                // target frameData buffer is fully populated, no more processing of available
                // input buffer necessary in this round
                break;
            }
        }
        assert frameData != null;
        assert !frameData.hasRemaining() : frameData.remaining();
        frameData.flip();
        long oldPosition = position;
        position += computedFrameLength;
        totalRemaining -= computedFrameLength;
        assert totalRemaining >= 0 : totalRemaining;
        assert totalRemaining > 0 || keepReplayData || queue.isEmpty();
        return new CryptoFrame(oldPosition, computedFrameLength, frameData);
    }

    /**
     * {@return the current number of buffered bytes}
     */
    public synchronized int remaining() {
        return totalRemaining;
    }
}
