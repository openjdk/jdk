/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, BELLSOFT. All rights reserved.
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

package compiler.intrinsics;

import java.lang.reflect.Method;
import java.util.function.IntConsumer;
import java.util.Arrays;
import jdk.test.whitebox.WhiteBox;
import jdk.test.lib.Asserts;

import static jdk.test.lib.Asserts.assertEQ;
import static jdk.test.lib.Asserts.assertTrue;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;

/*
 * @test
 * @bug 8300669
 * @summary check Arrays.fill methods for byte length up to 600 with aligned, unaligned
 *          and zero-nonzero cases.
 * @requires vm.flavor == "server" & (vm.opt.TieredStopAtLevel == null | vm.opt.TieredStopAtLevel == 4)
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -server -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-UseSIMDForArrayEquals
 *                   compiler.intrinsics.TestArraysFill
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -server -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+UseSIMDForArrayEquals
 *                   compiler.intrinsics.TestArraysFill
 */

public class TestArraysFill {
    static final int MAX_BYTES_LENGTH_CHECK = 600;
    static final int SIZE = MAX_BYTES_LENGTH_CHECK + 150;
    static final int START_BYTE = 64 + 4;
    static final byte BYTE_PATTERN = (byte) 42;
    static final short SHORT_PATTERN = (short) 0x1234;
    static final int INT_PATTERN = 0xCAFEBABE;

    WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws Exception {
        var t = new TestArraysFill();
        t.testByteAll();
        t.testShortAll();
        t.testIntAll();
    }

    void testByteAll() throws Exception {
        testAll(byte[].class, this::testByteForLength, MAX_BYTES_LENGTH_CHECK);
    }

    void testShortAll() throws Exception {
        testAll(short[].class, this::testShortForLength, MAX_BYTES_LENGTH_CHECK / 2);
    }

    void testIntAll() throws Exception {
        testAll(int[].class, this::testIntForLength, MAX_BYTES_LENGTH_CHECK / 4);
    }

    void testAll(Class arrayType, IntConsumer testForLength, int maxArrayLength) throws NoSuchMethodException {
        var elemType = arrayType.getComponentType();
        var m2 = Arrays.class.getDeclaredMethod("fill", arrayType, elemType);
        var m4 = Arrays.class.getDeclaredMethod("fill", arrayType, int.class, int.class, elemType);

        testForLength.accept(maxArrayLength); // warmup

        assertTrue(wb.enqueueMethodForCompilation(m2, COMP_LEVEL_FULL_OPTIMIZATION));
        assertTrue(wb.enqueueMethodForCompilation(m4, COMP_LEVEL_FULL_OPTIMIZATION));

        for (int length = 0; length < maxArrayLength; length++) {
            assertEQ(wb.getMethodCompilationLevel(m2), COMP_LEVEL_FULL_OPTIMIZATION);
            assertEQ(wb.getMethodCompilationLevel(m4), COMP_LEVEL_FULL_OPTIMIZATION);
            testForLength.accept(length);
            assertEQ(wb.getMethodCompilationLevel(m2), COMP_LEVEL_FULL_OPTIMIZATION);
            assertEQ(wb.getMethodCompilationLevel(m4), COMP_LEVEL_FULL_OPTIMIZATION);
        }
    }

    void testByteForLength(int length) {
        testByte2(length, (byte) 0, BYTE_PATTERN);
        testByte2(length, BYTE_PATTERN, (byte) 0);

        testByte4(length, (byte) 0, BYTE_PATTERN);
        testByte4(length, BYTE_PATTERN, (byte) 0);
    }

    void testShortForLength(int length) {
        testShort2(length, (short) 0, SHORT_PATTERN);
        testShort2(length, SHORT_PATTERN, (short) 0);

        testShort4(length, (short) 0, SHORT_PATTERN);
        testShort4(length, SHORT_PATTERN, (short) 0);
    }

    void testIntForLength(int length) {
        testInt2(length, 0, INT_PATTERN);
        testInt2(length, INT_PATTERN, 0);

        testInt4(length, 0, INT_PATTERN);
        testInt4(length, INT_PATTERN, 0);
    }

    void testByte2(int length, byte initVal, byte fillVal) {
        var data = new byte[length];
        for (int i = 0; i < data.length; i++)
            data[i] = initVal;
        Arrays.fill(data, fillVal);
        for (int i = 0; i < length; i++)
            check(i, data[i], 0, length, initVal, fillVal);
    }

    void testByte4(int length, byte initVal, byte fillVal) {
        var data = new byte[SIZE];
        for (int i = 0; i < data.length; i++)
            data[i] = initVal;
        int start = START_BYTE;
        int end = start + length;
        Arrays.fill(data, start, end, fillVal);
        for (int i = Math.max(0, start - 64); i < Math.min(end + 64, data.length); i++)
            check(i, data[i], start, end, initVal, fillVal);
    }

    void testShort2(int length, short initVal, short fillVal) {
        var data = new short[length];
        for (int i = 0; i < data.length; i++)
            data[i] = initVal;
        Arrays.fill(data, fillVal);
        for (int i = 0; i < length; i++)
            check(i, data[i], 0, length, initVal, fillVal);
    }

    void testShort4(int length, short initVal, short fillVal) {
        var data = new short[SIZE / 2];
        for (int i = 0; i < data.length; i++)
            data[i] = initVal;
        int start = START_BYTE / 2;
        int end = start + length;
        Arrays.fill(data, start, end, fillVal);
        for (int i = Math.max(0, start - 64); i < Math.min(end + 64, data.length); i++)
            check(i, data[i], start, end, initVal, fillVal);
    }

    void testInt2(int length, int initVal, int fillVal) {
        var data = new int[length];
        for (int i = 0; i < data.length; i++)
            data[i] = initVal;
        Arrays.fill(data, fillVal);
        for (int i = 0; i < length; i++)
            check(i, data[i], 0, length, initVal, fillVal);
    }

    void testInt4(int length, int initVal, int fillVal) {
        var data = new int[SIZE / 4];
        for (int i = 0; i < data.length; i++)
            data[i] = initVal;
        int start = START_BYTE / 4;
        int end = start + length;
        Arrays.fill(data, start, end, fillVal);
        for (int i = Math.max(0, start - 64); i < Math.min(end + 64, data.length); i++)
            check(i, data[i], start, end, initVal, fillVal);
    }

    void check(int i, int actual, int start, int end, int initVal, int fillVal) {
        int len = end - start;
        if (i < start) {
            assertEQ(actual, initVal, String.format("Corrupted value at %d before [%d x %d]", i, len, fillVal));
        } else if (i >= end) {
            assertEQ(actual, initVal, String.format("Corrupted value at %d after [%d x %d]", i, len, fillVal));
        } else {
            assertEQ(actual, fillVal, String.format("Wrong value at %d in [%d x %d]", i - start, len, fillVal));
        }
    }
}
