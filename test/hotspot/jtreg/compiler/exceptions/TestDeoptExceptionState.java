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
 */

/**
* @test
* @bug 8316422
* @summary Test exception state used for deoptimization.
* @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyStack -XX:+DeoptimizeALot
*      -Xcomp -XX:TieredStopAtLevel=1 -XX:CompileOnly=compiler.exceptions.TestDeoptExceptionState::test
*      compiler.exceptions.TestDeoptExceptionState
*/

package compiler.exceptions;

public class TestDeoptExceptionState {
    private static int res = 0;

    public static void main(String args[]) {
        int x = 42;
        int y = 1 + test();
        System.out.println("Foo " + x + " " + y);
    }

    public static int test() {
      int x = 42;
      int y = 1 + test1();
      return x + y;
    }

    public static int test1() {
        for (int i = 0; i < 100; i++) {
            try {
                divZero();
            } catch (ArithmeticException ea) {
                // Expected
            }
        }
        return 1;
    }

    public static void divZero() {
        res += div(0, 0);
    }

    public static long div(long dividend, long divisor) {
        return dividend / divisor;
    }
}
