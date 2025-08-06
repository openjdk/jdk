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
 * @bug 8357105
 * @summary Test that repeated stacked string concatenations do not
 *          consume too many compilation resources.
 * @run main/othervm compiler.stringopts.TestStackedConcatsMany
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *                   -XX:CompileOnly=compiler.stringopts.TestStackedConcatsMany::*
 *                   compiler.stringopts.TestStackedConcatsMany
 */

package compiler.stringopts;

public class TestStackedConcatsMany {

    public static void main (String... args) {
        for (int i = 0; i < 10; i++) {
            String s = f(" ");
        }
    }

    static String f(String c) {
        String s = " ";
        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        return s;
    }
}
