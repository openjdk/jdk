/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public class ImageLocation {
    public static final int ATTRIBUTE_END = 0;
    public static final int ATTRIBUTE_MODULE = 1;
    public static final int ATTRIBUTE_PARENT = 2;
    public static final int ATTRIBUTE_BASE = 3;
    public static final int ATTRIBUTE_EXTENSION = 4;
    public static final int ATTRIBUTE_OFFSET = 5;
    public static final int ATTRIBUTE_COMPRESSED = 6;
    public static final int ATTRIBUTE_UNCOMPRESSED = 7;
    public static final int ATTRIBUTE_COUNT = 8;

    protected final long[] attributes;

    protected final ImageStrings strings;

    public ImageLocation(long[] attributes, ImageStrings strings) {
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

    static long[] decompress(ByteBuffer bytes) {
        long[] attributes = new long[ATTRIBUTE_COUNT];

        if (bytes != null) {
            while (bytes.hasRemaining()) {
                int data = bytes.get() & 0xFF;
                int kind = attributeKind(data);

                if (kind == ATTRIBUTE_END) {
                    break;
                }

                if (kind < ATTRIBUTE_END || ATTRIBUTE_COUNT <= kind) {
                    throw new InternalError("Invalid jimage attribute kind");
                }

                int length = attributeLength(data);
                long value = 0;

                for (int j = 0; j < length; j++) {
                    value <<= 8;

                    if (!bytes.hasRemaining()) {
                        throw new InternalError("\"Missing jimage attribute datad");
                    }

                    value |= bytes.get() & 0xFF;
                }

                 attributes[kind] = value;
            }
        }

        return attributes;
    }

    public static byte[] compress(long[] attributes) {
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

    public boolean verify(String name) {
        return name.equals(getFullName());
    }

    long getAttribute(int kind) {
        if (kind < ATTRIBUTE_END || ATTRIBUTE_COUNT <= kind) {
            throw new InternalError("Invalid jimage attribute kind");
        }

        return attributes[kind];
    }

    String getAttributeString(int kind) {
        if (kind < ATTRIBUTE_END || ATTRIBUTE_COUNT <= kind) {
            throw new InternalError("Invalid jimage attribute kind");
        }

        return getStrings().get((int)attributes[kind]);
    }

    public String getModule() {
        return getAttributeString(ATTRIBUTE_MODULE);
    }

    public int getModuleOffset() {
        return (int)getAttribute(ATTRIBUTE_MODULE);
    }

    public String getBase() {
        return getAttributeString(ATTRIBUTE_BASE);
    }

    public int getBaseOffset() {
        return (int)getAttribute(ATTRIBUTE_BASE);
    }

    public String getParent() {
        return getAttributeString(ATTRIBUTE_PARENT);
    }

    public int getParentOffset() {
        return (int)getAttribute(ATTRIBUTE_PARENT);
    }

    public String getExtension() {
        return getAttributeString(ATTRIBUTE_EXTENSION);
    }

    public int getExtensionOffset() {
        return (int)getAttribute(ATTRIBUTE_EXTENSION);
    }

    public String getFullName() {
        return getFullName(false);
    }

    public String getFullName(boolean modulesPrefix) {
        StringBuilder builder = new StringBuilder();

        if (getModuleOffset() != 0) {
            if (modulesPrefix) {
                builder.append("/modules");
            }

            builder.append('/');
            builder.append(getModule());
            builder.append('/');
        }

        if (getParentOffset() != 0) {
            builder.append(getParent());
            builder.append('/');
        }

        builder.append(getBase());

        if (getExtensionOffset() != 0) {
            builder.append('.');
            builder.append(getExtension());
        }

        return builder.toString();
    }

    String buildName(boolean includeModule, boolean includeParent,
            boolean includeName) {
        StringBuilder builder = new StringBuilder();

        if (includeModule && getModuleOffset() != 0) {
            builder.append("/modules/");
            builder.append(getModule());
         }

        if (includeParent && getParentOffset() != 0) {
            builder.append('/');
            builder.append(getParent());
        }

        if (includeName) {
            if (includeModule || includeParent) {
                builder.append('/');
            }

            builder.append(getBase());

            if (getExtensionOffset() != 0) {
                builder.append('.');
                builder.append(getExtension());
            }
        }

        return builder.toString();
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

    static ImageLocation readFrom(BasicImageReader reader, int offset) {
        long[] attributes = reader.getAttributes(offset);
        ImageStringsReader strings = reader.getStrings();

        return new ImageLocation(attributes, strings);
    }
}
