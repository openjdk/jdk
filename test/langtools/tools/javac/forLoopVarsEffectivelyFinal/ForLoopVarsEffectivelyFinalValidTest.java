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
 * @summary Check for valid cases relating to for() loop variables being effectively final in the body
 */

import java.io.*;
import java.util.function.*;

public class ForLoopVarsEffectivelyFinalValidTest {

    // Test: a loop variable modified in INIT can be captured by a lambda in BODY
    public static int test1() {
        int total = 0;
        for (int i = 1, j = ++i; i <= 3; i++) {
            IntSupplier s = () -> i;
            total += s.getAsInt();
        }
        return total;
    }

    // Test: a loop variable modified in CONDITION can be captured by a lambda BODY
    public static int test2() {
        int total = 0;
        for (int i = 1; i++ <= 3; ) {
            IntSupplier s = () -> i;
            total += s.getAsInt();
        }
        return total;
    }

    // Test: a loop variable modified in STEP can be captured by a lambda BODY
    public static int test3() {
        int total = 0;
        for (int i = 1; i <= 3; i++) {
            IntSupplier s = () -> i;
            total += s.getAsInt();
        }
        return total;
    }

    // Test: complicated nesting doesn't confuse the analysis logic
    public static int test4() {
        int total = 0;
        for (int i = 1; i <= 3; i++) {
            IntSupplier s = () -> {
                int total2 = 0;
                for (int i2 = i; i2 <= 3; i2++) {
                    IntSupplier s2 = () -> i + i2;
                    total2 += s2.getAsInt();
                }
                return total2;
            };
            total += s.getAsInt();
        }
        return total;
    }

    // Test: complicated nesting doesn't confuse the analysis logic
    public static int test5() {
        int total = 0;
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 3; j++) {
                IntSupplier s = () -> i + j;
                total += s.getAsInt();
            }
        }
        return total;
    }

    // Test: the variable's assigment may happen in the body
    public static int test6() {
        int total = 0;
        int j = 0;
        for (int i; j < 3; ) {
            i = ++j;
            IntSupplier s = () -> i;
            total += s.getAsInt();
        }
        return total;
    }

    // Test: effectively final in the loop works with try-with-resources statements
    public static void test7() throws IOException {
        final InputStream input1 = new ByteArrayInputStream(new byte[0]);
        final InputStream input2 = new ByteArrayInputStream(new byte[0]);
        for (InputStream input = input1; true; input = input2) {
            try (input) {
                // nothing
            }
            if (input == input2)
                break;
        }
    }

    public static void main(String[] args) throws IOException {
        verify(test1(), 5);
        verify(test2(), 9);
        verify(test3(), 6);
        verify(test4(), 24);
        verify(test5(), 36);
        verify(test6(), 6);
        test7();
    }

    public static void verify(int actual, int expected) {
        if (actual != expected)
            throw new AssertionError(String.format("expected %d but got %d", expected, actual));
    }
}
