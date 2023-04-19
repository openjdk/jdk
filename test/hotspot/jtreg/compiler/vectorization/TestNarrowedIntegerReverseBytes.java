/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8305324
 * @summary C2: Wrong execution of vectorizing Interger.reverseBytes
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:CompileCommand=compileonly,compiler.vectorization.TestNarrowedIntegerReverseBytes::* compiler.vectorization.TestNarrowedIntegerReverseBytes
 */

package compiler.vectorization;

public class TestNarrowedIntegerReverseBytes {

    static final int LEN = 33;
    static byte byteArray[] = new byte[LEN];

    static void test() {
        for (int i = 0; i < LEN; i++) {
            byteArray[i] = (byte) Integer.reverseBytes(i);
        }
    }

    public static void main(String[] strArr) {
        for (int i = 0; i < 2; i++) {
            test();
        }
        for (int i = 0; i < byteArray.length; i++) {
            if (byteArray[i] != 0) {
                System.err.println("FAILED: all the elements should be zero");
                System.exit(1);
            }
        }
        System.out.println("PASSED");
    }

}
