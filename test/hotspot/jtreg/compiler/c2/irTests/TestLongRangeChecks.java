/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import java.util.Objects;

/*
 * @test
 * @bug 8259609
 * @summary C2: optimize long range checks in long counted loops
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestLongRangeChecks
 */

public class TestLongRangeChecks {
    public static void main(String[] args) {
        TestFramework.run();
    }


    @Test
    @IR(counts = { IRNode.LOOP, "1"})
    @IR(failOn = { IRNode.COUNTEDLOOP})
    public static void testStridePosScalePos(long start, long stop, long length, long offset) {
        final long scale = 1;
        final long stride = 1;

        // Loop is first transformed into a loop nest, long range
        // check into an int range check, the range check is hoisted
        // and the inner counted loop becomes empty so is optimized
        // out.
        for (long i = start; i < stop; i += stride) {
            Objects.checkIndex(scale * i + offset, length);
        }
    }

    @Run(test = "testStridePosScalePos")
    private void testStridePosScalePos_runner() {
        testStridePosScalePos(0, 100, 100, 0);
    }
}
