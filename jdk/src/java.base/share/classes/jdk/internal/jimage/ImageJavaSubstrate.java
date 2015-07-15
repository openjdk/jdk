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
package jdk.internal.jimage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.READ;
import jdk.internal.jimage.decompressor.Decompressor;

final class ImageJavaSubstrate implements ImageSubstrate {

    private final String imagePath;
    private final ByteOrder byteOrder;
    private final FileChannel channel;
    private final ImageHeader header;
    private final long indexSize;
    private final int[] redirect;
    private final int[] offsets;
    private final byte[] locations;
    private final byte[] strings;

    private final Decompressor decompressor = new Decompressor();

  private ImageJavaSubstrate(String imagePath, ByteOrder byteOrder)
          throws IOException {
        this.imagePath = imagePath;
        this.byteOrder = byteOrder;
        channel = FileChannel.open(Paths.get(imagePath), READ);

        int headerSize = ImageHeader.getHeaderSize();
        ByteBuffer buffer = getIndexBuffer(0, headerSize);
        header = ImageHeader.readFrom(buffer.asIntBuffer());

        if (header.getMagic() != ImageHeader.MAGIC ||
            header.getMajorVersion() != ImageHeader.MAJOR_VERSION ||
            header.getMinorVersion() != ImageHeader.MINOR_VERSION) {
            throw new IOException("Image not found \"" + imagePath + "\"");
        }

        indexSize = header.getIndexSize();

        redirect = readIntegers(header.getRedirectOffset(),
                                header.getRedirectSize());
        offsets = readIntegers(header.getOffsetsOffset(),
                               header.getOffsetsSize());
        locations = readBytes(header.getLocationsOffset(),
                              header.getLocationsSize());
        strings = readBytes(header.getStringsOffset(),
                            header.getStringsSize());
    }

    static ImageSubstrate openImage(String imagePath, ByteOrder byteOrder)
            throws IOException {
        return new ImageJavaSubstrate(imagePath, byteOrder);
    }

    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException ex) {
            // Mostly harmless
        }
    }

    @Override
    public boolean supportsDataBuffer() {
        return false;
    }

    private int[] readIntegers(long offset, long size) {
        assert size < Integer.MAX_VALUE;
        IntBuffer buffer = readBuffer(offset, size).asIntBuffer();
        int[] integers = new int[(int)size / 4];
        buffer.get(integers);

        return integers;
    }

    private byte[] readBytes(long offset, long size) {
        assert size < Integer.MAX_VALUE;
        ByteBuffer buffer = readBuffer(offset, size);
        byte[] bytes = new byte[(int)size];
        buffer.get(bytes);

        return bytes;
    }

    private ByteBuffer readBuffer(long offset, long size) {
        assert size < Integer.MAX_VALUE;
        ByteBuffer buffer = ByteBuffer.allocate((int)size);
        buffer.order(byteOrder);

        if (!readBuffer(buffer, offset, size)) {
            return null;
        }

        return buffer;
    }

    private boolean readBuffer(ByteBuffer buffer, long offset, long size) {
        assert size < Integer.MAX_VALUE;
        assert buffer.limit() == size;
        int read = 0;

        try {
            read = channel.read(buffer, offset);
            buffer.rewind();
        } catch (IOException ex) {
            // fall thru
        }

        return read == size;
    }

    @Override
    public ByteBuffer getIndexBuffer(long offset, long size) {
        assert size < Integer.MAX_VALUE;
        return readBuffer(offset, size);
    }

    @Override
    public ByteBuffer getDataBuffer(long offset, long size) {
        assert size < Integer.MAX_VALUE;
        return getIndexBuffer(indexSize + offset, size);
    }

    @Override
    public boolean read(long offset,
                 ByteBuffer compressedBuffer, long compressedSize,
                 ByteBuffer uncompressedBuffer, long uncompressedSize) {
        assert compressedSize < Integer.MAX_VALUE;
        assert uncompressedSize < Integer.MAX_VALUE;
        boolean isRead = readBuffer(compressedBuffer,
                                    indexSize + offset, compressedSize);
        if (isRead) {
            byte[] bytesIn = new byte[(int)compressedSize];
            compressedBuffer.get(bytesIn);
            byte[] bytesOut;
            try {
                bytesOut = decompressor.decompressResource(byteOrder, (int strOffset) -> {
                    return new UTF8String(getStringBytes(strOffset)).toString();
                }, bytesIn);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            uncompressedBuffer.put(bytesOut);
            uncompressedBuffer.rewind();
        }

        return isRead;
    }

    @Override
    public boolean read(long offset,
                 ByteBuffer uncompressedBuffer, long uncompressedSize) {
        assert uncompressedSize < Integer.MAX_VALUE;
        boolean isRead = readBuffer(uncompressedBuffer,
                                    indexSize + offset, uncompressedSize);

        return isRead;
    }

    @Override
    public byte[] getStringBytes(int offset) {
        if (offset == 0) {
            return new byte[0];
        }

        int length = strings.length - offset;

        for (int i = offset; i < strings.length; i++) {
            if (strings[i] == 0) {
                length = i - offset;
                break;
            }
        }

        byte[] bytes = new byte[length];
        System.arraycopy(strings, offset, bytes, 0, length);

        return bytes;
    }

    @Override
    public long[] getAttributes(int offset) {
        return ImageLocationBase.decompress(locations, offset);
    }

    @Override
    public ImageLocation findLocation(UTF8String name, ImageStringsReader strings) {
        int count = header.getTableLength();
        int index = redirect[name.hashCode() % count];

        if (index < 0) {
            index = -index - 1;
        } else {
            index = name.hashCode(index) % count;
        }

        long[] attributes = getAttributes(offsets[index]);

        ImageLocation imageLocation = new ImageLocation(attributes, strings);

        if (!imageLocation.verify(name)) {
            return null;
        }

        return imageLocation;
   }

    @Override
    public int[] attributeOffsets() {
        return offsets;
    }
}
