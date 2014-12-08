/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jimage.concurrent;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

import jdk.internal.jimage.PReader;

import sun.misc.Unsafe;

/**
 * A PReader implementation that supports concurrent pread operations.
 */
public class ConcurrentPReader extends PReader {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long BA_OFFSET = (long) UNSAFE.arrayBaseOffset(byte[].class);

    /**
     * A temporary buffer that is cached on a per-thread basis.
     */
    private static class TemporaryBuffer {
        static final ThreadLocal<TemporaryBuffer> CACHED_BUFFER =
             new ThreadLocal<TemporaryBuffer>() {
                @Override
                protected TemporaryBuffer initialValue() { return null; }
             };

        static final TemporaryBuffer NOT_AVAILABLE = new TemporaryBuffer(0L, 0);

        final long address;
        final int size;

        TemporaryBuffer(long address, int size) {
            this.address = address;
            this.size = size;
        }

        long address() { return address; }
        int size() { return size; }

        /**
         * Returns the {@code TemporaryBuffer} for the current thread. The buffer
         * is guaranteed to be of at least the given size. Returns {@code null}
         * if a buffer cannot be cached for this thread.
         */
        static TemporaryBuffer get(int len) {
            TemporaryBuffer buffer = CACHED_BUFFER.get();

            // cached buffer large enough?
            if (buffer != null && buffer.size() >= len) {
                return buffer;
            }

            // if this is an InnocuousThread then don't return anything
            if (buffer == NOT_AVAILABLE)
                return null;

            if (buffer != null) {
                // replace buffer in cache with a larger buffer
                long originalAddress = buffer.address();
                long address = UNSAFE.allocateMemory(len);
                buffer = new TemporaryBuffer(address, len);
                CACHED_BUFFER.set(buffer);
                UNSAFE.freeMemory(originalAddress);
            } else {
                // first usage.
                if (Thread.currentThread() instanceof sun.misc.InnocuousThread) {
                    buffer = NOT_AVAILABLE;
                } else {
                    long address = UNSAFE.allocateMemory(len);
                    buffer = new TemporaryBuffer(address, len);
                }
                CACHED_BUFFER.set(buffer);
            }
            return buffer;
        }
    }

    private final FileDescriptor fd;

    private ConcurrentPReader(FileInputStream fis) throws IOException {
        super(fis.getChannel());
        this.fd = fis.getFD();
    }

    public ConcurrentPReader(String file) throws IOException {
        this(new FileInputStream(file));
    }

    @Override
    public byte[] read(int len, long position) throws IOException {
        // need a temporary area of memory to read into
        TemporaryBuffer buffer = TemporaryBuffer.get(len);
        long address;
        if (buffer == null) {
            address = UNSAFE.allocateMemory(len);
        } else {
            address = buffer.address();
        }
        try {
            int n = pread(fd, address, len, position);
            if (n != len)
                throw new InternalError("short read, not handled yet");
            byte[] result = new byte[n];
            UNSAFE.copyMemory(null, address, result, BA_OFFSET, len);
            return result;
        } finally {
            if (buffer == null) {
                UNSAFE.freeMemory(address);
            }
        }
    }

    private static native int pread(FileDescriptor fd, long address, int len, long pos)
        throws IOException;

    private static native void initIDs();

    static {
        System.loadLibrary("java");
        initIDs();
    }
}
