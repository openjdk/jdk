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
package jdk.internal.jimage.decompressor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import jdk.internal.jimage.decompressor.ResourceDecompressor.StringsProvider;

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
    private static final int MAGIC = 0xCAFEFAFA;

    private static final int TINY_SIZE = 12;
    private static final int TINY_MAGIC = 0xCAFEDADA;
    private final long uncompressedSize;
    private final long compressedSize;
    private final int decompressorNameOffset;
    private final int contentOffset;
    private final boolean isTerminal;

    public CompressedResourceHeader(long compressedSize,
            long uncompressedSize, int decompressorNameOffset, int contentOffset,
            boolean isTerminal) {
        this.compressedSize = compressedSize;
        this.uncompressedSize = uncompressedSize;
        this.decompressorNameOffset = decompressorNameOffset;
        this.contentOffset = contentOffset;
        this.isTerminal = isTerminal;
    }

    public boolean isTerminal() {
        return isTerminal;
    }

    public int getDecompressorNameOffset() {
        return decompressorNameOffset;
    }

    public int getContentOffset() {
        return contentOffset;
    }

    public String getStoredContent(StringsProvider provider) {
        Objects.requireNonNull(provider);
        if (contentOffset == -1) {
            return null;
        }
        return provider.getString(contentOffset);
    }

    public long getUncompressedSize() {
        return uncompressedSize;
    }

    public long getResourceSize() {
        return compressedSize;
    }

    public byte[] getBytes(ByteOrder order) {
        Objects.requireNonNull(order);
        if (compressedSize <= Short.MAX_VALUE &&
                uncompressedSize <= Short.MAX_VALUE &&
                decompressorNameOffset <= Short.MAX_VALUE &&
                contentOffset <= Short.MAX_VALUE) {
            ByteBuffer buffer = ByteBuffer.allocate(TINY_SIZE);
            buffer.order(order);
            buffer.putInt(TINY_MAGIC);
            buffer.putShort((short)(compressedSize | (isTerminal ? 0x8000 : 0)));
            buffer.putShort((short)uncompressedSize);
            buffer.putShort((short)decompressorNameOffset);
            buffer.putShort((short)contentOffset);
            return buffer.array();
        } else {
            ByteBuffer buffer = ByteBuffer.allocate(SIZE);
            buffer.order(order);
            buffer.putInt(MAGIC);
            buffer.putLong(compressedSize);
            buffer.putLong(uncompressedSize);
            buffer.putInt(decompressorNameOffset);
            buffer.putInt(contentOffset);
            buffer.put(isTerminal ? (byte) 1 : (byte) 0);
            return buffer.array();
        }
    }

    public static CompressedResourceHeader readFromResource(ByteOrder order,
            byte[] resource) {
        Objects.requireNonNull(order);
        Objects.requireNonNull(resource);
        if (resource.length < TINY_SIZE) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(resource, 0, Math.min(resource.length, SIZE));
        buffer.order(order);
        int magic = buffer.getInt();
        if (magic == TINY_MAGIC) {
            short packedSizeTerminal = buffer.getShort();
            long size = packedSizeTerminal & 0x7FFFL;
            boolean isTerminal = (packedSizeTerminal & 0x8000) == 0x8000;
            long uncompressedSize = buffer.getShort();
            int decompressorNameOffset = buffer.getShort();
            int contentIndex = buffer.getShort();
            return new CompressedResourceHeader(size, uncompressedSize,
                    decompressorNameOffset, contentIndex, isTerminal);
        } else if (magic == MAGIC) {
            long size = buffer.getLong();
            long uncompressedSize = buffer.getLong();
            int decompressorNameOffset = buffer.getInt();
            int contentIndex = buffer.getInt();
            byte isTerminal = buffer.get();
            return new CompressedResourceHeader(size, uncompressedSize,
                    decompressorNameOffset, contentIndex, isTerminal == 1);
        }
        return null;
    }

    public int getHeaderSize() {
        if (compressedSize <= Short.MAX_VALUE &&
                uncompressedSize <= Short.MAX_VALUE &&
                decompressorNameOffset <= Short.MAX_VALUE &&
                contentOffset <= Short.MAX_VALUE) {
            return TINY_SIZE;
        } else {
            return SIZE;
        }
    }
}
