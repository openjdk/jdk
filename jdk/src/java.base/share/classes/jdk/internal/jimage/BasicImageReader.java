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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BasicImageReader {
    private final String imagePath;
    private final PReader preader;
    private final ByteOrder byteOrder;
    private final ImageHeader header;
    private final int indexSize;
    private final IntBuffer redirectBuffer;
    private final IntBuffer offsetsBuffer;
    private final ByteBuffer locationsBuffer;
    private final ByteBuffer stringsBuffer;
    private final ImageStrings strings;

    protected BasicImageReader(String imagePath, ByteOrder byteOrder) throws IOException {
        this.imagePath = imagePath;
        this.preader = PReader.open(imagePath);
        this.byteOrder = byteOrder;
        this.header = ImageHeader.readFrom(byteOrder, getIntBuffer(0, ImageHeader.getHeaderSize()));
        this.indexSize = header.getIndexSize();
        this.redirectBuffer = getIntBuffer(header.getRedirectOffset(), header.getRedirectSize());
        this.offsetsBuffer = getIntBuffer(header.getOffsetsOffset(), header.getOffsetsSize());
        this.locationsBuffer = getByteBuffer(header.getLocationsOffset(), header.getLocationsSize());
        this.stringsBuffer = getByteBuffer(header.getStringsOffset(), header.getStringsSize());
        this.strings = new ImageStrings(new ImageStream(stringsBuffer));
    }

    protected BasicImageReader(String imagePath) throws IOException {
        this(imagePath, ByteOrder.nativeOrder());
    }

    public static BasicImageReader open(String imagePath) throws IOException {
        return new BasicImageReader(imagePath, ByteOrder.nativeOrder());
    }

    public String imagePath() {
        return imagePath;
    }

    public boolean isOpen() {
        return preader.isOpen();
    }

    public void close() throws IOException {
        preader.close();
    }

    public ImageHeader getHeader() {
        return header;
    }

    public ImageLocation findLocation(String name) {
        return findLocation(new UTF8String(name));
    }

    public ImageLocation findLocation(byte[] name) {
        return findLocation(new UTF8String(name));
    }

    public synchronized ImageLocation findLocation(UTF8String name) {
        int count = header.getLocationCount();
        int hash = name.hashCode() % count;
        int redirect = getRedirect(hash);

        if (redirect == 0) {
            return null;
        }

        int index;

        if (redirect < 0) {
            // If no collision.
            index = -redirect - 1;
        } else {
            // If collision, recompute hash code.
            index = name.hashCode(redirect) % count;
        }

        int offset = getOffset(index);

        if (offset == 0) {
            return null;
        }

        ImageLocation location = getLocation(offset);

        return location.verify(name) ? location : null;
    }

    public String[] getEntryNames() {
        return getEntryNames(true);
    }

    public String[] getEntryNames(boolean sorted) {
        int count = header.getLocationCount();
        List<String> list = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            int offset = getOffset(i);

            if (offset != 0) {
                ImageLocation location = ImageLocation.readFrom(locationsBuffer, offset, strings);
                list.add(location.getFullnameString());
            }
        }

        String[] array = list.toArray(new String[0]);

        if (sorted) {
            Arrays.sort(array);
        }

        return array;
    }

    protected ImageLocation[] getAllLocations(boolean sorted) {
        int count = header.getLocationCount();
        List<ImageLocation> list = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            int offset = getOffset(i);

            if (offset != 0) {
                ImageLocation location = ImageLocation.readFrom(locationsBuffer, offset, strings);
                list.add(location);
            }
        }

        ImageLocation[] array = list.toArray(new ImageLocation[0]);

        if (sorted) {
            Arrays.sort(array, (ImageLocation loc1, ImageLocation loc2) ->
                    loc1.getFullnameString().compareTo(loc2.getFullnameString()));
        }

        return array;
    }

    private IntBuffer getIntBuffer(long offset, long size) throws IOException {
        MappedByteBuffer buffer = preader.channel().map(FileChannel.MapMode.READ_ONLY, offset, size);
        buffer.order(byteOrder);

        return buffer.asIntBuffer();
    }

    private ByteBuffer getByteBuffer(long offset, long size) throws IOException {
        MappedByteBuffer buffer = preader.channel().map(FileChannel.MapMode.READ_ONLY, offset, size);
        // order is not copied into the readonly copy.
        ByteBuffer readOnly = buffer.asReadOnlyBuffer();
        readOnly.order(byteOrder);
        return readOnly;
    }

    private int getRedirect(int index) {
        return redirectBuffer.get(index);
    }

    private int getOffset(int index) {
        return offsetsBuffer.get(index);
    }

    private ImageLocation getLocation(int offset) {
        return ImageLocation.readFrom(locationsBuffer, offset, strings);
    }

    public String getString(int offset) {
        return strings.get(offset).toString();
    }

    public byte[] getResource(ImageLocation loc) throws IOException {
        long compressedSize = loc.getCompressedSize();
        assert compressedSize < Integer.MAX_VALUE;

        if (compressedSize == 0) {
            return preader.read((int)loc.getUncompressedSize(),
                                indexSize + loc.getContentOffset());
        } else {
            byte[] buf = preader.read((int)compressedSize,
                                      indexSize + loc.getContentOffset());
            return ImageFile.Compressor.decompress(buf);
        }
    }

    public byte[] getResource(String name) throws IOException {
        ImageLocation location = findLocation(name);

        return location != null ? getResource(location) : null;
    }

    public List<String> getNames(String name) throws IOException {
        return getNames(getResource(name));
    }

    public List<String> getNames(byte[] bytes) {
        IntBuffer buffer = ByteBuffer.wrap(bytes).asIntBuffer();
        List<String> names = new ArrayList<>();

        while (buffer.hasRemaining()) {
            int offset = buffer.get();
            names.add(getString(offset));
        }

        return names;
    }
}
