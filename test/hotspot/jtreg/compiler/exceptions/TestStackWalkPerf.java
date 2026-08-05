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

/**
 * @test
 * @bug 8389130
 * @summary With Valhalla, frame::sender became a bit too big and is not as spontaneously inlined as before.
 *          This causes some measurable performance regressions in cases where walking the stack is frequent.
 * @run main/othervm -Xbatch
 *                   -XX:-TieredCompilation
 *                   -XX:CompileCommand=dontinline,${test.main.class}::fillInStackTrace
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.exceptions;

public class TestStackWalkPerf extends Throwable {
    private static final TestStackWalkPerf PROBE = new TestStackWalkPerf();

    private static void fillInStackTrace(int depth, int fills) {
        if (depth == 0) {
            for (int i = 0; i < fills; i++) {
                PROBE.fillInStackTrace();
            }
            return;
        }
        fillInStackTrace(depth - 1, fills);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            fillInStackTrace(512, 1);
        }
        fillInStackTrace(512, 1_000_000);
    }
}
