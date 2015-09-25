/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import sun.misc.JavaNioAccess;
import sun.misc.SharedSecrets;

public final class ImageNativeSubstrate implements ImageSubstrate {
    static {
        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    System.loadLibrary("jimage");
                    return null;
                }
            });
     }

    private static final JavaNioAccess NIOACCESS =
            SharedSecrets.getJavaNioAccess();

    private final long id;
    private final long indexAddress;
    private final long dataAddress;

    static native long openImage(String imagePath, boolean bigEndian);
    static native void closeImage(long id);
    static native long getIndexAddress(long id);
    static native long getDataAddress(long id);
    static native boolean readCompressed(long id, long offset,
            ByteBuffer compressedBuffer, long compressedSize,
            ByteBuffer uncompressedBuffer, long uncompressedSize);
    static native boolean read(long id, long offset,
            ByteBuffer uncompressedBuffer, long uncompressedSize);
    static native byte[] getStringBytes(long id, int offset);
    static native long[] getAttributes(long id, int offset);
    static native long[] findAttributes(long id, byte[] path);
    static native int[] attributeOffsets(long id);

    public static native long JIMAGE_Open(String path) throws IOException;
    public static native void JIMAGE_Close(long jimageHandle);
    public static native long JIMAGE_FindResource(long jimageHandle,
                    String moduleName, String Version, String path,
                    long[] size);
    public static native long JIMAGE_GetResource(long jimageHandle,
                    long locationHandle, byte[] buffer, long size);
    // Get an array of names that match; return the count found upto array size
    public static native int JIMAGE_Resources(long jimageHandle,
                    String[] outputNames);
    // Return the module name for the package
    public static native String JIMAGE_PackageToModule(long imageHandle,
                    String packageName);

    static ByteBuffer newDirectByteBuffer(long address, long capacity) {
        assert capacity < Integer.MAX_VALUE;
        return NIOACCESS.newDirectByteBuffer(address, (int)capacity, null);
    }

    private ImageNativeSubstrate(long id) {
        this.id = id;
        this.indexAddress = getIndexAddress(id);
        this.dataAddress = getDataAddress(id);
    }

    static ImageSubstrate openImage(String imagePath, ByteOrder byteOrder)
            throws IOException {
        long id = openImage(imagePath, byteOrder == ByteOrder.BIG_ENDIAN);

        if (id == 0) {
             throw new IOException("Image not found \"" + imagePath + "\"");
        }

        return new ImageNativeSubstrate(id);
    }

    @Override
    public void close() {
        closeImage(id);
    }

    @Override
    public ByteBuffer getIndexBuffer(long offset, long size) {
        return newDirectByteBuffer(indexAddress + offset, size);
    }

    @Override
    public ByteBuffer getDataBuffer(long offset, long size) {
        return dataAddress != 0 ?
                newDirectByteBuffer(dataAddress + offset, size) : null;
    }

    @Override
    public boolean supportsDataBuffer() {
        return dataAddress != 0;
    }

    @Override
    public boolean read(long offset,
                 ByteBuffer compressedBuffer, long compressedSize,
                 ByteBuffer uncompressedBuffer, long uncompressedSize) {
        return readCompressed(id, offset,
                    compressedBuffer, compressedSize,
                    uncompressedBuffer, uncompressedSize);
    }

    @Override
    public boolean read(long offset,
                 ByteBuffer uncompressedBuffer, long uncompressedSize) {
        return read(id, offset, uncompressedBuffer, uncompressedSize);
    }

    @Override
    public byte[] getStringBytes(int offset) {
        byte[] ret = getStringBytes(id, offset);
        if (ret == null) {
            throw new OutOfMemoryError("Error accessing array at offset "
                    + offset);
        }
        return ret;
    }

    @Override
    public long[] getAttributes(int offset) {
        return getAttributes(id, offset);
    }

    @Override
    public ImageLocation findLocation(UTF8String name, ImageStringsReader strings) {
        long[] attributes = findAttributes(id, name.getBytes());

        return attributes != null ? new ImageLocation(attributes, strings) : null;
    }

    @Override
    public int[] attributeOffsets() {
        return attributeOffsets(id);
    }
}
