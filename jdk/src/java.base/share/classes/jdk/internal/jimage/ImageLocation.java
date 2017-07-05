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

import java.nio.ByteBuffer;

public final class ImageLocation {
    final static int ATTRIBUTE_END = 0;
    final static int ATTRIBUTE_BASE = 1;
    final static int ATTRIBUTE_PARENT = 2;
    final static int ATTRIBUTE_EXTENSION = 3;
    final static int ATTRIBUTE_OFFSET = 4;
    final static int ATTRIBUTE_COMPRESSED = 5;
    final static int ATTRIBUTE_UNCOMPRESSED = 6;
    final static int ATTRIBUTE_COUNT = 7;

    private int locationOffset;
    private long[] attributes;
    private byte[] bytes;
    private final ImageStrings strings;

    private ImageLocation(ImageStrings strings) {
        this.strings = strings;
    }

    void writeTo(ImageStream stream) {
        compress();
        locationOffset = stream.getPosition();
        stream.put(bytes, 0, bytes.length);
    }

    static ImageLocation readFrom(ByteBuffer locationsBuffer, int offset, ImageStrings strings) {
        final long[] attributes = new long[ATTRIBUTE_COUNT];

        for (int i = offset; true; ) {
            int data = locationsBuffer.get(i++) & 0xFF;
            int kind = attributeKind(data);
            assert ATTRIBUTE_END <= kind && kind < ATTRIBUTE_COUNT : "Invalid attribute kind";

            if (kind == ATTRIBUTE_END) {
                break;
            }

            int length = attributeLength(data);
            long value = 0;

            for (int j = 0; j < length; j++) {
                value <<= 8;
                value |= locationsBuffer.get(i++) & 0xFF;
            }

            attributes[kind] = value;
        }

        ImageLocation location =  new ImageLocation(strings);
        location.attributes = attributes;

        return location;
    }

    private static int attributeLength(int data) {
        return (data & 0x7) + 1;
    }

    private static int attributeKind(int data) {
        return data >>> 3;
    }

    public boolean verify(UTF8String name) {
        UTF8String match = UTF8String.match(name, getParent());

        if (match == null) {
            return false;
        }

        match = UTF8String.match(match, getBase());

        if (match == null) {
            return false;
        }

        match = UTF8String.match(match, getExtension());

        return match != null && match.length() == 0;
    }


    long getAttribute(int kind) {
        assert ATTRIBUTE_END < kind && kind < ATTRIBUTE_COUNT : "Invalid attribute kind";
        decompress();

        return attributes[kind];
    }

    UTF8String getAttributeUTF8String(int kind) {
        assert ATTRIBUTE_END < kind && kind < ATTRIBUTE_COUNT : "Invalid attribute kind";
        decompress();

        return strings.get((int)attributes[kind]);
    }

    String getAttributeString(int kind) {
        return getAttributeUTF8String(kind).toString();
    }

    ImageLocation addAttribute(int kind, long value) {
        assert ATTRIBUTE_END < kind && kind < ATTRIBUTE_COUNT : "Invalid attribute kind";
        decompress();
        attributes[kind] = value;
        return this;
    }

    private void decompress() {
        if (attributes == null) {
            attributes = new long[ATTRIBUTE_COUNT];
        }

        if (bytes != null) {
            for (int i = 0; i < bytes.length; ) {
                int data = bytes[i++] & 0xFF;
                int kind = attributeKind(data);

                if (kind == ATTRIBUTE_END) {
                    break;
                }

                assert ATTRIBUTE_END < kind && kind < ATTRIBUTE_COUNT : "Invalid attribute kind";
                int length = attributeLength(data);
                long value = 0;

                for (int j = 0; j < length; j++) {
                    value <<= 8;
                    value |= bytes[i++] & 0xFF;
                }

                 attributes[kind] = value;
            }

            bytes = null;
        }
    }

    private void compress() {
        if (bytes == null) {
            ImageStream stream = new ImageStream(16);

            for (int kind = ATTRIBUTE_END + 1; kind < ATTRIBUTE_COUNT; kind++) {
                long value = attributes[kind];

                if (value != 0) {
                    int n = (63 - Long.numberOfLeadingZeros(value)) >> 3;
                    stream.put((kind << 3) | n);

                    for (int i = n; i >= 0; i--) {
                        stream.put((int)(value >> (i << 3)));
                    }
                }
            }

            stream.put(ATTRIBUTE_END << 3);
            bytes = stream.toArray();
            attributes = null;
        }
    }

    static ImageLocation newLocation(UTF8String fullname, ImageStrings strings, long contentOffset, long compressedSize, long uncompressedSize) {
        UTF8String base;
        UTF8String extension = extension(fullname);
        int parentOffset = ImageStrings.EMPTY_OFFSET;
        int extensionOffset = ImageStrings.EMPTY_OFFSET;
        int baseOffset;

        if (extension.length() != 0) {
            UTF8String parent = parent(fullname);
            base = base(fullname);
            parentOffset = strings.add(parent);
            extensionOffset = strings.add(extension);
        } else {
            base = fullname;
        }

        baseOffset = strings.add(base);

        return new ImageLocation(strings)
               .addAttribute(ATTRIBUTE_BASE, baseOffset)
               .addAttribute(ATTRIBUTE_PARENT, parentOffset)
               .addAttribute(ATTRIBUTE_EXTENSION, extensionOffset)
               .addAttribute(ATTRIBUTE_OFFSET, contentOffset)
               .addAttribute(ATTRIBUTE_COMPRESSED, compressedSize)
               .addAttribute(ATTRIBUTE_UNCOMPRESSED, uncompressedSize);
    }

    @Override
    public int hashCode() {
        return getExtension().hashCode(getBase().hashCode(getParent().hashCode()));
    }

    int hashCode(int base) {
        return getExtension().hashCode(getBase().hashCode(getParent().hashCode(base)));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof ImageLocation)) {
            return false;
        }

        ImageLocation other = (ImageLocation)obj;

        return getBaseOffset() == other.getBaseOffset() &&
               getParentOffset() == other.getParentOffset() &&
               getExtensionOffset() == other.getExtensionOffset();
    }

    static UTF8String parent(UTF8String fullname) {
        int slash = fullname.lastIndexOf('/');

        return slash == UTF8String.NOT_FOUND ? UTF8String.EMPTY_STRING : fullname.substring(0, slash + 1);
    }

    static UTF8String extension(UTF8String fullname) {
        int dot = fullname.lastIndexOf('.');

        return dot == UTF8String.NOT_FOUND ? UTF8String.EMPTY_STRING : fullname.substring(dot);
    }

    static UTF8String base(UTF8String fullname) {
        int slash = fullname.lastIndexOf('/');

        if (slash != UTF8String.NOT_FOUND) {
            fullname = fullname.substring(slash + 1);
        }

        int dot = fullname.lastIndexOf('.');

        if (dot != UTF8String.NOT_FOUND) {
            fullname = fullname.substring(0, dot);
        }

        return fullname;
    }

    int getLocationOffset() {
        return locationOffset;
    }

    UTF8String getBase() {
        return getAttributeUTF8String(ATTRIBUTE_BASE);
    }

    public String getBaseString() {
        return  getBase().toString();
    }

    int getBaseOffset() {
        return (int)getAttribute(ATTRIBUTE_BASE);
    }

    UTF8String getParent() {
        return getAttributeUTF8String(ATTRIBUTE_PARENT);
    }

    public String getParentString() {
        return getParent().toString();
    }

    int getParentOffset() {
        return (int)getAttribute(ATTRIBUTE_PARENT);
    }

    UTF8String getExtension() {
        return getAttributeUTF8String(ATTRIBUTE_EXTENSION);
    }

    public String getExtensionString() {
        return getExtension().toString();
    }

    int getExtensionOffset() {
        return (int)getAttribute(ATTRIBUTE_EXTENSION);
    }

    UTF8String getName() {
        return getBase().concat(getExtension());
    }

    String getNameString() {
        return getName().toString();
    }

    UTF8String getFullname() {
        return getParent().concat(getBase(), getExtension());
    }

    String getFullnameString() {
        return getFullname().toString();
    }

    public long getContentOffset() {
        return getAttribute(ATTRIBUTE_OFFSET);
    }

    public long getCompressedSize() {
        return getAttribute(ATTRIBUTE_COMPRESSED);
    }

    public long getUncompressedSize() {
        return getAttribute(ATTRIBUTE_UNCOMPRESSED);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        decompress();

        for (int kind = ATTRIBUTE_END + 1; kind < ATTRIBUTE_COUNT; kind++) {
            long value = attributes[kind];

            if (value == 0) {
                continue;
            }

            switch (kind) {
                case ATTRIBUTE_BASE:
                    sb.append("Base: ");
                    sb.append(value);
                    sb.append(' ');
                    sb.append(strings.get((int)value).toString());
                    break;

                case ATTRIBUTE_PARENT:
                    sb.append("Parent: ");
                    sb.append(value);
                    sb.append(' ');
                    sb.append(strings.get((int)value).toString());
                    break;

                case ATTRIBUTE_EXTENSION:
                    sb.append("Extension: ");
                    sb.append(value);
                    sb.append(' ');
                    sb.append(strings.get((int)value).toString());
                    break;

                case ATTRIBUTE_OFFSET:
                    sb.append("Offset: ");
                    sb.append(value);
                    break;

                case ATTRIBUTE_COMPRESSED:
                    sb.append("Compressed: ");
                    sb.append(value);
                    break;

                case ATTRIBUTE_UNCOMPRESSED:
                    sb.append("Uncompressed: ");
                    sb.append(value);
                    break;
           }

           sb.append("; ");
        }

        return sb.toString();
    }
}
