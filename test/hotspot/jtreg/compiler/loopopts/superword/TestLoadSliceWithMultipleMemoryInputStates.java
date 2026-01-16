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
 * @test id=all-flags
 * @summary Test a case where we can have one memory slice that has only loads,
 *          but the loads from the slice do not have all the same input memory
 *          state from before the loop. This is rather rare but it can happen.
 * @bug 8373453
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,${test.main.class}::test
 *      -Xbatch -XX:-TieredCompilation
 *      ${test.main.class}
 */

/*
 * @test id=fewer-flags
 * @bug 8373453
 * @run main/othervm
 *      -XX:CompileCommand=compileonly,${test.main.class}::test
 *      ${test.main.class}
 */

/*
 * @test id=vanilla
 * @bug 8373453
 * @run main ${test.main.class}
 */

package compiler.loopopts.superword;

public class TestLoadSliceWithMultipleMemoryInputStates {
    static void test() {
        // The relevant slice is the value field of the Byte Objects.
        Byte x = 1;

        for (int i = 0; i < 2; i++) {
            if ((i & 1) == 0) {
                // Not sure what this loop is needed for, but it is very sensitive,
                // I cannot even replace N with 32.
                int N = 32;
                for (int j = 0; j < N; j++) {
                    if (j == 1) {
                        x = (byte) x;
                    }
                }

                for (int j = 0; j < 32; j++) {
                    // The call below has an effect on the memory state
                    // If we optimize the Load for Byte::value, we can bypass
                    // this call, since we know that Byte::value cannot be
                    // modified during the call.
                    Object o = 1;
                    o.toString();

                    for (int k = 0; k < 32; k++) { // OSR around here
                        // Loads of x byte field have different memory input states
                        // This is because some loads can split their memory state
                        // through a phi further up, and others are not put back on
                        // the IGVN worklist and are thus not optimized and keep
                        // the old memory state. Both are correct though.
                        x = (byte) (x + 1);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            test();
        }
    }
}
