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
package jdk.internal.jimage.decompressor;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * Index compressor. Use the minimal amount of bytes required to store
 * an integer.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public class CompressIndexes {
    private static final int INTEGER_SIZE = 4;

    public static List<Integer> decompressFlow(byte[] values) {
        List<Integer> lst = new ArrayList<>();
        for (int i = 0; i < values.length;) {
            byte b = values[i];
            int length = isCompressed(b) ? getLength(b) : INTEGER_SIZE;
            int decompressed = decompress(values, i);
            lst.add(decompressed);
            i += length;
        }
        return lst;
    }

    public static int readInt(DataInputStream cr) throws IOException {
        byte[] b = new byte[1];
        cr.readFully(b);
        byte firstByte = b[0];
        boolean compressed = CompressIndexes.isCompressed(firstByte);
        int toRead = 4;
        if(compressed) {
            toRead = CompressIndexes.getLength(firstByte);
        }
        byte[] content = new byte[toRead-1];
        cr.readFully(content);
        ByteBuffer bb = ByteBuffer.allocate(content.length+1);
        bb.put(firstByte);
        bb.put(content);
        int index = CompressIndexes.decompress(bb.array(), 0);
        return index;
    }

    public static int getLength(byte b) {
        return ((byte) (b & 0x60) >> 5);
    }

    public static boolean isCompressed(byte b) {
        return b < 0;
    }

    public static int decompress(byte[] value, int offset) {
        byte b1 = value[offset];
        ByteBuffer buffer = ByteBuffer.allocate(INTEGER_SIZE);
        if (isCompressed(b1)) { // compressed
            int length = getLength(b1);
            byte clearedValue = (byte) (b1 & 0x1F);

            int start = INTEGER_SIZE - length;
            buffer.put(start, clearedValue);
            for (int i = offset + 1; i < offset + length; i++) {
                buffer.put(++start, value[i]);
            }
        } else {
            buffer.put(value, offset, INTEGER_SIZE);
        }
        return buffer.getInt(0);
    }

    public static byte[] compress(int val) {
        ByteBuffer result = ByteBuffer.allocate(4).putInt(val);
        byte[] array = result.array();

        if ((val & 0xFF000000) == 0) { // nothing on 4th
            if ((val & 0x00FF0000) == 0) { // nothing on 3rd
                if ((val & 0x0000FF00) == 0) { // nothing on 2nd
                    if ((val & 0x000000E0) == 0) { // only in 1st, encode length in the byte.
                        //sign bit and size 1 ==> 101X
                        result = ByteBuffer.allocate(1);
                        result.put((byte) (0xA0 | array[3]));
                    } else { // add a byte for size
                        //sign bit and size 2 ==> 110X
                        result = ByteBuffer.allocate(2);
                        result.put((byte) 0xC0);
                        result.put(array[3]);
                    }
                } else { // content in 2nd
                    if ((val & 0x0000E000) == 0) {// encode length in the byte.
                        //sign bit and size 2 ==> 110X
                        result = ByteBuffer.allocate(2);
                        result.put((byte) (0xC0 | array[2]));
                        result.put(array[3]);
                    } else { // add a byte for size
                        //sign bit and size 3 ==> 111X
                        result = ByteBuffer.allocate(3);
                        result.put((byte) 0xE0);
                        result.put(array[2]);
                        result.put(array[3]);
                    }
                }
            } else {// content in 3rd
                if ((val & 0x00E00000) == 0) {// encode length in the byte.
                    //sign bit and size 3 ==> 111X
                    result = ByteBuffer.allocate(3);
                    result.put((byte) (0xE0 | array[1]));
                    result.put(array[2]);
                    result.put(array[3]);
                } else { // add a byte, useless
                    //
                }
            }
        }
        return result.array();
    }
}
