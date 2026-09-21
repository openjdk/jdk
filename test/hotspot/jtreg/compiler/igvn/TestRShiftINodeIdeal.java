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

package compiler.igvn;

/*
 * @test
 * @bug 8385093
 * @summary Test that IGVN revisits RShiftI when a LoadUS is down to one output in
 *          (LoadUS(...) << 16) >> 16 sign extension pattern.
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                   -Xcomp
 *                   -XX:CompileCommand=compileonly,${test.main.class}$Z::f
 *                   -XX:CompileCommand=dontinline,${test.main.class}$X::<init>
 *                   -XX:VerifyIterativeGVN=1110
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */
public class TestRShiftINodeIdeal {
    static class X {
        char x = 1;
    }

    static class Y {
        short y = (short)new X().x;
    }

    static class Z {
        static short z = new Y().y;

        public static int f() {
            return new Y().y;
        }
    }
    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            Z.f();
        }
    }
}
