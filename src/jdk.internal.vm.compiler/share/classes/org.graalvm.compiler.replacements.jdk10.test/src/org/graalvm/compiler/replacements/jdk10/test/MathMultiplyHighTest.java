/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Arm Limited. All rights reserved.
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


package org.graalvm.compiler.replacements.jdk10.test;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.nodes.calc.IntegerMulHighNode;
import org.graalvm.compiler.replacements.test.MethodSubstitutionTest;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.junit.Test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.TargetDescription;

public class MathMultiplyHighTest extends MethodSubstitutionTest {
    private static final long[] INPUT = {Long.MIN_VALUE, Long.MIN_VALUE + 1, 0XF64543679090840EL, -1L,
                    0L, 0X5L, 0X100L, 0X4336624L, 0x25842900000L, Long.MAX_VALUE - 1, Long.MAX_VALUE};

    public static long multiplyHigh(long m, long n) {
        return Math.multiplyHigh(m, n);
    }

    @Test
    public void testMultiplyHigh() {
        TargetDescription target = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getTarget();
        if (target.arch instanceof AArch64) {
            assertInGraph(testGraph("multiplyHigh"), IntegerMulHighNode.class);
        }
        for (long input1 : INPUT) {
            for (long input2 : INPUT) {
                test("multiplyHigh", input1, input2);
            }
        }
    }
}
