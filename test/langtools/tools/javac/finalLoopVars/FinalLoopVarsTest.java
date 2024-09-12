/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8338711
 * @summary Test final for() loop variables may be mutated in the step, etc.
 * @enablePreview
 */

import java.util.function.*;

public class FinalLoopVarsTest {

    // Test: a final loop variable can be mutated in the step
    public static int test1() {
        int total = 0;
        for (final int i = 0; i < 3; i++)
            total += i;
        return total;
    }

    // Test: a final loop variable can be captured by a lambda in the body
    public static int test2() {
        int total = 0;
        for (final int i = 0; i < 3; i++) {
            IntSupplier s = () -> i;
            total += s.getAsInt();
        }
        return total;
    }

    // Test: a non-final loop variable can be captured by a lambda in the body
    public static int test3() {
        int total = 0;
        for (int i = 0; i < 3; i++) {
            IntSupplier s = () -> i;
            total += s.getAsInt();
        }
        return total;
    }

    // Test: step mutations can appear in nested expressions
    public static int test4() {
        int total = 0;
        for (final int i = 0;
          i < 3;
          i = switch (0) { default -> ++i; })
            total += i;
        return total;
    }

    // Test: mutated final loop variables do not have constant values
    public static int test5() {
        int total = 0;
        int max = 0;
        for (final int i = 0; i == 0; max = ++i)
            total += i;
        return max * 10 + total;        // this statement is reachable
    }

    // Test: mutated final loop variables do not have constant values
    public static int test6() {
        int total = 0;
        int max = 0;
        for (final int i = 0; i != 0; max = ++i)
            total += i;                 // this statement is reachable
        return max * 10 + total;
    }

    // Test: weird nesting doesn't confuse the analysis logic
    public static int test7() {
        int total = 0;
        for (final int i = 0;
          i < 3;
          i++, new Object() {{
                                for (int i = 0; i < 3; i++)
                                    i *= 2;
                            }})
            total += i;
        return total;
    }

    // Test: final loop variables without initializers don't change
    public static int test8() {
        int total = 0;
        for (final int i; true; i++) {
            total++;
            break;
        }
        return total;
    }

    public static void main(String[] args) {
        verify(test1(), 3);
        verify(test2(), 3);
        verify(test3(), 3);
        verify(test4(), 3);
        verify(test5(), 10);
        verify(test6(), 0);
        verify(test7(), 3);
        verify(test8(), 1);
    }

    public static void verify(int actual, int expected) {
        if (actual != expected)
            throw new AssertionError(String.format("expected %d but got %d", expected, actual));
    }
}
