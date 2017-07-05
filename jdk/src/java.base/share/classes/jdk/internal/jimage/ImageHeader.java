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

import java.nio.ByteOrder;
import java.nio.IntBuffer;

public final class ImageHeader {
    public static final int MAGIC = 0xCAFEDADA;
    public static final int BADMAGIC = 0xDADAFECA;
    public static final short MAJOR_VERSION = 0;
    public static final short MINOR_VERSION = 1;

    private final int magic;
    private final short majorVersion;
    private final short minorVersion;
    private final int locationCount;
    private final int locationsSize;
    private final int stringsSize;

    ImageHeader(int locationCount, int locationsSize, int stringsSize) {
        this(MAGIC, MAJOR_VERSION, MINOR_VERSION, locationCount, locationsSize, stringsSize);
    }

    ImageHeader(int magic, short majorVersion, short minorVersion, int locationCount,
                int locationsSize, int stringsSize)
    {
        this.magic = magic;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.locationCount = locationCount;
        this.locationsSize = locationsSize;
        this.stringsSize = stringsSize;
    }

    static int getHeaderSize() {
       return 4 +
              2 + 2 +
              4 +
              4 +
              4;
    }

    static ImageHeader readFrom(ByteOrder byteOrder, IntBuffer buffer) {
        int magic = buffer.get(0);
        int version = buffer.get(1);
        short majorVersion = (short)(byteOrder == ByteOrder.BIG_ENDIAN ?
            version >>> 16 : (version & 0xFFFF));
        short minorVersion = (short)(byteOrder == ByteOrder.BIG_ENDIAN ?
            (version & 0xFFFF) : version >>> 16);
        int locationCount = buffer.get(2);
        int locationsSize = buffer.get(3);
        int stringsSize = buffer.get(4);

        return new ImageHeader(magic, majorVersion, minorVersion, locationCount,
                               locationsSize, stringsSize);
    }

    void writeTo(ImageStream stream) {
        stream.putInt(magic);
        stream.putShort(majorVersion);
        stream.putShort(minorVersion);
        stream.putInt(locationCount);
        stream.putInt(locationsSize);
        stream.putInt(stringsSize);
    }

    public int getMagic() {
        return magic;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public int getLocationCount() {
        return locationCount;
    }

    public int getRedirectSize() {
        return locationCount* 4;
    }

    public int getOffsetsSize() {
        return locationCount* 4;
    }

    public int getLocationsSize() {
        return locationsSize;
    }

    public int getStringsSize() {
        return stringsSize;
    }

    public int getIndexSize() {
        return getHeaderSize() +
               getRedirectSize() +
               getOffsetsSize() +
               getLocationsSize() +
               getStringsSize();
    }

    int getRedirectOffset() {
        return getHeaderSize();
    }

    int getOffsetsOffset() {
        return getRedirectOffset() +
               getRedirectSize();
    }

    int getLocationsOffset() {
        return getOffsetsOffset() +
               getOffsetsSize();
    }

    int getStringsOffset() {
        return getLocationsOffset() +
               getLocationsSize();
    }
}
