/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.arraycopy;
import java.util.Random;

/**
 * @test
 * @bug 8310159
 * @summary Test large arrayCopy.
 *
 * @run main/timeout=600 compiler.arraycopy.TestArrayCopyDisjointLarge
 *
 */

public class TestArrayCopyDisjointLarge {

    public static final int ARRLEN = 4194304;
    public static int fromPos, toPos;
    public static byte[] fromByteArr, toByteArr;

    public static void setup() {
        fromPos = 0;
        toPos = 0;

        fromByteArr = new byte[ARRLEN];
        toByteArr = new byte[ARRLEN];
        for (int i = 0 ; i < ARRLEN ; i++) {
            fromByteArr[i] = (byte)i;
        }
    }

    public static void validate(String msg, byte[] toByteArr, int length, int fromPos, int toPos) {
        for(int i = 0 ; i < length; i++) {
            if (fromByteArr[i + fromPos] != toByteArr[i + toPos]) {
                System.out.println(msg + "[" + toByteArr.getClass() + "] Result mismtach at i = " + i
                                + " expected = " + fromByteArr[i + fromPos]
                                + " actual   = " + toByteArr[i + toPos]
                                + " fromPos = " + fromPos
                                + " toPos = " + toPos);
                throw new Error("Fail");
            }
        }
    }

    public static void testByte(int length, int fromPos, int toPos) {
        System.arraycopy(fromByteArr, fromPos, toByteArr, toPos, length);
        validate(" Test ByteArr ", toByteArr, length, fromPos, toPos);
    }

    public static void main(String [] args) {
        int base_size = 2621440;
        Random r = new Random(1024);
        int [] lengths = {base_size - 1, base_size, base_size + 1, base_size + 63, base_size + 64,
                                base_size + 65, base_size + 255, base_size + 256, base_size + 257,
                                base_size + r.nextInt(2048)};
        setup();

        for (int i = 0 ; i < 20 ; i++ ) {
            testByte(lengths[i % lengths.length], 0, 0);
            testByte(lengths[i % lengths.length], 0, 9);
            testByte(lengths[i % lengths.length], 9, 0);
            testByte(lengths[i % lengths.length], 9, 9);
            testByte(lengths[i % lengths.length], r.nextInt(2048) , r.nextInt(2048));
        }
    }
}
