/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

/*
 * @test
 * @bug JDK-8292780
 * @summary misc tests failed "assert(false) failed: graph should be schedulable"
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement TestArrayCopyPartialInliningLoadSplit
 */

public class TestArrayCopyPartialInliningLoadSplit {
    public static void main(String[] args) {
        byte[] array = new byte[16];
        for (int i = 0; i < 20_0000; i++) {
            test(array, 16, 0, 0);
        }
    }

    private static void test(byte[] array, int length, int srcPos, int dstPos) {
        byte[] nonEscaping = new byte[16];
        nonEscaping[0] = 0x42;
        System.arraycopy(array, srcPos, nonEscaping, 1, 8);
        System.arraycopy(nonEscaping, 0, array, 0, length);
    }
}
