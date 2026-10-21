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

/*
 * @test
 * @bug 8394960
 * @summary [Valhalla] unitialized field when null value returned from the interpreter
 * @enablePreview
 * @run main ${test.main.class}
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:CompileCommand=exclude,${test.main.class}::get
 *                   -XX:CompileCommand=dontinline,${test.main.class}::safepoint
 *                   -XX:CompileOnly=${test.main.class}::* ${test.main.class}
 */

public class TestValueUnitializedFieldsAtSafepoint {
    static value record V(Object o0, Object o1, Object o2,
                          Object o3, Object o4, Object o5) {
    }

    static V get() {
        return null;
    }

    static void safepoint() {
        System.gc();
    }

    static int test() {
        V v = get();
        safepoint();
        return v == null ? 42 : v.o0.hashCode();
    }

    public static void main(String[] args) {
        if (test() != 42) {
            throw new RuntimeException("wrong result");
        }
    }
}
