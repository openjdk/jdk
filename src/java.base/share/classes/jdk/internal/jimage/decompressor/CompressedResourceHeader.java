/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.jimage.decompressor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 *
 * A resource header for compressed resource. This class is handled internally,
 * you don't have to add header to the resource, headers are added automatically
 * for compressed resources.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public final class CompressedResourceHeader {

    private static final int SIZE = 29;
    public static final int MAGIC = 0xCAFEFAFA;

    // Standard header offsets
    private static final int MAGIC_OFFSET = 0; // 4 bytes
    private static final int COMPRESSED_OFFSET = 4; // 8 bytes
    private static final int UNCOMPRESSED_OFFSET = 12; // 8 bytes
    private static final int DECOMPRESSOR_NAME_OFFSET = 20; // 4 bytes, followed by 4 byte gap
    private static final int IS_TERMINAL_OFFSET = 28; // 1 byte

    private final long uncompressedSize;
    private final long compressedSize;
    private final int decompressorNameOffset;
    private final boolean isTerminal;

    public CompressedResourceHeader(long compressedSize,
            long uncompressedSize, int decompressorNameOffset,
            boolean isTerminal) {
        this.compressedSize = compressedSize;
        this.uncompressedSize = uncompressedSize;
        this.decompressorNameOffset = decompressorNameOffset;
        this.isTerminal = isTerminal;
    }

    public boolean isTerminal() {
        return isTerminal;
    }

    public int getDecompressorNameOffset() {
        return decompressorNameOffset;
    }

    public long getUncompressedSize() {
        return uncompressedSize;
    }

    public long getResourceSize() {
        return compressedSize;
    }

    public byte[] getBytes(ByteOrder order) {
        Objects.requireNonNull(order);
        ByteBuffer buffer = ByteBuffer.allocate(SIZE);
        buffer.order(order);
        buffer.putInt(MAGIC);
        buffer.putLong(compressedSize);
        buffer.putLong(uncompressedSize);
        buffer.putInt(decompressorNameOffset);
        // Compatibility
        buffer.putInt(-1);
        buffer.put(isTerminal ? (byte)1 : (byte)0);
        return buffer.array();
    }

    public static int getSize() {
        return SIZE;
    }

    public static CompressedResourceHeader readFromResource(ByteOrder order,
            byte[] resource) {
        Objects.requireNonNull(order);
        Objects.requireNonNull(resource);
        if (resource.length < getSize()) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(resource, 0, SIZE);
        buffer.order(order);
        int magic = buffer.getInt(MAGIC_OFFSET);
        if (magic != MAGIC) {
            return null;
        }
        long size = buffer.getLong(COMPRESSED_OFFSET);
        long uncompressedSize = buffer.getLong(UNCOMPRESSED_OFFSET);
        int decompressorNameOffset = buffer.getInt(DECOMPRESSOR_NAME_OFFSET);
        // skip unused 'contentOffset' int
        byte isTerminal = buffer.get(IS_TERMINAL_OFFSET);
        return new CompressedResourceHeader(size, uncompressedSize,
                decompressorNameOffset, isTerminal == 1);
    }
}
