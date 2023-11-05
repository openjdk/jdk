/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8303045
 * @compile TestInlinedSplitFallInIrreducibleLoopStatus.jasm
 * @summary Regions that are inlined are by default tagged as NeverIrreducibleEntry.
 *          Test that if a split_fall_in happens to such a region, we do not throw
 *          a spurious assert.
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,TestInlinedSplitFallInIrreducibleLoopStatus::test*
 *      -XX:CompileCommand=compileonly,TestInlinedSplitFallInIrreducibleLoopStatusMain::test*
 *      -Xbatch -XX:PerMethodTrapLimit=0
 *      TestInlinedSplitFallInIrreducibleLoopStatusMain
 */

public class TestInlinedSplitFallInIrreducibleLoopStatusMain {
    public static void main(String[] strArr) {
        for (int i = 0; i < 10_000; i++) {
            test_outer(0, 0, 0);
        }
    }
    static void test_outer(int v0, int v1, int v2) {
        // inline method test_inner
        TestInlinedSplitFallInIrreducibleLoopStatus.test_inner(v0, v1, v2);
    }
}
