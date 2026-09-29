/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import jdk.internal.net.http.quic.frames.CryptoFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.frames.StreamFrame;

/**
 * A class to take care of frames reordering in an ordered flow.
 *
 * Frames that are {@linkplain #receive(QuicFrame) received} out of order
 * will be either buffered or dropped, depending on their {@linkplain
 * #OrderedFlow(Comparator, ToLongFunction, ToIntFunction) position}
 * with respect to the current ordered flow {@linkplain #offset() offset}.
 * The buffered frames are returned by later calls to {@linkplain #poll()}
 * when the flow offset matches the frame offset.
 *
 * Frames that are {@linkplain #receive(QuicFrame) received} in order
 * are immediately returned.
 *
 * This class is not thread-safe and concurrent access needs to be synchronized
 * externally.
 * @param <T> A frame type that defines an offset and a {@linkplain
 *           #OrderedFlow(Comparator, ToLongFunction, ToIntFunction)
 *           length}. The offset of the frame
 *           indicates its  {@linkplain
 *           #OrderedFlow(Comparator, ToLongFunction, ToIntFunction)
 *           position} in the ordered flow.
 */
public sealed abstract class OrderedFlow<T extends QuicFrame> {

    /**
     * A subclass of {@link OrderedFlow} used to reorder instances of
     * {@link CryptoFrame}.
     */
    public static final class CryptoDataFlow extends OrderedFlow<CryptoFrame> {
        /**
         * Constructs a new instance of {@code CryptoDataFlow} to reorder
         * a flow of {@code CryptoFrame} instances.
         */
        public CryptoDataFlow() {
            super(CryptoFrame::compareOffsets,
                    CryptoFrame::offset,
                    CryptoFrame::length);
        }

        @Override
        protected CryptoFrame slice(CryptoFrame frame, long offset, int length) {
            if (length == 0) return null;
            return frame.slice(offset, length);
        }
    }

    /**
     * A subclass of {@link OrderedFlow} used to reorder instances of
     * {@link StreamFrame}.
     */
    public static final class StreamDataFlow extends OrderedFlow<StreamFrame> {
        /**
         * Constructs a new instance of {@code StreamDataFlow} to reorder
         * a flow of {@code StreamFrame} instances.
         */
        public StreamDataFlow() {
            super(StreamFrame::compareOffsets,
                    StreamFrame::offset,
                    StreamFrame::dataLength);
        }

        @Override
        protected StreamFrame slice(StreamFrame frame, long offset, int length) {
            if (length == 0) return null;
            return frame.slice(offset, length);
        }
    }

    private final ConcurrentSkipListSet<T> queue;
    private final ToLongFunction<T> position;
    private final ToIntFunction<T> length;
    long offset;
    long buffered;

    /**
     * Constructs a new instance of ordered flow to reorder frames in a given
     * flow.
     * @param comparator A comparator to order the frames according to their position in
     *                   the ordered flow. Typically, this will compare the
     *                   frame's offset: the frame with the smaller offset will be sorted
     *                   before the frame with the greater offset
     * @param position   A method reference that returns the position of the frame in the
     *                   flow. For instance, this would be {@link CryptoFrame#offset()
     *                   CryptoFrame::offset} if {@code <T>} is {@code CryptoFrame}, or
     *                   {@link StreamFrame#offset() StreamFrame::offset} if {@code <T>}
     *                   is {@code StreamFrame}
     * @param length     A method reference that returns the number of bytes in the frame data.
     *                   This is used to compute the expected position of the next
     *                   frame in the flow. For instance, this would be {@link CryptoFrame#length()
     *                   CryptoFrame::length} if {@code <T>} is {@code CryptoFrame}, or
     *                   {@link StreamFrame#dataLength() StreamFrame::dataLength} if {@code <T>}
     *                   is {@code StreamFrame}
     */
    public OrderedFlow(Comparator<T> comparator, ToLongFunction<T> position,
                       ToIntFunction<T> length) {
        queue = new ConcurrentSkipListSet<>(comparator);
        this.position = position;
        this.length = length;
    }

    /**
     * {@return a slice of the given frame}
     * @param frame   the frame to slice
     * @param offset  the new frame offset
     * @param length  the new frame length
     * @throws IndexOutOfBoundsException if the new offset or length
     *   fall outside of the frame's bounds
     */
    protected abstract T slice(T frame, long offset, int length);

    /**
     * Receives a new frame. If the frame is below the current
     * offset the frame is dropped. If it is above the current offset,
     * it is queued.
     * If the frame is exactly at the current offset, it is
     * returned.
     *
     * @param frame a frame that was received
     * @return the next frame in the flow, or {@code null} if it is not
     *         available yet.
     */
    public T receive(T frame) {
        if (frame == null) return null;

        long start = this.position.applyAsLong(frame);
        int length = this.length.applyAsInt(frame);
        long end = start + length;
        assert length >= 0;
        assert start >= 0;
        long offset = this.offset;
        if (end <= offset || length == 0) {
            // late arrival or empty frame. Just drop it; No overlap
            // if we reach here!
            return null;
        } else if (start > offset) {
            // the frame is after the offset.
            // insert or slice it, depending on what we
            // have already received.
            enqueue(frame, start, length, offset);
            return null;
        } else {
            // case where the frame is either at offset, or is below
            // offset but has a length that provides bytes that
            // overlap with the current offset. In the later case
            // we will return a slice.
            int todeliver = (int)(end - offset);

            assert end == offset + todeliver;
            // update the offset with the new position
            this.offset = end;
            // cleanup the queue
            dropuntil(end);
            if (start == offset) return frame;
            return slice(frame, offset, todeliver);
        }
    }

    private T peekFirst() {
        if (queue.isEmpty()) return null;
        // why is there no peekFirst?
        try {
            return queue.first();
        } catch (NoSuchElementException nse) {
            return null;
        }
    }

    private void enqueue(T frame, long pos, int length, long after) {
        assert  pos == position.applyAsLong(frame);
        assert  length == this.length.applyAsInt(frame);
        assert pos > after;
        long offset = this.offset;
        assert offset >= after;
        long newpos = pos;
        int newlen = length;
        long limit = Math.addExact(pos, length);

        // look at the closest frame, if any, whose offset is <= to
        // the new frame offset. Try to see if the new frame overlaps
        // with that frame, and if so, drops the part that overlaps
        // in the new frame.
        T floor = queue.floor(frame);
        if (floor != null) {
            long foffset = position.applyAsLong(floor);
            long flen = this.length.applyAsInt(floor);
            if (limit <= foffset + flen) {
                // bytes already all buffered!
                // just drop the frame
                return;
            }
            assert foffset <= pos;
            // foffset == pos case handled as ceiling below
            if (foffset < pos && pos - foffset < flen) {
                // reduce the frame if it overlaps with the
                // one that sits just before in the queue
                newpos = foffset + flen;
                newlen = length - (int) (newpos - pos);
            }
        }
        assert limit == newpos + newlen;

        // Look at the frames that have an offset higher or equal to
        // the new frame offset, and see if any overlap with the new
        // frame. Remove frames that are entirely contained in the new one,
        // slice the current frame if the frames overlap.
        while (true) {
            T ceil = queue.ceiling(frame);
            if (ceil != null) {
                long coffset = position.applyAsLong(ceil);
                assert coffset >= newpos : "overlapping frames in queue";
                if (coffset < limit) {
                    long clen = this.length.applyAsInt(ceil);
                    if (clen <= limit - coffset) {
                        // ceiling frame completely contained in the new frame:
                        // remove the ceiling frame
                        queue.remove(ceil);
                        buffered -= clen;
                        continue;
                    }
                    // safe cast, since newlen <= len
                    newlen = (int) (coffset - newpos);
                }
            }
            break;
        }
        assert newlen >= 0;
        if (newlen == length) {
            assert newpos == pos;
            queue.add(frame);
        } else if (newlen > 0) {
            queue.add(slice(frame, newpos, newlen));
        }
        buffered += newlen;
    }

    /**
     * Removes and return the head of the queue if it is at the
     * current offset. Otherwise, returns null.
     * @return the head of the queue if it is at the current offset,
     * or {@code null}
     */
    public T poll() {
        return poll(offset);
    }

    /**
     * {@return the number of buffered frames}
     */
    public int size() {
        return queue.size();
    }

    /**
     * {@return the number of bytes buffered}
     */
     public long buffered() {
         return buffered;
     }

    /**
     * {@return true if there are no buffered frames}
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * {@return the current offset of this buffer}
     */
    public long offset() {
        return offset;
    }

    /**
     * Drops all buffered frames
     */
    public void clear() {
        queue.clear();
    }

    /**
     * Drop all frames in the buffer whose position is strictly
     * below offset.
     *
     * @param offset the offset below which frames should be dropped
     * @return the amount of dropped data
     */
    private long dropuntil(long offset) {
        T head;
        long pos;
        long dropped = 0;
        do {
            head = peekFirst();
            if (head == null) break;
            pos = position.applyAsLong(head);
            if (pos < offset) {
                var length = this.length.applyAsInt(head);
                var consumed = offset - pos;
                if (length <= consumed) {
                    // drop it
                    if (head == queue.pollFirst()) {
                        buffered -= length;
                        dropped += length;
                    } else {
                        throw new AssertionError("Concurrent modification");
                    }
                } else {
                    // safe cast: consumed < length if we reach here
                    int newlen = length - (int)consumed;
                    var newhead = slice(head, offset, newlen);
                    if (head == queue.pollFirst()) {
                        queue.add(newhead);
                        buffered -= consumed;
                        dropped += consumed;
                    } else {
                        throw new AssertionError("Concurrent modification");
                    }
                }
            }
        } while (pos < offset);
        return dropped;
    }

    /**
     * Pretends to {@linkplain #receive(QuicFrame) receive} the head of the queue,
     * if it is at the provided offset
     *
     * @param offset the minimal offset
     *
     * @return a received frame at the current flow offset, or {@code null}
     */
    private T poll(long offset) {
        long current = this.offset;
        assert offset <= current;
        dropuntil(offset);
        T head = peekFirst();
        if (head != null) {
            long pos = position.applyAsLong(head);
            if (pos == offset) {
                // the frame we wanted was in the queue!
                //   well, let's handle it...
                if (head == queue.pollFirst()) {
                    long length = this.length.applyAsInt(head);
                    buffered -= length;
                } else {
                    throw new AssertionError("Concurrent modification");
                }
                return receive(head);
            }
        }
        return null;
    }
}
