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

/**
 * @test
 * @bug 8388848
 * @summary Test EA with wide arguments and mixed scalarized calling convention.
 * @enablePreview
 * @library /test/lib /
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-PreloadClasses
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   -XX:CompileCommand=dontinline,${test.main.class}*::test*
 *                   ${test.main.class}
 */

import jdk.test.lib.Asserts;

public class TestEAWideArg {

    // When the test methods below are linked, 'Unloaded' is still unloaded,
    // creating a mix of scalarized and non-scalarized value arguments.
    static value record Unloaded(int value) { }

    static Unloaded testLongCallee(long l, Integer i1, Unloaded unloaded, Integer i2) {
        return unloaded;
    }

    static Unloaded testDoubleCallee(double l, Integer i1, Unloaded unloaded, Integer i2) {
        return unloaded;
    }

    static Unloaded testLong(int value) {
        return testLongCallee(value, value, new Unloaded(value), value);
    }

    static Unloaded testDouble(int value) {
        return testDoubleCallee(value, value, new Unloaded(value), value);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 50_000; i++) {
            Asserts.assertEquals(testLong(i).value, i);
            Asserts.assertEquals(testDouble(i).value, i);
        }
    }
}

