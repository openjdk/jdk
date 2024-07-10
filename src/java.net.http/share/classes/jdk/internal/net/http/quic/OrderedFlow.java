/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import jdk.internal.net.http.quic.frames.CryptoFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.frames.StreamFrame;

/**
 * A class to take care of frames reordering in an ordered flow.
 * Frames that are {@linkplain #receive(QuicFrame) received} out of order
 * will be either buffered or dropped, depending on their {@linkplain
 * #OrderedFlow(Comparator, ToLongFunction, ToIntFunction) position}
 * with respect to the current ordered flow {@linkplain #offset() offset}.
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
            super(CryptoFrame.COMPARE_OFFSETS,
                    CryptoFrame::offset,
                    CryptoFrame::length);
        }

        @Override
        protected CryptoFrame slice(CryptoFrame frame, long offset, int length) {
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
            super(StreamFrame.COMPARE_OFFSETS,
                    StreamFrame::offset,
                    StreamFrame::dataLength);
        }

        @Override
        protected StreamFrame slice(StreamFrame frame, long offset, int length) {
            return frame.slice(offset, length);
        }
    }

    private final ConcurrentSkipListSet<T> queue;
    private final ToLongFunction<T> position;
    private final ToIntFunction<T> length;
    volatile long offset;
    volatile long buffered;

    /**
     * Constructs a new instance of ordered flow to reorder frames in a given
     * flow.
     * @param comparator A comparator to order the frames according to their position in
     *                   the ordered flow. Typically, this will compare the
     *                   frame's offset: the frame with the smaller offset will be sorted
     *                   before the frame with the greater offset
     * @param position   A method reference that returns the position of the frame in the
     *                   flow. Typically, this is a method reference to {@link
     *                   QuicFrame#offset() QuicFrame::offset} (or its subclass override)
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
     * Receives a new frame. If the frame offset is below the current
     * offset the frame is dropped. If it is above the current offset,
     * it is queued.
     * If the frame is exactly at the current offset, it is
     * returned.
     * Otherwise, if the queue contains a frame at the
     * current offset, that frame is returned instead.
     *
     * @param frame a frame that was received
     * @return the next frame in the flow, or {@code null} if it is not
     *         available yet.
     */
    public T receive(T frame) {
        if (frame == null) return null;

        var received = frame;
        long pos = this.position.applyAsLong(frame);
        int length = this.length.applyAsInt(frame);
        assert length >= 0;
        assert pos >= 0;
        long offset = this.offset;
        long offsetdiff = offset - pos;

        // case where the frame is either at offset, or is below
        // offset but has a length that provides bytes that
        // overlap with the current offset. In the later case
        // we will return a slice.
        int todeliver = offsetdiff < 0 ? -1
                : (offsetdiff > length ? -1 : length - (int)offsetdiff);

        // is this the frame we are waiting for, or does it overlaps
        // with the frame we're waiting for?
        if (todeliver >= 0) {
            // The code below may return a FIN frame with length 0 even if
            // one has already been returned. This is to ensure we don't fail
            // to return a 0-length frame that has the FIN bit set.
            while (todeliver >= 0) {
                // that's the one we're waiting for: let's compute
                // the position of the next one
                long next = Math.addExact(offset, todeliver);
                // try to update the offset with the new position
                if (OFFSET.compareAndSet(this, offset, next)) {
                    // cleanup the queue
                    dropuntil(next);
                    if (pos == offset) return frame;
                    if (todeliver == 0) return poll(offset, null);
                    return slice(frame, offset, todeliver);
                } else {
                    // someone beat us to it!
                    offset = this.offset;
                    offsetdiff = offset - pos;
                    // case where the frame is either at offset, or is below
                    // offset but has a length that provides bytes over
                    // the current offset
                    todeliver = offsetdiff < 0 ? -1
                            : (offsetdiff > length ? -1 : length - (int)offsetdiff);
                    assert todeliver >= 0;
                }
            }
            // this frame has been superseded - drop it
            return null;
        } else if (pos < offset) {
            // late arrival! duplicated or retransmitted frame which
            // has already been handled. Just drop it; No overlap
            // if we reach here!
            return null;
        } else {
            // otherwise, the frame is after the offset.
            // insert or slice it, depending on what we
            // have already received.
            return enqueue(frame, pos, length, offset);
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

    private T enqueue(T frame, long pos, int length, long after) {
        assert  pos == position.applyAsLong(frame);
        assert  length == this.length.applyAsInt(frame);
        assert pos > after;
        T head = null;
        long offset;
        // OK to use synchronized: only safe method calls
        synchronized (this) {
            head = peekFirst();
            offset = this.offset;
            assert offset >= after;
            int buffering = -1;
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
                if (foffset == pos && flen == length) {
                    // duplicate of floor - just drop it.
                    return poll(offset, head);
                }
                assert foffset <= pos;
                if (pos - foffset < flen) {
                    // shift newpos
                    if (limit - foffset - flen > 0) {
                        // reduce the frame if it overlaps with the
                        // one that sits just before in the queue
                        newpos = foffset + flen;
                        newlen = length - (int) (newpos - pos);
                    } else {
                        // bytes already all buffered!
                        // just drop the frame
                        return poll(offset, head);
                    }
                }
            }

            // Look at the frames that have an offset higher or equal to
            // the new frame offset, and see if any overlap with the new
            // frame. Use slices of the new frame to fill up the holes,
            // if any.
            while (true) {
                T ceil = queue.ceiling(frame);
                // need to add frames to plug the holes while
                // ceil.offset < frame.offset + frame.length
                if (ceil != null) {
                    long coffset = position.applyAsLong(ceil);
                    if (coffset < limit) {
                        long clen = this.length.applyAsInt(ceil);
                        if (coffset < newpos) {
                            newpos = coffset + clen;
                            assert newpos >= 0; // there should be no overflow here
                            if (newpos >= limit) {
                                // nothing more to do. we enqueued
                                // anything that needed buffering
                                return poll(offset, head);
                            }
                            // safe cast, since newlen <= len
                            newlen = (int) (limit - newpos);
                            // drop the bytes that were already enqueued
                            frame = slice(frame, newpos, newlen);
                            continue;
                        }
                        assert coffset >= newpos;
                        if (clen <= limit - coffset) {
                            // ceiling frame completely contained in the new frame:
                            // remove the ceiling frame
                            queue.remove(ceil);
                            buffered -= clen;
                            continue;
                        }
                        // safe cast, since newlen <= len
                        newlen = (int) (coffset - newpos);
                        queue.add(slice(frame, newpos, newlen));
                        buffered += newlen;
                        newpos = Math.addExact(coffset, clen);
                        assert newpos >= limit;
                        return poll(offset, head);
                    }
                }
                break;
            }
            assert limit == newpos + newlen;
            assert newlen >= 0;
            if (newlen == length) {
                assert newpos == pos;
                queue.add(frame);
            } else if (newlen > 0) {
                queue.add(frame = slice(frame, newpos, newlen));
            }
            buffered += newlen;
        }
        // then peek at the queue: maybe there's something
        //   that's been buffered in between
        return poll(offset, head);
    }

    /**
     * {@return the frame which is at the head of the queue, or null}
     * This method acts like {@link Queue#peek()}.
     * The frame at the head of the queue might not be at the current
     * offset.
     */
    public T peek() {
        return peekFirst();
    }

    /**
     * Removes and return the head of the queue if it is at the
     * current offset. Otherwise, returns null.
     * @return the head of the queue if it is at the current offset,
     * or {@code null}
     */
    public T poll() {
        return poll(offset, null);
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
                    // OK to use synchronized: only safe method calls
                    synchronized (this) {
                        if (head == peekFirst() && queue.remove(head)) {
                            buffered -= length;
                            dropped += length;
                        }
                    }
                } else {
                    // safe cast: consumed < length if we reach here
                    int newlen = length - (int)consumed;
                    var newhead = slice(head, offset, newlen);
                    // OK to use synchronized: only safe method calls
                    synchronized (this) {
                        if (head == peekFirst() && queue.remove(head)) {
                            queue.add(newhead);
                            buffered -= consumed;
                            dropped += consumed;
                        }
                    }
                }
            }
        } while (pos < offset);
        return dropped;
    }

    /**
     * Pretends to {@linkplain #receive(QuicFrame) receive} the head of the queue,
     * if it is at the provided offset and if it is not the given frame.
     *
     * @param offset the minimal offset
     * @param frame  a frame that we don't want to match (typically
     *               because we just added it to the queue, and we know
     *               it's not at the right offset yet)
     *
     * @return a received frame at the current flow offset, or {@code null}
     */
    private T poll(long offset, T frame) {
        long current = this.offset;
        assert offset <= current;
        dropuntil(offset);
        T head = peekFirst();
        if (head != null && head != frame) {
            long pos = position.applyAsLong(head);
            if (pos == offset) {
                // the frame we wanted was in the queue!
                //   well, let's handle it...
                T first = null;
                // OK to use synchronized: only safe method calls
                synchronized (this) {
                    if (head == peekFirst() && queue.remove(head)) {
                        long length = this.length.applyAsInt(head);
                        buffered -= length;
                        first = head;
                    }
                }
                if (first == head) {
                    return receive(head);
                }
            }
        }
        return null;
    }

    private static final VarHandle OFFSET;
    static {
        try {
            Lookup lookup = MethodHandles.lookup();
            OFFSET = lookup.findVarHandle(OrderedFlow.class, "offset", long.class);
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }
}
