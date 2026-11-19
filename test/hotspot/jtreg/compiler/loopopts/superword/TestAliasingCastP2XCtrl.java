/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=all-flags
 * @bug 8366490
 * @summary Test that we set the ctrl of CastP2X when generating
 *          the aliasing runtime check, preventing the CastP2X
 *          from floating over a SafePoint that could move the oop,
 *          and render the cast value stale.
 * @requires vm.gc == "G1" | vm.gc == "null"
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,*TestAliasingCastP2XCtrl::test
 *      -XX:CompileCommand=dontinline,*TestAliasingCastP2XCtrl::allocateArrays
 *      -XX:-TieredCompilation
 *      -Xbatch
 *      -XX:+UseG1GC
 *      -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM
 *      compiler.loopopts.superword.TestAliasingCastP2XCtrl
 */

/*
 * @test id=fewer-flags
 * @bug 8366490
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,*TestAliasingCastP2XCtrl::test
 *      -XX:CompileCommand=dontinline,*TestAliasingCastP2XCtrl::allocateArrays
 *      -Xbatch
 *      -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM
 *      compiler.loopopts.superword.TestAliasingCastP2XCtrl
 */

/*
 * @test id=vanilla
 * @bug 8366490
 * @run main compiler.loopopts.superword.TestAliasingCastP2XCtrl
 */

package compiler.loopopts.superword;

public class TestAliasingCastP2XCtrl {
    static final int N = 400;
    static boolean flag = false;

    static void allocateArrays() {
        for (int i = 0; 200_000 > i; ++i) {
            int[] a = new int[N];
        }
        // Makes GC more likely.
        // Without it I could not reproduce it on slowdebug,
        // but only with fastdebug.
        if (flag) { System.gc(); }
        flag = !flag;
    }

    static int[] test() {
        int a[] = new int[N];
        // We must make sure that no CastP2X happens before
        // the call below, otherwise we may have an old oop.
        allocateArrays();
        // The CastP2X for the aliasing runtime check should
        // only be emitted after the call, to ensure we only
        // deal with oops that are updated if there is a GC
        // that could move our allocated array.

        // Not fully sure why we need the outer loop, but maybe
        // it is needed so that a part of the check is hoisted,
        // and the floats up, over the call if we do not set
        // the ctrl.
        for (int k = 0; k < 500; k++) {
            for (int i = 1; i < 69; i++) {
                // Aliasing references -> needs runtime check,
                // should always fail.
                a[i] =  14;
                a[4] -= 14;
                // The range computation for the constant access
                // produces a shape:
                //   AddL(CastP2X(a), 0x20)
                // And this shape only depends on a, so it could
                // easily float above the call to allocateArrays
                // if we do not set a ctrl that prevents that.
            }
        }
        return a;
    }

    public static void main(String[] args) {
        int[] gold = test();
        for (int r = 0; r < 20; r++) {
            int[] a = test();
            if (a[4] != gold[4]) {
                throw new RuntimeException("wrong value " + gold[4] + " " + a[4]);
            }
        }
    }
}
