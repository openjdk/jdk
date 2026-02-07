/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8362117
 * @summary Test stacked string concatenations where the toString result
 *          of the first StringBuilder chain is used as a test for a
 *          simple diamond in the second StringBuilder. If the region of
 *          the simple diamond has a Phi that is used as a parameter in the
 *          concatenation, a wrong result should not be produced.
 * @library /test/lib /
 * @run main/othervm compiler.stringopts.TestStackedConcatsPhiUseOfDiamondRegion
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *                   -XX:CompileOnly=compiler.stringopts.TestStackedConcatsPhiUseOfDiamondRegion::f
 *                   compiler.stringopts.TestStackedConcatsPhiUseOfDiamondRegion
 */

package compiler.stringopts;

import jdk.test.lib.Asserts;

public class TestStackedConcatsPhiUseOfDiamondRegion {

    public static void main (String... args) {
        new StringBuilder(); // load the class
        f();
    }

    static String f() {
        String s = "a";
        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append((s == "xx") ? s : "aa").toString();
        Asserts.assertEQ(s, "aaaa"); // in particular, we should not have s.equals("aaxx").
        return s;
    }
}
