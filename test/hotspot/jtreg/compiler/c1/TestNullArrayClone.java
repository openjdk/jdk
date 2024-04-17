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
 * @bug 8302850
 *
 * @run main/othervm -XX:-UseOnStackReplacement -XX:-BackgroundCompilation -XX:TieredStopAtLevel=1
 *                   -XX:CompileOnly=compiler.c1.TestNullArrayClone::test -XX:+UnlockExperimentalVMOptions
 *                   -XX:CompileCommand=blackhole,compiler.c1.TestNullArrayClone::blackhole
 *                   compiler.c1.TestNullArrayClone
 */
package compiler.c1;

import java.util.concurrent.ThreadLocalRandom;

public class TestNullArrayClone {
    public static void main(String[] args) {
        final int size = 10;
        final int[] ints = new int[size];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = ThreadLocalRandom.current().nextInt();
        }

        for (int i = 0; i < 1_000; i++) {
            int[] result = test(ints);
            blackhole(result);
        }

        try {
            test(null);
            throw new RuntimeException("Expected NullPointerException to be thrown");
        } catch (NullPointerException e) {
        }
    }

    static int[] test(int[] ints) {
        return ints.clone();
    }

    static void blackhole(Object obj) {}
}
