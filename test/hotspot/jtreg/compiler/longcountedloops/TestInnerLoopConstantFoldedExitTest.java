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
 * @bug 8375639
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:StressLongCountedLoop=1 -XX:+AlwaysIncrementalInline
 *                   -Xbatch -XX:CompileCommand=compileonly,${test.main.class}::test ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.longcountedloops;

public class TestInnerLoopConstantFoldedExitTest {
    static int offset = 1;
    static final char[] array = {'a', 'b'};

    static void loop(String s, int off) {
        for (int i = 0; i < 2; i++) {
            if (array[off + i] != s.charAt(i)) {
                return;
            }
        }
    }

    static void test() {
        int start = offset - 1;
        loop("cd", start);
        loop("ab", start);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 50_000; i++) {
            test();
        }
    }
}

