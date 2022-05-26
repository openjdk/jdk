/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
 * @summary Vectorization test on array unsafe operations
 * @library /test/lib /
 *
 * @build sun.hotspot.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.ArrayUnsafeOpTest
 *
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class ArrayUnsafeOpTest extends VectorizationTestRunner {

    private static final int SIZE = 2345;

    private static Unsafe unsafe;

    public ArrayUnsafeOpTest() throws Exception {
        Class klass = Unsafe.class;
        Field field = klass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
    }

    @Test
    public byte[] arrayUnsafeFill() {
        byte[] res = new byte[SIZE];
        for (int i = 0; i < 500; i++) {
            unsafe.putByte(res, i + 24, (byte) i);
        }
        return res;
    }

    @Test
    public byte[] arrayUnsafeFillWithOneAddp() {
        byte[] res = new byte[SIZE];
        for (int i = 123; i < 500; i++) {
            unsafe.putByte(res, i, (byte) i);
        }
        return res;
    }

    @Test
    // Note that this case cannot be vectorized since data dependence
    // exists between two unsafe stores of different types on the same
    // array reference.
    public int[] arrayUnsafeFillTypeMismatch() {
        int[] res = new int[SIZE];
        for (int i = 0; i < 500; i++) {
            unsafe.putByte(res, i + 24, (byte) i);
            unsafe.putShort(res, i + 28, (short) 0);
        }
        return res;
    }

    @Test
    // Note that this case cannot be vectorized since data dependence
    // exists between adjacent iterations. (The memory address storing
    // an int array is not increased by 4 per iteration.)
    public int[] arrayUnsafeFillAddrIncrMismatch() {
        int[] res = new int[SIZE];
        for (int i = 0; i < 500; i++) {
            unsafe.putInt(res, i + 24, i);
        }
        return res;
    }
}

