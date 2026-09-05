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
 * @bug 8384283
 * @summary Test post-parse call devirtualization with an unloaded return type.
 * @enablePreview
 * @run main ${test.main.class}
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}::*
 *                   ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

public class TestCallDevirtualizationWithUnloadedReturn {

    static interface BaseClass {
        public Unloaded method();
    }

    static class Impl1 implements BaseClass {
        public Unloaded method() {
            return null;
        }
    }

    static class Impl2 implements BaseClass {
        public Unloaded method() {
            return null;
        }
    }

    static class Impl3 implements BaseClass {
        public Unloaded method() {
            return null;
        }
    }

    static class Unloaded { }

    static Object test() {
        BaseClass receiver = new Impl1();
        // Hide the fact that receiver type is Impl2 until after loop
        // opts to trigger post-parse call devirtualization below.
        for (int i = 0; i < 3; i++) {
            if (i > 1) {
                receiver = new Impl2();
            }
        }
        // This is strength-reduced to a static call but since the
        // return type is unloaded it might be a scalarized return.
        return receiver.method();
    }

    public static void main(String[] args) {
        for (int i = 0; i < 50_000; i++) {
            test();
        }
    }
}
