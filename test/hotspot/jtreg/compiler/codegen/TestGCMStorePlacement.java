/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package compiler.codegen;

import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8255763
 * @summary Tests GCM's store placement for reducible and irreducible CFGs.
 * @library /test/lib /
 * @run main/othervm -Xbatch compiler.codegen.TestGCMStorePlacement reducible
 * @run main/othervm -Xbatch compiler.codegen.TestGCMStorePlacement irreducible
 */

public class TestGCMStorePlacement {

    static int counter;

    // Reducible case: counter++ should not be placed into the loop.
    static void testReducible() {
        counter++;
        int acc = 0;
        for (int i = 0; i < 50; i++) {
            if (i % 2 == 0) {
                acc += 1;
            }
        }
        return;
    }

    // Irreducible case (due to OSR compilation): counter++ should not be placed
    // outside its switch case block.
    static void testIrreducible() {
        for (int i = 0; i < 30; i++) {
            switch (i % 3) {
            case 0:
                for (int j = 0; j < 50; j++) {
                    // OSR enters here.
                    for (int k = 0; k < 7000; k++) {}
                    if (i % 2 == 0) {
                        break;
                    }
                }
                counter++;
                break;
            case 1:
                break;
            case 2:
                break;
            }
        }
        return;
    }

    public static void main(String[] args) {
        switch (args[0]) {
        case "reducible":
            // Cause a regular C2 compilation of testReducible.
            for (int i = 0; i < 100_000; i++) {
                counter = 0;
                testReducible();
                Asserts.assertEQ(counter, 1);
            }
            break;
        case "irreducible":
            // Cause an OSR C2 compilation of testIrreducible.
            counter = 0;
            testIrreducible();
            Asserts.assertEQ(counter, 10);
            break;
        default:
            System.out.println("invalid mode");
        }
    }
}
