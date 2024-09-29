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

package compiler.vectorapi;

import jdk.incubator.vector.*;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * @test
 * @bug 8341137
 * @key randomness
 * @requires vm.cpu.features ~= ".*avx.*"
 * @summary Optimize long vector multiplication using x86 VPMULUDQ instruction.
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorMultiplyOpt
 */

public class VectorMultiplyOpt {

    public static long [] src1;
    public static long [] src2;
    public static long [] res;

    public static final int SIZE = 4095;
    public static final Random r = new Random(1024);
    public static final VectorSpecies<Long> LSP = LongVector.SPECIES_PREFERRED;

    public static void pattern1(long [] res, long [] src1, long [] src2) {
        int i = 0;
        for (; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, src1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, src2, i);
            vsrc1.lanewise(VectorOperators.AND, 0xFFFFFFFFL)
                .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.AND, 0xFFFFFFFFL))
                .intoArray(res, i);
        }
        for (; i < res.length; i++) {
            res[i] = (src1[i] & 0xFFFFFFFFL) * (src2[i] & 0xFFFFFFFFL);
        }
    }

    public static void pattern2(long [] res, long [] src1, long [] src2) {
        int i = 0;
        for (; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, src1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, src2, i);
            vsrc1.lanewise(VectorOperators.AND, 0xFFFFFFFFL)
                .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.LSHR, 32))
                .intoArray(res, i);
        }
        for (; i < res.length; i++) {
            res[i] = (src1[i] & 0xFFFFFFFFL) * (src2[i] >>> 32);
        }
    }

    public static void pattern3(long [] res, long [] src1, long [] src2) {
        int i = 0;
        for (; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, src1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, src2, i);
            vsrc1.lanewise(VectorOperators.LSHR, 32)
                .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.LSHR, 32))
                .intoArray(res, i);
        }
        for (; i < res.length; i++) {
            res[i] = (src1[i] >>> 32) * (src2[i] >>> 32);
        }
    }

    public static void pattern4(long [] res, long [] src1, long [] src2) {
        int i = 0;
        for (; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, src1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, src2, i);
            vsrc1.lanewise(VectorOperators.LSHR, 32)
                .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.AND, 0xFFFFFFFFL))
                .intoArray(res, i);
        }
        for (; i < res.length; i++) {
            res[i] = (src1[i] >>> 32) * (src2[i] & 0xFFFFFFFFL);
        }
    }

    interface Validator {
        public long apply(long src1, long src2);
    }

    public static void validate(String msg, long [] actual, long [] src1, long [] src2, Validator func) {
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != func.apply(src1[i], src2[i])) {
                throw new AssertionError(msg + "index " + i + ": src1 = " + src1[i] + " src2 = " +
                                         src2[i] + " actual = " + actual[i] + " expected = " +
                                         func.apply(src1[i], src2[i]));
            }
        }
    }

    public static void setup() {
        src1 = new long[SIZE];
        src2 = new long[SIZE];
        res  = new long[SIZE];
        IntStream.range(0, SIZE).forEach(i -> { src1[i] = Long.MAX_VALUE * r.nextLong(); });
        IntStream.range(0, SIZE).forEach(i -> { src2[i] = Long.MAX_VALUE * r.nextLong(); });
    }

    public static void main(String[] args) {
        setup();
        for (int ic = 0; ic < 1000; ic++) {
            pattern1(res, src1, src2);
            validate("pattern1 ", res, src1, src2, (src1, src2) -> (src1 & 0xFFFFFFFFL) * (src2 & 0xFFFFFFFFL));

            pattern2(res, src1, src2);
            validate("pattern2 ", res, src1, src2, (src1, src2) -> (src1 & 0xFFFFFFFFL) * (src2 >>> 32));

            pattern3(res, src1, src2);
            validate("pattern3 ", res, src1, src2, (src1, src2) -> (src1 >>> 32) * (src2 >>> 32));

            pattern4(res, src1, src2);
            validate("pattern4 ", res, src1, src2, (src1, src2) -> (src1 >>> 32) * (src2 & 0xFFFFFFFFL));
        }
        System.out.println("PASSED");
    }
}
