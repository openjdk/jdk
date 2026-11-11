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
package compiler.intrinsics;

import compiler.lib.ir_framework.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/*
 * @test
 * @bug 8394736
 * @summary When a call to Objects::checkIndex fails and hits the uncommon trap, the compiler
 *          should still intrinsify Preconditions::checkIndex at all other calls to
 *          Objects::checkIndex.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestPollutedCheckIndex {
    private static final MemorySegment SEGMENT = Arena.global().allocate(Integer.BYTES);

    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.setDefaultWarmup(1);
        // Ensure the stability of the tests
        framework.addFlags("-XX:-CompactStrings",
                           "-XX:CompileCommand=dontinline,*::outOfBoundsCheckIndex",
                           "-XX:CompileCommand=inline,java.lang.String::*",
                           "-XX:CompileCommand=inline,java.lang.StringUTF16::*");
        framework.addScenarios(new Scenario(0));
        framework.addScenarios(new Scenario(1, "-XX:PerMethodTrapLimit=0"));
        framework.start();
    }

    private static void polluteInt() {
        try {
            Objects.checkIndex(1, 0);
            throw new RuntimeException();
        } catch (IndexOutOfBoundsException _) {}
    }

    private static void polluteLong() {
        try {
            Objects.checkIndex(1L, 0L);
            throw new RuntimeException();
        } catch (IndexOutOfBoundsException _) {}
    }

    private static void polluteString() {
        try {
            // This trap pollutes String::checkIndex, preventing Preconditions::checkIndex from
            // intrinsifying from there
            int _ = "".codePointAt(1);
            throw new RuntimeException();
        } catch (IndexOutOfBoundsException _) {}
    }

    private static void polluteMemorySegment() {
        try {
            SEGMENT.set(ValueLayout.JAVA_INT_UNALIGNED, 4, 0);
            throw new RuntimeException();
        } catch (IndexOutOfBoundsException _) {}
    }

    @Test
    @IR(counts = {IRNode.RANGE_CHECK_TRAP, "1"})
    private static int testInt(int idx, int limit) {
        return Objects.checkIndex(idx, limit);
    }

    @Test
    @IR(counts = {IRNode.RANGE_CHECK_TRAP, "1"})
    private static long testLong(long idx, long limit) {
        return Objects.checkIndex(idx, limit);
    }

    @Test
    @IR(counts = {IRNode.RANGE_CHECK_TRAP, "1"})
    private static char testString(String str, int idx) {
        return str.charAt(idx);
    }

    @Test
    @IR(counts = {IRNode.RANGE_CHECK_TRAP, "1"})
    private static int testMemorySegment(MemorySegment ms, long offset) {
        return ms.get(ValueLayout.JAVA_INT_UNALIGNED, offset);
    }

    @Run(test = {"testInt", "testLong", "testString", "testMemorySegment"})
    public void run() {
        for (int i = 0; i < 20000; i++) {
            polluteInt();
            polluteLong();
            polluteString();
            polluteMemorySegment();
        }

        testInt(0, 1);
        testLong(0, 1);
        testString("a", 0);
        testMemorySegment(SEGMENT, 0);
    }
}
