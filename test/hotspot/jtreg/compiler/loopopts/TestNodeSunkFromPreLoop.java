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

/**
 * @test
 * @bug 8314024
 * @requires vm.compiler2.enabled
 * @summary Node used in check in main loop sunk from pre loop before RC elimination
 * @run main/othervm -XX:-BackgroundCompilation -XX:-UseLoopPredicate TestNodeSunkFromPreLoop
 *
 */

public class TestNodeSunkFromPreLoop {
    private static int unusedField;

    public static void main(String[] args) {
        A object = new A();
        for (int i = 0; i < 20_000; i++) {
            test(object, 1000, 0);
        }
    }

    private static int test(A object, int stop, int inv) {
        int res = 0;
        // pre/main/post loops created for this loop
        for (int i = 0; i < stop; i++) {
            // Optimized out. Delay transformation of loop above.
            for (int j = 0; j < 10; j++) {
                for (int k = 0; k < 10; k++) {
                }
            }
            // null check in pre loop so field load also in pre loop
            int v = object.field;
            int v2 = (v + inv);
            if (i > 1000) {
                // never taken. v + inv has a use out of loop at an unc.
                unusedField = v2;
            }
            // transformed in the main loop to i + (v + inv)
            int v3 = (v + (i + inv));
            if (v3 > 1000) {
                break;
            }
        }
        return res;
    }

    private static class A {
        public int field;
    }
}
