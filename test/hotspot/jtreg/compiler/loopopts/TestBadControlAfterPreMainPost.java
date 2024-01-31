/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * bug 8315920
 * @summary C2: "control input must dominate current control" assert failure
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:-BackgroundCompilation -XX:-UseLoopPredicate -XX:-DoEscapeAnalysis TestBadControlAfterPreMainPost
 */

public class TestBadControlAfterPreMainPost {
    private static volatile int volatileField;

    public static void main(String[] args) {
        int[] array2 = new int[100];
        for (int i = 0; i < 20_000; i++) {
            test(1, array2);
        }
    }

    private static int test(int j, int[] array2) {
        int[] array = new int[10];
        array[j] = 42;
        float f = 1;
        for (int i = 0; i < 100; i++) {
            for (int k = 0; k < 10; k++) {
            }
            f = f * 2;
        }
        int v = array[0];
        int i = 0;
        do {
            synchronized (new Object()) {
            }
            array2[i + v] = 42;
            i++;
        } while (i < 100);
        return (int)f;
    }
}
