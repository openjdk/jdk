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
 * @bug 8356246
 * @summary Test stacked string concatenations where the toString of the first StringBuilder
 *          is used as a shared test by two diamond Ifs in the second StringBuilder.
 * @run main/othervm compiler.stringopts.TestStackedConcatsSharedTest
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *                   -XX:CompileOnly=compiler.stringopts.TestStackedConcatsSharedTest::*
 *                   compiler.stringopts.TestStackedConcatsSharedTest
 */

package compiler.stringopts;

public class TestStackedConcatsSharedTest {

    public static void main(String... args) {
        f(); // one warmup call
        String s = f();
        if (!s.equals("")) {
            throw new RuntimeException("wrong result");
        }
    }

    static String f() {
        String s = "";
        s = new StringBuilder().toString();
        // Warming up with many iterations invalidated the optimization due to an unstable If
        // associated with the valueOf calls below. Using -Xcomp for the test.
        s = new StringBuilder(String.valueOf(s)).append(String.valueOf(s)).toString();
        return s;
    }
}
