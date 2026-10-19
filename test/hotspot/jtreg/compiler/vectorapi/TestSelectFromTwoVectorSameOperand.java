/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=SVE
 * @bug 8387149
 * @summary Test case for SelectFromTwoVector with index operand same as other inputs.
 * @requires vm.compiler2.enabled
 * @requires os.arch == "aarch64" & vm.cpu.features ~= ".*sve.*"
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run main/othervm
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:UseSVE=1
 *      -XX:-TieredCompilation -Xbatch
 *      -XX:CompileCommand=dontinline,${test.main.class}::test*
 *      -XX:CompileCommand=compileonly,${test.main.class}::test*
 *      ${test.main.class}
 */

/*
 * @test id=NEON
 * @bug 8387149
 * @summary Test case for SelectFromTwoVector with index operand same as other inputs.
 * @requires vm.compiler2.enabled
 * @requires os.arch == "aarch64" & vm.cpu.features ~= ".*asimd.*"
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run main/othervm
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:UseSVE=0
 *      -XX:-TieredCompilation -Xbatch
 *      -XX:CompileCommand=dontinline,${test.main.class}::test*
 *      -XX:CompileCommand=compileonly,${test.main.class}::test*
 *      ${test.main.class}
 */

package compiler.vectorapi;

import java.util.Random;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

public class TestSelectFromTwoVectorSameOperand {
    static final int SIZE = 8;

    static byte[] byte_input1 = new byte[SIZE];
    static byte[] byte_input2 = new byte[SIZE];
    static byte[] byte_output = new byte[SIZE];
    static final byte byte_index_mask = 15;

    static short[] short_input1 = new short[SIZE / 2];
    static short[] short_input2 = new short[SIZE / 2];
    static short[] short_output = new short[SIZE / 2];
    static final short short_index_mask = 7;

    static {
        Random r = new Random(42);
        r.nextBytes(byte_input1);
        r.nextBytes(byte_input2);

        for (int i = 0; i < SIZE / 2; i++) {
            short_input1[i] = byte_input1[i];
            short_input2[i] = byte_input2[i];
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 100_000; ++i) {
            test_byte_src1();
            verify_byte(byte_input1, byte_input2, byte_input1, byte_output);
            test_byte_src2();
            verify_byte(byte_input1, byte_input2, byte_input2, byte_output);
            test_short_src1();
            verify_short(short_input1, short_input2, short_input1, short_output);
            test_short_src2();
            verify_short(short_input1, short_input2, short_input2, short_output);
        }
    }

    static void test_byte_src1() {
        ByteVector src1 = ByteVector.fromArray(ByteVector.SPECIES_64, byte_input1, 0).and(byte_index_mask);
        ByteVector src2 = ByteVector.fromArray(ByteVector.SPECIES_64, byte_input2, 0).and(byte_index_mask);
        src1.selectFrom(src1, src2).intoArray(byte_output, 0);
    }

    static void test_byte_src2() {
        ByteVector src1 = ByteVector.fromArray(ByteVector.SPECIES_64, byte_input1, 0).and(byte_index_mask);
        ByteVector src2 = ByteVector.fromArray(ByteVector.SPECIES_64, byte_input2, 0).and(byte_index_mask);
        src2.selectFrom(src1, src2).intoArray(byte_output, 0);
    }

    static void test_short_src1() {
        ShortVector src1 = ShortVector.fromArray(ShortVector.SPECIES_64, short_input1, 0).and(short_index_mask);
        ShortVector src2 = ShortVector.fromArray(ShortVector.SPECIES_64, short_input2, 0).and(short_index_mask);
        src1.selectFrom(src1, src2).intoArray(short_output, 0);
    }

    static void test_short_src2() {
        ShortVector src1 = ShortVector.fromArray(ShortVector.SPECIES_64, short_input1, 0).and(short_index_mask);
        ShortVector src2 = ShortVector.fromArray(ShortVector.SPECIES_64, short_input2, 0).and(short_index_mask);
        src2.selectFrom(src1, src2).intoArray(short_output, 0);
    }

    static void verify_byte(byte[] src1, byte[] src2, byte[] index, byte[] output) {
        for (int i = 0; i < SIZE; i++) {
            int index_value = index[i] & byte_index_mask;
            byte element_value = (index_value < SIZE) ? src1[index_value] : src2[index_value - SIZE];
            byte masked_element_value = (byte) (element_value & byte_index_mask);
            Asserts.assertEQ(masked_element_value, output[i]);
        }
    }

    static void verify_short(short[] src1, short[] src2, short[] index, short[] output) {
        for (int i = 0; i < SIZE / 2; i++) {
            int index_value = index[i] & short_index_mask;
            short element_value = (index_value < SIZE / 2) ? src1[index_value] : src2[index_value - SIZE / 2];
            short masked_element_value = (short) (element_value & short_index_mask);
            Asserts.assertEQ(masked_element_value, output[i]);
        }
    }
}
