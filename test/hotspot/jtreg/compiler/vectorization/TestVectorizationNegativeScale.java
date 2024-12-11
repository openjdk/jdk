/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8332827
 * @summary [REDO] C2: crash in compiled code because of dependency on removed range check CastIIs
 *
 * @library /test/lib /
 * @run driver TestVectorizationNegativeScale
 *
 */

import compiler.lib.ir_framework.*;

import java.util.Arrays;

public class TestVectorizationNegativeScale {
    public static void main(String[] args) {
        TestFramework.run();
    }

    static byte[] array = new byte[1000];

    @Test
    @IR(counts = { IRNode.STORE_VECTOR , ">= 1"})
    private static void test1(byte[] array, int start) {
        for (int i = start; i < array.length; i++) {
            array[array.length - i - 1] = 0x42;
        }
    }

    @Run(test = "test1")
    private static void test1Runner() {
        Arrays.fill(array, (byte)0);
        test1(array, 0);
        for (int j = 0; j < array.length; j++) {
            if (array[j] != 0x42) {
                throw new RuntimeException("For index " + j + ": " + array[j]);
            }
        }
    }
}
