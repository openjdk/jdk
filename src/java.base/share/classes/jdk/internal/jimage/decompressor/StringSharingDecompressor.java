/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jimage.ImageStringsReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Properties;

/**
 * A Decompressor that reconstructs the constant pool of classes.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public class StringSharingDecompressor implements ResourceDecompressor {

    public static final int EXTERNALIZED_STRING = 23;
    public static final int EXTERNALIZED_STRING_DESCRIPTOR = 25;

    private static final int CONSTANT_Utf8 = 1;
    private static final int CONSTANT_Integer = 3;
    private static final int CONSTANT_Float = 4;
    private static final int CONSTANT_Long = 5;
    private static final int CONSTANT_Double = 6;
    private static final int CONSTANT_Class = 7;
    private static final int CONSTANT_String = 8;
    private static final int CONSTANT_Fieldref = 9;
    private static final int CONSTANT_Methodref = 10;
    private static final int CONSTANT_InterfaceMethodref = 11;
    private static final int CONSTANT_NameAndType = 12;
    private static final int CONSTANT_MethodHandle = 15;
    private static final int CONSTANT_MethodType = 16;
    private static final int CONSTANT_InvokeDynamic = 18;
    private static final int CONSTANT_Module = 19;
    private static final int CONSTANT_Package = 20;

    private static final int[] SIZES = new int[21];

    static {

        //SIZES[CONSTANT_Utf8] = XXX;
        SIZES[CONSTANT_Integer] = 4;
        SIZES[CONSTANT_Float] = 4;
        SIZES[CONSTANT_Long] = 8;
        SIZES[CONSTANT_Double] = 8;
        SIZES[CONSTANT_Class] = 2;
        SIZES[CONSTANT_String] = 2;
        SIZES[CONSTANT_Fieldref] = 4;
        SIZES[CONSTANT_Methodref] = 4;
        SIZES[CONSTANT_InterfaceMethodref] = 4;
        SIZES[CONSTANT_NameAndType] = 4;
        SIZES[CONSTANT_MethodHandle] = 3;
        SIZES[CONSTANT_MethodType] = 2;
        SIZES[CONSTANT_InvokeDynamic] = 4;
        SIZES[CONSTANT_Module] = 2;
        SIZES[CONSTANT_Package] = 2;
    }

    public static int[] getSizes() {
        return SIZES.clone();
    }

    @SuppressWarnings("fallthrough")
    public static byte[] normalize(StringsProvider provider, byte[] transformed,
                                   int offset, long originalSize) throws IOException {
        if (originalSize > Integer.MAX_VALUE) {
            throw new OutOfMemoryError("Required array size too large");
        }

        byte[] bytesOut = new byte[(int) originalSize];
        int bytesOutOffset = 0;

        // maginc/4, minor/2, major/2
        final int headerSize = 8;

        System.arraycopy(transformed, offset, bytesOut, bytesOutOffset, headerSize);
        bytesOutOffset += headerSize;

        ByteBuffer bytesIn = ByteBuffer.wrap(transformed);
        bytesIn.position(offset + headerSize);
        int count = Short.toUnsignedInt(bytesIn.getShort());
        bytesOut[bytesOutOffset++] = (byte) ((count >> 8) & 0xff);
        bytesOut[bytesOutOffset++] = (byte) (count & 0xff);
        for (int i = 1; i < count; i++) {
            int tag = Byte.toUnsignedInt(bytesIn.get());
            switch (tag) {
                case CONSTANT_Utf8: {
                    int stringLength = Short.toUnsignedInt(bytesIn.getShort());
                    bytesOut[bytesOutOffset++] = (byte) tag;
                    bytesOut[bytesOutOffset++] = (byte) ((stringLength >> 8) & 0xff);
                    bytesOut[bytesOutOffset++] = (byte) (stringLength & 0xff);
                    bytesIn.get(bytesOut, bytesOutOffset, stringLength);
                    bytesOutOffset += stringLength;
                    break;
                }

                case EXTERNALIZED_STRING: {
                    int index = CompressIndexes.readInt(bytesIn);
                    String orig = provider.getString(index);
                    bytesOut[bytesOutOffset++] = CONSTANT_Utf8;

                    int bytesLength = ImageStringsReader.mutf8FromString(bytesOut, bytesOutOffset + 2, orig);
                    bytesOut[bytesOutOffset++] = (byte) ((bytesLength >> 8) & 0xff);
                    bytesOut[bytesOutOffset++] = (byte) (bytesLength & 0xff);
                    bytesOutOffset += bytesLength;
                    break;
                }

                case EXTERNALIZED_STRING_DESCRIPTOR: {
                    bytesOut[bytesOutOffset++] = CONSTANT_Utf8;
                    bytesOutOffset += reconstruct(provider, bytesIn, bytesOut, bytesOutOffset);
                    break;
                }
                case CONSTANT_Long:
                case CONSTANT_Double: {
                    i++;
                }
                default: {
                    bytesOut[bytesOutOffset++] = (byte) tag;
                    int size = SIZES[tag];
                    bytesIn.get(bytesOut, bytesOutOffset, size);
                    bytesOutOffset += size;
                }
            }
        }

        if (bytesIn.remaining() != bytesOut.length - bytesOutOffset) {
            throw new IOException("Resource content size mismatch");
        }

        bytesIn.get(bytesOut, bytesOutOffset, bytesIn.remaining());
        return bytesOut;
    }

    private static int reconstruct(StringsProvider reader, ByteBuffer bytesIn, byte[] bytesOut, int bytesOutOffset)
            throws IOException {
        int descIndex = CompressIndexes.readInt(bytesIn);
        String desc = reader.getString(descIndex);
        byte[] encodedDesc = ImageStringsReader.mutf8FromString(desc);
        int indexes_length = CompressIndexes.readInt(bytesIn);
        byte[] bytes = new byte[indexes_length];
        bytesIn.get(bytes);
        int[] indices = CompressIndexes.decompressFlow(bytes);
        int argIndex = 0;
        int current = bytesOutOffset + 2;
        for (byte c : encodedDesc) {
            if (c == 'L') {
                bytesOut[current++] = c;
                int index = indices[argIndex];
                argIndex += 1;
                String pkg = reader.getString(index);
                if (!pkg.isEmpty()) {
                    pkg = pkg + "/";
                    current += ImageStringsReader.mutf8FromString(bytesOut, current, pkg);
                }
                int classIndex = indices[argIndex];
                argIndex += 1;
                String clazz = reader.getString(classIndex);
                current += ImageStringsReader.mutf8FromString(bytesOut, current, clazz);
            } else {
                bytesOut[current++] = c;
            }
        }
        int stringLength = current - bytesOutOffset - 2;
        bytesOut[bytesOutOffset] = (byte) ((stringLength >> 8) & 0xff);
        bytesOut[bytesOutOffset + 1] = (byte) (stringLength & 0xff);
        return stringLength + 2;
    }

    @Override
    public String getName() {
        return StringSharingDecompressorFactory.NAME;
    }

    public StringSharingDecompressor(Properties properties) {

    }

    @Override
    public byte[] decompress(StringsProvider reader, byte[] content,
                             int offset, long originalSize) throws Exception {
        return normalize(reader, content, offset, originalSize);
    }
}
