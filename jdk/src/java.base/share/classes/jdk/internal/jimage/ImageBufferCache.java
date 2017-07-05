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

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
class ImageBufferCache {
    private static final int MAX_FREE_BUFFERS = 3;
    private static final int LARGE_BUFFER = 0x10000;
    private static final ThreadLocal<ArrayList<ImageBufferCache>>
            threadLocal = new ThreadLocal<>();

    private final ByteBuffer buffer;
    private boolean isUsed;

    static ByteBuffer getBuffer(long size) {
        if (size < 0 || Integer.MAX_VALUE < size) {
            throw new IndexOutOfBoundsException("size");
        }

        ByteBuffer buffer = null;

        if (size > LARGE_BUFFER) {
            buffer = ByteBuffer.allocateDirect((int)((size + 0xFFF) & ~0xFFF));
        } else {
            ArrayList<ImageBufferCache> buffers = threadLocal.get();

            if (buffers == null) {
                buffers = new ArrayList<>(MAX_FREE_BUFFERS);
                threadLocal.set(buffers);
            }

            int i = 0, j = buffers.size();
            for (ImageBufferCache imageBuffer : buffers) {
                if (size <= imageBuffer.capacity()) {
                    j = i;

                    if (!imageBuffer.isUsed) {
                        imageBuffer.isUsed = true;
                        buffer = imageBuffer.buffer;

                        break;
                    }
                } else {
                    break;
                }

                i++;
            }

            if (buffer == null) {
                ImageBufferCache imageBuffer = new ImageBufferCache((int)size);
                buffers.add(j, imageBuffer);
                buffer = imageBuffer.buffer;
            }
        }

        buffer.rewind();
        buffer.limit((int)size);

        return buffer;
    }

    static void releaseBuffer(ByteBuffer buffer) {
        ArrayList<ImageBufferCache> buffers = threadLocal.get();

        if (buffers == null) {
            return;
        }

        if (buffer.capacity() > LARGE_BUFFER) {
            return;
        }

        int i = 0, j = buffers.size();
        for (ImageBufferCache imageBuffer : buffers) {
            if (!imageBuffer.isUsed) {
                j = Math.min(j, i);
            }

            if (imageBuffer.buffer == buffer) {
                imageBuffer.isUsed = false;
                j = Math.min(j, i);

                break;
            }
        }

        if (buffers.size() > MAX_FREE_BUFFERS && j != buffers.size()) {
            buffers.remove(j);
        }
    }

    private ImageBufferCache(int needed) {
        this.buffer = ByteBuffer.allocateDirect((needed + 0xFFF) & ~0xFFF);
        this.isUsed = true;
        this.buffer.limit(needed);
    }

    private long capacity() {
        return buffer.capacity();
    }
}
