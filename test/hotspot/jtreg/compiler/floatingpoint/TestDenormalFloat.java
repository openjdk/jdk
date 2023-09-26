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
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      compiler.floatingpoint.TestDenormalFloat
 */

package compiler.floatingpoint;

import static java.lang.System.loadLibrary;
import jdk.test.whitebox.WhiteBox;

public class TestDenormalFloat {
    private final static WhiteBox wb = WhiteBox.getWhiteBox();

    static volatile float lastFloat;

    private static void testFloats(String name) {
        lastFloat = 0x1.0p-149f;
        for (float x = lastFloat * 2; x <= 0x1.0p127f; x *= 2) {
            if (x != x || x <= lastFloat) {
                throw new AssertionError("TEST FAILED: " + name + ", " + x);
            }
            lastFloat = x;
        }
    }

    public native static void breakThings();

    public static void main(String[] args) {
        testFloats("0");
        System.out.println("Loading libfast-math.so");
        loadLibrary("fast-math");
        testFloats("1");
        breakThings();
        wb.forceSafepoint();
        testFloats("2");
        System.out.println("Test passed.");
    }
}
