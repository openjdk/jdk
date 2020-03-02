/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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


package org.graalvm.compiler.core.aarch64.test;

import org.graalvm.compiler.lir.LIRInstruction;
import org.junit.Test;

import java.util.function.Predicate;

public class AArch64FloatSqrtTest extends AArch64MatchRuleTest {

    private static final Predicate<LIRInstruction> p1 = op -> op.name().equals("SQRT");
    private static final Predicate<LIRInstruction> p2 = op -> op.name().equals("AArch64FloatConvert");

    public float floatSqrt(float f) {
        return (float) Math.sqrt(f);
    }

    private float[] input = {-1, 0f, -0f, Float.MAX_VALUE, Float.MIN_NORMAL, Float.MIN_VALUE, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY};

    @Test
    public void testFloatSqrt() {
        for (float f : input) {
            test("floatSqrt", f);
            checkLIR("floatSqrt", p1, 1);
            checkLIR("floatSqrt", p2, 0);
        }
    }
}
