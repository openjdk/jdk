/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8295159
 * @summary DSO created with -ffast-math breaks Java floating-point arithmetic
 * @run main/othervm compiler.floatingpoint.TestDenormalFloat
 */

// gcc -g -ffast-math -shared -Lbuild/linux-x86_64-server-release/jdk/lib -ljava -o libfast-math.so libfast-math.c
// LD_LIBRARY_PATH=.:build/linux-x86_64-server-release/jdk/lib:build/linux-x86_64-server-release/jdk/lib/server/ java -cp /tmp compiler.floatingpoint.TestDenormalFloat
//
// Result:
// Loading libfast-math.so
// Exception in thread "main" java.lang.AssertionError: TEST FAILED: 0.0
//      at compiler.floatingpoint.TestDenormalFloat.testFloats(TestDenormalFloat.java:47)
//      at compiler.floatingpoint.TestDenormalFloat.main(TestDenormalFloat.java:57)


package compiler.floatingpoint;

import static java.lang.System.loadLibrary;

public class TestDenormalFloat {
    static volatile float lastFloat;

    private static void testFloats() {
        lastFloat = 0x1.0p-149f;
        for (float x = lastFloat * 2; x <= 0x1.0p127f; x *= 2) {
            if (x != x || x <= lastFloat) {
                throw new AssertionError("TEST FAILED: " + x);
            }
            lastFloat = x;
        }
    }

    public static void main(String[] args) {
        testFloats();
        System.out.println("Loading libfast-math.so");
        loadLibrary("fast-math");
        testFloats();
        System.out.println("Test passed.");
    }
}
