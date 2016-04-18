/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.jimage;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrt-fs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
class ImageBufferCache {
    private static final int MAX_CACHED_BUFFERS = 3;
    private static final int LARGE_BUFFER = 0x10000;
    private static final ThreadLocal<BufferReference[]> CACHE =
        new ThreadLocal<BufferReference[]>() {
            @Override
            protected BufferReference[] initialValue() {
                // 1 extra slot to simplify logic of releaseBuffer()
                return new BufferReference[MAX_CACHED_BUFFERS + 1];
            }
        };

    private static ByteBuffer allocateBuffer(long size) {
        return ByteBuffer.allocateDirect((int)((size + 0xFFF) & ~0xFFF));
    }

    static ByteBuffer getBuffer(long size) {
        if (size < 0 || Integer.MAX_VALUE < size) {
            throw new IndexOutOfBoundsException("size");
        }

        ByteBuffer result = null;

        if (size > LARGE_BUFFER) {
            result = allocateBuffer(size);
        } else {
            BufferReference[] cache = CACHE.get();

            // buffers are ordered by decreasing capacity
            // cache[MAX_CACHED_BUFFERS] is always null
            for (int i = MAX_CACHED_BUFFERS - 1; i >= 0; i--) {
                BufferReference reference = cache[i];

                if (reference != null) {
                    ByteBuffer buffer = reference.get();

                    if (buffer != null && size <= buffer.capacity()) {
                        cache[i] = null;
                        result = buffer;
                        result.rewind();
                        break;
                    }
                }
            }

            if (result == null) {
                result = allocateBuffer(size);
            }
        }

        result.limit((int)size);

        return result;
    }

    static void releaseBuffer(ByteBuffer buffer) {
        if (buffer.capacity() > LARGE_BUFFER) {
            return;
        }

        BufferReference[] cache = CACHE.get();

        // expunge cleared BufferRef(s)
        for (int i = 0; i < MAX_CACHED_BUFFERS; i++) {
            BufferReference reference = cache[i];
            if (reference != null && reference.get() == null) {
                cache[i] = null;
            }
        }

        // insert buffer back with new BufferRef wrapping it
        cache[MAX_CACHED_BUFFERS] = new BufferReference(buffer);
        Arrays.sort(cache, DECREASING_CAPACITY_NULLS_LAST);
        // squeeze the smallest one out
        cache[MAX_CACHED_BUFFERS] = null;
    }

    private static Comparator<BufferReference> DECREASING_CAPACITY_NULLS_LAST =
        new Comparator<BufferReference>() {
            @Override
            public int compare(BufferReference br1, BufferReference br2) {
                return Integer.compare(br2 == null ? 0 : br2.capacity,
                                       br1 == null ? 0 : br1.capacity);
            }
        };

    private static class BufferReference extends WeakReference<ByteBuffer> {
        // saved capacity so that DECREASING_CAPACITY_NULLS_LAST comparator
        // is stable in the presence of GC clearing the WeakReference concurrently
        final int capacity;

        BufferReference(ByteBuffer buffer) {
            super(buffer);
            capacity = buffer.capacity();
        }
    }
}
