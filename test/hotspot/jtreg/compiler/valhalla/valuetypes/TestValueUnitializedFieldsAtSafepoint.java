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

package compiler.valhalla.valuetypes;

import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8394960 8395884
 * @summary Test scalarized null value returns with uninitialized fields
 * @enablePreview
 * @library /test/lib
 * @run main ${test.main.class}
 * @run main/othervm -Xcomp -XX:+IgnoreUnrecognizedVMOptions -XX:-PreloadClasses
 *                   -XX:CompileCommand=exclude,${test.main.class}::getInterpreter*
 *                   -XX:CompileCommand=exclude,${test.main.class}::getC1*,1000
 *                   -XX:CompileCommand=exclude,${test.main.class}::run*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::safepoint
 *                   -XX:CompileOnly=${test.main.class}::* ${test.main.class}
 */
public class TestValueUnitializedFieldsAtSafepoint {
    // Six oop fields to use all scalarized return registers on x64
    static value record V6(Object o0, Object o1, Object o2,
                           Object o3, Object o4, Object o5) {
    }

    // Seven oop fields to use all scalarized return registers on AArch64
    static value record V7(Object o0, Object o1, Object o2, Object o3,
                           Object o4, Object o5, Object o6) {
    }

    // This value is passed as argument to make sure the
    // return register is initialized with garbage.
    static final int INVALID_OOP = 0x12345678;

    static V6 getInterpreterX64() {
        return null;
    }

    static V7 getInterpreterAArch64() {
        return null;
    }

    static V6 getC1X64(int value) {
        return null;
    }

    static V7 getC1AArch64(int value) {
        return null;
    }

    static void safepoint() {
        System.gc();
    }

    static int testInterpreterX64() {
        V6 v = getInterpreterX64();
        safepoint();
        return v == null ? 42 : v.o0.hashCode();
    }

    static int testInterpreterAArch64() {
        V7 v = getInterpreterAArch64();
        safepoint();
        return v == null ? 42 : v.o0.hashCode();
    }

    static int testC1X64() {
        V6 v = getC1X64(INVALID_OOP);
        safepoint();
        return v == null ? 42 : v.o5.hashCode();
    }

    static int testC1AArch64() {
        V7 v = getC1AArch64(INVALID_OOP);
        safepoint();
        return v == null ? 42 : v.o6.hashCode();
    }

    static void runX64() {
        getC1X64(INVALID_OOP);
        V6 forceLoad = new V6(null, null, null, null, null, null);
        Asserts.assertEQ(testC1X64(), 42); // Make sure the call is resolved
        Asserts.assertEQ(testC1X64(), 42);
        Asserts.assertEQ(testInterpreterX64(), 42);
    }

    static void runAArch64() {
        getC1AArch64(INVALID_OOP);
        V7 forceLoad = new V7(null, null, null, null, null, null, null);
        Asserts.assertEQ(testC1AArch64(), 42); // Make sure the call is resolved
        Asserts.assertEQ(testC1AArch64(), 42);
        Asserts.assertEQ(testInterpreterAArch64(), 42);
    }

    public static void main(String[] args) {
        runX64();
        runAArch64();
    }
}
