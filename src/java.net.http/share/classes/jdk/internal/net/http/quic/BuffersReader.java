/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A class that allows to read data from an aggregation of {@code ByteBuffer}.
 * This is mostly geared to reading Quic or HTTP/3 frames that are composed
 * of an aggregation of {@linkplain VariableLengthEncoder Variable Length Integers}.
 * This class is not multi-thread safe.
 * <p>
 * The {@code BuffersReader} class is an abstract class with two concrete
 * implementations: {@link SingleBufferReader} and {@link ListBuffersReader}.
 * <p>
 * The {@link SingleBufferReader} presents a simple lightweight view of a single
 * {@link ByteBuffer}. Instances of {@code SingleBufferReader} can be created by
 * calling {@link BuffersReader#single(ByteBuffer) BuffersReader.single(buffer)};
 * <p>
 * The {@link ListBuffersReader} view can be created from a (possibly empty)
 * list of byte buffers. New byte buffers can be later {@linkplain
 * ListBuffersReader#add(ByteBuffer) added} to the {@link ListBuffersReader} instance
 * as they become available. Once a frame has been fully received,
 * {@link BuffersReader#release()} or {@link BuffersReader#getAndRelease(long)} should
 * be called to forget and relinquish all bytes buffers up to the current
 * {@linkplain #position() position} of the {@code BuffersReader}.
 * Released buffers are removed from {@code BuffersReader} list, and the position
 * of the reader is reset to 0, allowing to read the next frame from the remaining
 * data.
 */
public abstract sealed class BuffersReader {

    /**
     * Release all buffers held by this {@code BuffersReader}, whether
     * consumed or unconsumed. Released buffer are all set to their
     * limit.
     */
    public abstract void clear();

    // Used to store the original position and limit of a
    // buffer at the time it's added to the reader's list
    // It is not possible to beyond that position or limit when
    // using the reader
    private record Buffer(ByteBuffer buffer, int offset, int limit) {
        Buffer {
            assert offset <= limit;
            assert offset >= 0;
            assert limit == buffer.limit();
        }
        Buffer(ByteBuffer buffer) {
            this(buffer, buffer.position(), buffer.limit());
        }
    }

    /**
     * {@return the current position of the reader}
     * The semantic is similar to {@link ByteBuffer#position()}.
     */
    public abstract long position();

    /**
     * {@return the limit of the reader}
     * The semantic is similar to {@link ByteBuffer#limit()}.
     */
    public abstract long limit();

    /**
     * Reads one byte from the reader. This method increase
     * the position by one. The semantic is similar to
     * {@link ByteBuffer#get()}.
     * @return the byte at the current position
     * @throws BufferUnderflowException if trying to read past
     *         the limit.
     */
    public abstract byte get();

    /**
     * Reads the byte located at the given position in the
     * reader. The semantic is similar to {@link ByteBuffer#get(int)}.
     * This method doesn't change the position of the reader.
     *
     * @param position the position of the byte
     * @return the byte at the given position in the reader
     *
     * @throws IndexOutOfBoundsException if trying to read before
     * the reader's position or after the reader's limit
     */
    public abstract byte get(long position);

    /**
     * Sets the position of the reader.
     * The semantic is similar to {@link ByteBuffer#position(int)}.
     *
     * @param newPosition the new position
     *
     * @throws IllegalArgumentException if trying to set
     * the position to a negative value, or to a value
     * past the limit
     */
    public abstract void position(long newPosition);

    /**
     * Releases all the data that has been read, sets the
     * reader's position to 0 and its limit to the amount
     * of data remaining.
     */
    public abstract void release();

    /**
     * Returns a list of {@code ByteBuffer} containing the
     * requested amount of bytes, starting at the current
     * position, then release all the data up to the new
     * position, and reset the reader's position to 0 and
     * the reader's limit to the amount of remaining data.
     * .
     * @param bytes the amount of bytes to read and move
     *              to the returned list.
     *
     * @return a list of {@code ByteBuffer} containing the next
     *    {@code bytes} of data, starting at the current position.
     *
     * @throws BufferUnderflowException if attempting to read past
     * the limit
     */
    public abstract List<ByteBuffer> getAndRelease(long bytes);

    /**
     * {@return true if the reader has remaining bytes to the read}
     * The semantic is similar to {@link ByteBuffer#hasRemaining()}.
     */
    public boolean hasRemaining() {
        return position() < limit();
    }

    /**
     * {@return the number of bytes that remain to read}
     * The semantic is similar to {@link ByteBuffer#remaining()}.
     */
    public long remaining() {
        long rem = limit() - position();
        return rem > 0 ? rem : 0;
    }

    /**
     * {@return the cumulated amount of data that has been read in this
     *  {@code BuffersReader} since its creation}
     *  This number is not reset when calling {@link #release()}.
     */
    public abstract long read();

    /**
     * {@return The offset of this {@code BuffersReader}}
     * This is the position in the first {@code ByteBuffer} that
     * was set on the reader. The {@code BuffersReader} will not
     * allow to get or set a position lower than the offset.
     */
    public abstract long offset();

    /**
     * {@return true if this {@code BuffersReader} is empty}
     * A {@code BuffersReader} is empty if it has been {@linkplain
     * #list() created empty, or if it has been {@linkplain #release()
     * released} after all data has been read.
     */
    public abstract boolean isEmpty();

    /**
     * A lightweight view allowing to see a {@link ByteBuffer} as a
     * {@link BuffersReader}. This class wrap a single {@link ByteBuffer}
     * and cannot be reused after {@link #release()}.
     */
    public static final class SingleBufferReader extends BuffersReader {
        ByteBuffer single;
        long read = 0;
        long start;
        SingleBufferReader(ByteBuffer single) {
            this.single = single;
            start = single.position();
        }

        @Override
        public void release() {
            single = null;
        }

        @Override
        public List<ByteBuffer> getAndRelease(long bytes) {
            return List.of(getAndReleaseBuffer(bytes));
        }

        @Override
        public byte get() {
            if (single == null) throw new BufferUnderflowException();
            return single.get();
        }

        @Override
        public byte get(long position) {
            if (single == null || position < start || position >= single.limit())
                throw new IndexOutOfBoundsException();
            return single.get((int) position);
        }

        @Override
        public long limit() {
            return single == null ? 0 : single.limit();
        }

        @Override
        public long position() {
            return single == null ? 0 : single.position();
        }

        @Override
        public boolean hasRemaining() {
            return single != null && single.hasRemaining();
        }

        @Override
        public void position(long pos) {
            if (single == null || pos < start || pos > single.limit())
                throw new BufferUnderflowException();
            single.position((int) pos);
        }

        /**
         * This method has the same semantics than {@link #getAndRelease(long)}
         * except that it avoids creating a list.
         * @return a buffer containing the next {@code bytes}.
         */
        public ByteBuffer getAndReleaseBuffer(long bytes) {
            var released = single;
            int remaining = released.remaining();
            if (bytes > remaining)
                throw new BufferUnderflowException();
            if (bytes == remaining) {
                read = single.limit() - start;
                single = null;
            } else {
                read = single.position() - start;
                single = released.slice(released.position() + (int)bytes, released.limit());
                start = 0;
                released = released.slice(released.position(), (int) bytes);
            }
            return released;
        }

        @Override
        public long read() {
            return single == null ? read : (read + single.position() - start);
        }

        @Override
        public long offset() {
            return start;
        }

        @Override
        public boolean isEmpty() {
            return single == null;
        }

        @Override
        public void clear() {
            if (single == null) return;
            single.position(single.limit());
            single = null;
        }
    }

    /**
     * A {@code BuffersReader} that iterates over a list of {@code ByteBuffers}.
     * New {@code ByteBuffers} can be added at the end of list by calling
     * {@link #add(ByteBuffer)} or {@link #addAll(List)}, which increases
     * the {@linkplain #limit() limit} accordingly.
     * <p>
     * When {@link #release() released}, the data prior to the current
     * {@linkplain #position()} is discarded, the {@linkplain #position() position}
     * and {@linkplain #offset() offset} are reset to {@code 0}, and the
     * {@linkplain #limit() limit} is set to the amount of remaining data.
     * <p>
     * A {@code ListBuffersReader} can be reused after being released.
     * If it still contains data, the {@linkplain #offset() offset} will
     * be {@code 0}. Otherwise, the offset will be set to the position
     * of the first buffer {@linkplain #add(ByteBuffer) added} to the
     * {@code ListBuffersReader}.
     */
    public static final class ListBuffersReader extends BuffersReader {
        private final List<Buffer> buffers = new ArrayList<>();
        private Buffer current;
        private int nextIndex;
        private long currentOffset;
        private long position;
        private long limit;
        private long start;
        private long readAndReleased = 0;

        ListBuffersReader() {
        }

        /**
         * Adds a new {@code ByteBuffer} to this {@code BuffersReader}.
         * If the reader is {@linkplain #isEmpty() empty}, the reader's
         * {@linkplain #offset() offset} and {@linkplain #position() position}
         * is set to the buffer's position, and the reader {@linkplain #limit()
         * limit} is set to the buffer's limit.
         * Otherwise, the reader's limit is simply increased by the buffer's
         * remaining bytes. The reader will only allow to read those bytes
         * between the current position and limit of the buffer.
         *
         * @apiNote
         * This class doesn't make defensive copies of the provided buffers,
         * so the caller must not modify the buffer's position or limit
         * after it's been added to the reader.
         *
         * @param buffer a byte buffer
         * @return this reader
         */
        public ListBuffersReader add(ByteBuffer buffer) {
            if (buffers.isEmpty()) {
                int lim = buffer.limit();
                buffers.add(new Buffer(buffer, 0, lim));
                start = buffer.position();
                position = limit = start;
                currentOffset = 0;
            } else {
                buffers.add(new Buffer(buffer));
            }
            limit += buffer.remaining();
            return this;
        }

        /**
         * Adds a list of byte buffers to this reader.
         * This is equivalent to calling:
         * {@snippet :
         *     ListBuffersReader reader = ...;
         *     for (var buffer : buffers) {
         *         reader.add(buffer); // @link substring="add" target="#add(ByteBuffer)"
         *     }
         * }
         * @param buffers a list of {@link ByteBuffer ByteBuffers}
         * @return this reader
         */
        public ListBuffersReader addAll(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (isEmpty()) {
                    add(buffer);
                    continue;
                }
                this.buffers.add(new Buffer(buffer));
                limit += buffer.remaining();
            }
            return this;
        }

        @Override
        public boolean isEmpty() {
            return buffers.isEmpty();
        }

        @Override
        public byte get() {
            ByteBuffer buffer = current(true);
            byte res = buffer.get();
            position++;
            return res;
        }

        @Override
        public byte get(long pos) {
            if (pos >= limit || pos < start)
                throw new IndexOutOfBoundsException();
            ByteBuffer buffer = current(false);
            if (position == limit && current != null) {
                // let the current buffer throw
                buffer = current.buffer;
            }
            assert buffer != null : "limit check failed";
            if (pos == position) {
                return buffer.get(buffer.position());
            }
            long offset = currentOffset;
            int index = nextIndex;
            Buffer cur = current;
            while (pos >= offset) {
                int bpos = buffer.position();
                int boffset = cur.offset;
                int blimit = buffer.limit();
                assert index == nextIndex || bpos == boffset;
                if (pos - offset < blimit - boffset) {
                    return buffer.get((int) (pos - offset + boffset));
                }
                if (index >= buffers.size()) {
                    assert false : "buffers exhausted";
                    throw new IndexOutOfBoundsException();
                }
                int skipped = cur.limit - cur.offset;
                offset += skipped;
                cur = buffers.get(index++);
                buffer = cur.buffer;
            }
            assert pos <= offset;
            int blimit = cur.offset;
            int boffset = cur.offset;
            while (pos < offset) {
                assert blimit == cur.limit || index == nextIndex && blimit == boffset;
                if (index <= 1) {
                    assert false : "buffers exhausted";
                    throw new IndexOutOfBoundsException();
                }
                cur = buffers.get(--index - 1);
                buffer = cur.buffer;
                int bpos = buffer.position();
                blimit = buffer.limit();
                boffset = cur.offset;
                int skipped = blimit - boffset;
                offset -= skipped;
                assert index == nextIndex || bpos == blimit;
                if (pos - offset >= 0 && pos - offset < blimit - boffset) {
                    return buffer.get((int) (pos - offset + boffset));
                }
            }
            assert false : "buffer not found";
            throw new IndexOutOfBoundsException(); // should not reach here
        }

        /**
         * {@return the current {@code ByteBuffer} in which to find
         * the byte at the current {@link #position()}}
         *
         * @param throwIfUnderflow if true, calling this method
         *        will throw {@link BufferUnderflowException} if
         *        the position is past the limit.
         *
         * @throws BufferUnderflowException if attempting to read past
         *    the limit and {@code throwIfUnderflow == true}
         */
        private ByteBuffer current(boolean throwIfUnderflow) {
            while (current == null || !current.buffer.hasRemaining()) {
                if (buffers.size() > nextIndex) {
                    if (nextIndex != 0) {
                        currentOffset = position;
                    } else {
                        currentOffset = 0;
                    }
                    current = buffers.get(nextIndex++);
                } else if (throwIfUnderflow) {
                    throw new BufferUnderflowException();
                } else {
                    return null;
                }
            }
            return current.buffer;
        }

        @Override
        public List<ByteBuffer> getAndRelease(long bytes) {
            release();
            if (bytes > limit - position) {
                throw new BufferUnderflowException();
            }
            ByteBuffer buf = current(false);
            if (buf == null || bytes == 0) return List.of();
            List<ByteBuffer> list = null;
            assert position == 0;
            assert currentOffset == 0;
            while (bytes > 0) {
                buf = current(false);
                assert nextIndex == 1;
                assert buf != null;
                assert buf.position() == current.offset;
                int remaining = buf.remaining();
                if (remaining <= bytes) {
                    var b = buffers.remove(--nextIndex);
                    assert b == current;
                    long relased = buf.remaining();
                    assert b.buffer.limit() == b.limit;
                    bytes -= relased;
                    limit -= relased;
                    readAndReleased += relased;
                    current = null;

                    // if a buffer has no remaining bytes it
                    // may be EOF. Let's not skip it here
                    // if (!buf.hasRemaining()) continue;

                    if (bytes == 0 && list == null) {
                        list = List.of(buf);
                    } else {
                        if (list == null) {
                            list = new ArrayList<>();
                        }
                        list.add(buf);
                    }
                } else {
                    var b = current;
                    long relased = bytes;
                    bytes = 0;
                    limit -= relased;
                    var pos = buf.position();
                    assert b.limit == buf.limit();
                    assert pos == b.offset;
                    var slice = buf.slice(pos, (int)relased);
                    buf.position(pos + (int) relased);
                    buffers.set(nextIndex - 1, current = new Buffer(buf));
                    readAndReleased += relased;
                    if (list != null) {
                        list.add(slice);
                    } else {
                        list = List.of(slice);
                    }
                    assert bytes == 0;
                }
            }
            return list;
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public long limit() {
            return limit;
        }

        @Override
        public void release() {
            long released = - start;
            for (var it = buffers.listIterator(); it.hasNext(); ) {
                var b = it.next();
                var buf = b.buffer;
                released += (buf.position() - b.offset);
                if (buf.hasRemaining()) {
                    it.set(new Buffer(buf));
                    break;
                }
                it.remove();
            }
            assert released == position - start
                    : "start=%s, position=%s, released=%s"
                    .formatted(start, position, released);
            readAndReleased += released;
            limit -= position;
            current = null;
            position = 0;
            currentOffset = 0;
            nextIndex = 0;
            start = 0;
        }

        @Override
        public void position(long pos) {
            if (pos > limit) throw new IllegalArgumentException(pos + " > " + limit);
            if (pos < start) throw new IllegalArgumentException(pos +  " < " + start);
            if (pos == position) return; // happy case!
            // look forward, starting from the current position:
            //    - identify the ByteBuffer that contains the requested position
            //    - set the local position in that ByteBuffer to
            //      match the requested position
            if (pos > position) {
                long skip = pos - position;
                assert skip > 0;
                while (skip > 0) {
                    var buffer = current(true);
                    int remaining = buffer.remaining();
                    if (remaining == 0) continue;
                    if (skip > remaining) {
                        // somewhere after the current buffer
                        buffer.position(buffer.limit());
                        position += remaining;
                        skip -= remaining;
                    } else {
                        // somewhere in the current buffer
                        buffer.position(buffer.position() + (int) skip);
                        position += skip;
                        skip = 0;
                    }
                }
            } else {
                // look backward, starting from the current position:
                //    - identify the ByteBuffer that contains the requested position
                //    - set the local position in that ByteBuffer to
                //      match the requested position
                long skip = pos - position;
                assert skip < 0;
                if (current == null) {
                    current(false);
                    if (current == null)
                        throw new IllegalArgumentException();
                }
                while (skip < 0) {
                    var buffer = current.buffer;
                    assert buffer.limit() == current.limit;
                    var remaining = buffer.position() - current.offset;
                    var rest = skip + remaining;
                    if (rest >= 0) {
                        // somewhere in this byte buffer, between the
                        // buffer offset and the buffer position
                        buffer.position(buffer.position() + (int)skip);
                        position += skip;
                        assert position >= start;
                        skip = 0;
                    } else {
                        // in some buffer prior to the current byte buffer
                        buffer.position(current.offset);
                        skip += remaining;
                        position -= remaining;
                        assert skip < 0;
                        assert position >= start;
                        assert nextIndex > 1;
                        current = buffers.get(--nextIndex - 1);
                        currentOffset -= current.limit - current.offset;
                        assert currentOffset >= 0;
                        assert current.buffer.position() == current.limit;
                    }
                }
            }
        }

        @Override
        public long read() {
            return readAndReleased + (position - start);
        }

        @Override
        public long offset() {
            return start;
        }

        @Override
        public void clear() {
            release();
            position(limit());
            release();
        }
    }

    /**
     * Creates a lightweight {@link SingleBufferReader} view over
     * a single {@link ByteBuffer}.
     * @param buffer a byte buffer
     * @return a lightweight {@link SingleBufferReader} view over
     *         a single {@link ByteBuffer}
     */
    public static SingleBufferReader single(ByteBuffer buffer) {
        return new SingleBufferReader(Objects.requireNonNull(buffer));
    }

    /**
     * Creates an {@linkplain #isEmpty() empty} {@link ListBuffersReader}.
     * @return an empty {@code ListBuffersReader}
     */
    public static ListBuffersReader list() {
        return new ListBuffersReader();
    }

    /**
     * Creates a {@link ListBuffersReader} with the given
     * {@code buffer}. More buffers can be later {@linkplain
     * ListBuffersReader#add(ByteBuffer) added} as they become
     * available.
     * @return a {@code ListBuffersReader}
     */
    public static ListBuffersReader list(ByteBuffer buffer) {
        return new ListBuffersReader().add(buffer);
    }

    /**
     * Creates a {@link ListBuffersReader} with the given
     * {@code buffers} list. More buffers can be later {@linkplain
     * ListBuffersReader#add(ByteBuffer) added} as they become
     * available.
     * @return a {@code ListBuffersReader}
     */
    public static ListBuffersReader list(List<ByteBuffer> buffers) {
        return new ListBuffersReader().addAll(buffers);
    }
}
