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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Comparator;
import java.util.stream.IntStream;

public class BasicImageReader implements AutoCloseable {
    private final String imagePath;
    private final ImageSubstrate substrate;
    private final ByteOrder byteOrder;
    private final ImageStringsReader strings;

    protected BasicImageReader(String imagePath, ByteOrder byteOrder)
            throws IOException {
        this.imagePath = imagePath;
        this.substrate = openImageSubstrate(imagePath, byteOrder);
        this.byteOrder = byteOrder;
        this.strings = new ImageStringsReader(this);
    }

    protected BasicImageReader(String imagePath) throws IOException {
        this(imagePath, ByteOrder.nativeOrder());
    }

    private static ImageSubstrate openImageSubstrate(String imagePath, ByteOrder byteOrder)
            throws IOException {
        ImageSubstrate substrate;

        try {
            substrate = ImageNativeSubstrate.openImage(imagePath, byteOrder);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError ex) {
            substrate = ImageJavaSubstrate.openImage(imagePath, byteOrder);
        }

        return substrate;
    }

    public static BasicImageReader open(String imagePath) throws IOException {
        return new BasicImageReader(imagePath, ByteOrder.nativeOrder());
    }

    public static void releaseByteBuffer(ByteBuffer buffer) {
        ImageBufferCache.releaseBuffer(buffer);
    }

    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public String imagePath() {
        return imagePath;
    }

    public String imagePathName() {
        int slash = imagePath().lastIndexOf(File.separator);

        if (slash != -1) {
            return imagePath().substring(slash + 1);
        }

        return imagePath();
    }

    public boolean isOpen() {
        return true;
    }

    public void close() throws IOException {
        substrate.close();
    }

    public ImageHeader getHeader() throws IOException {
        return ImageHeader.readFrom(
                getIndexIntBuffer(0, ImageHeader.getHeaderSize()));
    }

    public ImageStringsReader getStrings() {
        return strings;
    }

    public ImageLocation findLocation(String name) {
        return findLocation(new UTF8String(name));
    }

    public ImageLocation findLocation(byte[] name) {
        return findLocation(new UTF8String(name));
    }

    public synchronized ImageLocation findLocation(UTF8String name) {
        return substrate.findLocation(name, strings);
    }

    public String[] getEntryNames() {
        return IntStream.of(substrate.attributeOffsets())
                        .filter(o -> o != 0)
                        .mapToObj(o -> ImageLocation.readFrom(this, o).getFullNameString())
                        .sorted()
                        .toArray(String[]::new);
    }

    protected ImageLocation[] getAllLocations(boolean sorted) {
        return IntStream.of(substrate.attributeOffsets())
                        .filter(o -> o != 0)
                        .mapToObj(o -> ImageLocation.readFrom(this, o))
                        .sorted(Comparator.comparing(ImageLocation::getFullNameString))
                        .toArray(ImageLocation[]::new);
    }

    private IntBuffer getIndexIntBuffer(long offset, long size)
            throws IOException {
        ByteBuffer buffer = substrate.getIndexBuffer(offset, size);
        buffer.order(byteOrder);

        return buffer.asIntBuffer();
    }

    ImageLocation getLocation(int offset) {
        return ImageLocation.readFrom(this, offset);
    }

    public long[] getAttributes(int offset) {
        return substrate.getAttributes(offset);
    }

    public String getString(int offset) {
        return getUTF8String(offset).toString();
    }

    public UTF8String getUTF8String(int offset) {
        return new UTF8String(substrate.getStringBytes(offset));
    }

    private byte[] getBufferBytes(ByteBuffer buffer, long size) {
        assert size < Integer.MAX_VALUE;
        byte[] bytes = new byte[(int)size];
        buffer.get(bytes);

        return bytes;
    }

    private byte[] getBufferBytes(long offset, long size) {
        ByteBuffer buffer = substrate.getDataBuffer(offset, size);

        return getBufferBytes(buffer, size);
    }

    public byte[] getResource(ImageLocation loc) {
        long offset = loc.getContentOffset();
        long compressedSize = loc.getCompressedSize();
        long uncompressedSize = loc.getUncompressedSize();
        assert compressedSize < Integer.MAX_VALUE;
        assert uncompressedSize < Integer.MAX_VALUE;

        if (substrate.supportsDataBuffer() && compressedSize == 0) {
            return getBufferBytes(offset, uncompressedSize);
        }

        ByteBuffer uncompressedBuffer = ImageBufferCache.getBuffer(uncompressedSize);
        boolean isRead;

        if (compressedSize != 0) {
            ByteBuffer compressedBuffer = ImageBufferCache.getBuffer(compressedSize);
            isRead = substrate.read(offset, compressedBuffer, compressedSize,
                                          uncompressedBuffer, uncompressedSize);
            ImageBufferCache.releaseBuffer(compressedBuffer);
        } else {
            isRead = substrate.read(offset, uncompressedBuffer, uncompressedSize);
        }

        byte[] bytes = isRead ? getBufferBytes(uncompressedBuffer,
                                               uncompressedSize) : null;

        ImageBufferCache.releaseBuffer(uncompressedBuffer);

        return bytes;
    }

    public byte[] getResource(String name) {
        ImageLocation location = findLocation(name);

        return location != null ? getResource(location) : null;
    }

    public ByteBuffer getResourceBuffer(ImageLocation loc) {
        long offset = loc.getContentOffset();
        long compressedSize = loc.getCompressedSize();
        long uncompressedSize = loc.getUncompressedSize();
        assert compressedSize < Integer.MAX_VALUE;
        assert uncompressedSize < Integer.MAX_VALUE;

        if (substrate.supportsDataBuffer() && compressedSize == 0) {
            return substrate.getDataBuffer(offset, uncompressedSize);
        }

        ByteBuffer uncompressedBuffer = ImageBufferCache.getBuffer(uncompressedSize);
        boolean isRead;

        if (compressedSize != 0) {
            ByteBuffer compressedBuffer = ImageBufferCache.getBuffer(compressedSize);
            isRead = substrate.read(offset, compressedBuffer, compressedSize,
                                          uncompressedBuffer, uncompressedSize);
            ImageBufferCache.releaseBuffer(compressedBuffer);
        } else {
            isRead = substrate.read(offset, uncompressedBuffer, uncompressedSize);
        }

        if (isRead) {
            return uncompressedBuffer;
        } else {
            ImageBufferCache.releaseBuffer(uncompressedBuffer);

            return null;
        }
    }

    public ByteBuffer getResourceBuffer(String name) {
        ImageLocation location = findLocation(name);

        return location != null ? getResourceBuffer(location) : null;
    }

    public InputStream getResourceStream(ImageLocation loc) {
        byte[] bytes = getResource(loc);

        return new ByteArrayInputStream(bytes);
    }

    public InputStream getResourceStream(String name) {
        ImageLocation location = findLocation(name);

        return location != null ? getResourceStream(location) : null;
    }
}
