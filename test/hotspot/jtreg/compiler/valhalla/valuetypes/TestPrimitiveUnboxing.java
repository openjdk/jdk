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
 * @test
 * @bug 8392758
 * @summary C2 must handle boxing and unboxing call projections consistently
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   -XX:CompileCommand=dontinline,java.lang.Integer::intValue
 *                   -XX:CompileCommand=delayinline,java.lang.Long::longValue ${test.main.class}
 * @run main ${test.main.class}
 */

import jdk.test.lib.Asserts;

public class TestPrimitiveUnboxing {
    static final Integer THE_ANSWER = 42;

    // Test that unboxing methods are marked as dead loop safe
    static void testDeadLoop() {
        int j = 0;
        // Nested loops keep the dead parts of the graph alive for long enough
        do {
            for (int i = 0; i < 1; i++) {
                // This always throws
                int res = 42/0;
            }
        } while (j < 0);

        // Unreachable
        while (j < 1) {
            // We assert here when intValue is removed by macro expansion because
            // the dead loop entry created an I/O phi -> call -> proj data loop.
            THE_ANSWER.intValue();
        }
    }

    // Test that unboxing methods are not re-marked as not dead loop safe
    static long testLateInline(Long value) {
        return value.longValue();
    }

    public static void main(String[] args) {
        Asserts.assertThrows(ArithmeticException.class, TestPrimitiveUnboxing::testDeadLoop);
        Asserts.assertEQ(testLateInline(42L), 42L);
    }
}

