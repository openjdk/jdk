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

/*
 * @test
 * @bug 8389214
 * @summary Test that a StoreFlatNode with a TOP value input is properly handled by IGVN.
 * @enablePreview
 * @run main ${test.main.class}
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -Xcomp
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AlwaysIncrementalInline
 *                   -XX:+StressIGVN -XX:StressSeed=331205763
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

public class TestDeadStoreFlat {
    static Integer box() {
        throw new RuntimeException();
    }

    static Integer[] test() {
        try {
            Integer boxed = box();
            return new Integer[] { boxed };
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void main(String[] args) {
        // Make sure exception class loaded
        RuntimeException tmp = new RuntimeException();
        test();
    }
}

