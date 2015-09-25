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

public class ImageLocationBase {
    static final int ATTRIBUTE_END = 0;
    static final int ATTRIBUTE_MODULE = 1;
    static final int ATTRIBUTE_PARENT = 2;
    static final int ATTRIBUTE_BASE = 3;
    static final int ATTRIBUTE_EXTENSION = 4;
    static final int ATTRIBUTE_OFFSET = 5;
    static final int ATTRIBUTE_COMPRESSED = 6;
    static final int ATTRIBUTE_UNCOMPRESSED = 7;
    static final int ATTRIBUTE_COUNT = 8;

    protected final long[] attributes;

    protected final ImageStrings strings;

    protected ImageLocationBase(long[] attributes, ImageStrings strings) {
        this.attributes = attributes;
        this.strings = strings;
    }

    ImageStrings getStrings() {
        return strings;
    }

    private static int attributeLength(int data) {
        return (data & 0x7) + 1;
    }

    private static int attributeKind(int data) {
        return data >>> 3;
    }

    static long[] decompress(byte[] bytes) {
        return decompress(bytes, 0);
    }

    static long[] decompress(byte[] bytes, int offset) {
        long[] attributes = new long[ATTRIBUTE_COUNT];

        if (bytes != null) {
            for (int i = offset; i < bytes.length; ) {
                int data = bytes[i++] & 0xFF;
                int kind = attributeKind(data);

                if (kind == ATTRIBUTE_END) {
                    break;
                }

                assert ATTRIBUTE_END < kind &&
                       kind < ATTRIBUTE_COUNT : "Invalid attribute kind";
                int length = attributeLength(data);
                long value = 0;

                for (int j = 0; j < length; j++) {
                    value <<= 8;
                    value |= bytes[i++] & 0xFF;
                }

                 attributes[kind] = value;
            }
        }

        return attributes;
    }

    static byte[] compress(long[] attributes) {
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

        return stream.toArray();
     }

    public boolean verify(UTF8String name) {
        return UTF8String.equals(getFullName(), name);
    }

    protected long getAttribute(int kind) {
        assert ATTRIBUTE_END < kind &&
               kind < ATTRIBUTE_COUNT : "Invalid attribute kind";

        return attributes[kind];
    }

    protected UTF8String getAttributeUTF8String(int kind) {
        assert ATTRIBUTE_END < kind &&
               kind < ATTRIBUTE_COUNT : "Invalid attribute kind";

        return getStrings().get((int)attributes[kind]);
    }

    protected String getAttributeString(int kind) {
        return getAttributeUTF8String(kind).toString();
    }

    UTF8String getModule() {
        return getAttributeUTF8String(ATTRIBUTE_MODULE);
    }

    public String getModuleString() {
        return getModule().toString();
    }

    int getModuleOffset() {
        return (int)getAttribute(ATTRIBUTE_MODULE);
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

    UTF8String getFullName() {
        return getFullName(false);
    }

    UTF8String getFullName(boolean modulesPrefix) {
        // Note: Consider a UTF8StringBuilder.
        UTF8String fullName = UTF8String.EMPTY_STRING;

        if (getModuleOffset() != 0) {
            fullName = fullName.concat(
                // TODO The use of UTF8String.MODULES_STRING does not belong here.
                modulesPrefix? UTF8String.MODULES_STRING :
                               UTF8String.EMPTY_STRING,
                UTF8String.SLASH_STRING,
                getModule(),
                UTF8String.SLASH_STRING);
        }

        if (getParentOffset() != 0) {
            fullName = fullName.concat(getParent(),
                                       UTF8String.SLASH_STRING);
        }

        fullName = fullName.concat(getBase());

        if (getExtensionOffset() != 0) {
                fullName = fullName.concat(UTF8String.DOT_STRING,
                                           getExtension());
        }

        return fullName;
    }

    UTF8String buildName(boolean includeModule, boolean includeParent,
            boolean includeName) {
        // Note: Consider a UTF8StringBuilder.
        UTF8String name = UTF8String.EMPTY_STRING;

        if (includeModule && getModuleOffset() != 0) {
            name = name.concat(UTF8String.MODULES_STRING,
                               UTF8String.SLASH_STRING,
                               getModule());
        }

        if (includeParent && getParentOffset() != 0) {
            name = name.concat(UTF8String.SLASH_STRING,
                                       getParent());
        }

        if (includeName) {
            if (includeModule || includeParent) {
                name = name.concat(UTF8String.SLASH_STRING);
            }

            name = name.concat(getBase());

            if (getExtensionOffset() != 0) {
                name = name.concat(UTF8String.DOT_STRING,
                                           getExtension());
            }
        }

        return name;
    }

    String getFullNameString() {
        return getFullName().toString();
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
}
