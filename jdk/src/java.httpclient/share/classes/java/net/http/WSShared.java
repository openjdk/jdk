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

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

//
//  +-----------+---------------+------------ ~ ------+
//  |  shared#1 |    shared#2   | non-shared          |
//  +-----------+---------------+------------ ~ ------+
//  |                                                 |
//  |<------------------  shared0  ---------- ~ ----->|
//
//
// Objects of the type are not thread-safe. It's the responsibility of the
// client to access shared buffers safely between threads.
//
// It would be perfect if we could extend java.nio.Buffer, but it's not an
// option since Buffer and all its descendants have package-private
// constructors.
//
abstract class WSShared<T extends Buffer> implements WSDisposable {

    protected final AtomicBoolean disposed = new AtomicBoolean();
    protected final T buffer;

    protected WSShared(T buffer) {
        this.buffer = Objects.requireNonNull(buffer);
    }

    static <T extends Buffer> WSShared<T> wrap(T buffer) {
        return new WSShared<>(buffer) {
            @Override
            WSShared<T> share(int pos, int limit) {
                throw new UnsupportedOperationException();
            }
        };
    }

    // TODO: should be a terminal operation as after it returns the buffer might
    //       have escaped (we can't protect it any more)
    public T buffer() {
        checkDisposed();
        return buffer;
    }

    abstract WSShared<T> share(final int pos, final int limit);

    WSShared<T> select(final int pos, final int limit) {
        checkRegion(pos, limit, buffer());
        select(pos, limit, buffer());
        return this;
    }

    @Override
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            throw new IllegalStateException("Has been disposed previously");
        }
    }

    int limit() {
        return buffer().limit();
    }

    WSShared<T> limit(int newLimit) {
        buffer().limit(newLimit);
        return this;
    }

    int position() {
        return buffer().position();
    }

    WSShared<T> position(int newPosition) {
        buffer().position(newPosition);
        return this;
    }

    int remaining() {
        return buffer().remaining();
    }

    boolean hasRemaining() {
        return buffer().hasRemaining();
    }

    WSShared<T> flip() {
        buffer().flip();
        return this;
    }

    WSShared<T> rewind() {
        buffer().rewind();
        return this;
    }

    WSShared<T> put(WSShared<? extends T> src) {
        put(this.buffer(), src.buffer());
        return this;
    }

    static void checkRegion(int position, int limit, Buffer buffer) {
        if (position < 0 || position > buffer.capacity()) {
            throw new IllegalArgumentException("position: " + position);
        }
        if (limit < 0 || limit > buffer.capacity()) {
            throw new IllegalArgumentException("limit: " + limit);
        }
        if (limit < position) {
            throw new IllegalArgumentException
                    ("limit < position: limit=" + limit + ", position=" + position);
        }
    }

    void select(int newPos, int newLim, Buffer buffer) {
        int oldPos = buffer.position();
        int oldLim = buffer.limit();
        assert 0 <= oldPos && oldPos <= oldLim && oldLim <= buffer.capacity();
        if (oldLim <= newPos) {
            buffer().limit(newLim).position(newPos);
        } else {
            buffer.position(newPos).limit(newLim);
        }
    }

    // The same as dst.put(src)
    static <T extends Buffer> T put(T dst, T src) {
        if (dst instanceof ByteBuffer) {
            ((ByteBuffer) dst).put((ByteBuffer) src);
        } else if (dst instanceof CharBuffer) {
            ((CharBuffer) dst).put((CharBuffer) src);
        } else {
            // We don't work with buffers of other types
            throw new IllegalArgumentException();
        }
        return dst;
    }

    // TODO: Remove when JDK-8150785 has been done
    @SuppressWarnings("unchecked")
    static <T extends Buffer> T slice(T buffer) {
        if (buffer instanceof ByteBuffer) {
            return (T) ((ByteBuffer) buffer).slice();
        } else if (buffer instanceof CharBuffer) {
            return (T) ((CharBuffer) buffer).slice();
        } else {
            // We don't work with buffers of other types
            throw new IllegalArgumentException();
        }
    }

    // TODO: Remove when JDK-8150785 has been done
    @SuppressWarnings("unchecked")
    static <T extends Buffer> T duplicate(T buffer) {
        if (buffer instanceof ByteBuffer) {
            return (T) ((ByteBuffer) buffer).duplicate();
        } else if (buffer instanceof CharBuffer) {
            return (T) ((CharBuffer) buffer).duplicate();
        } else {
            // We don't work with buffers of other types
            throw new IllegalArgumentException();
        }
    }

    @Override
    public String toString() {
        return super.toString() + "[" + WSUtils.toString(buffer()) + "]";
    }

    private void checkDisposed() {
        if (disposed.get()) {
            throw new IllegalStateException("Has been disposed previously");
        }
    }
}
