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
 * @summary Test stacked string concatenations where the toString result
 *          of the first StringBuilder chain is wired into an uncommon trap
 *          located in the second one.
 * @run main/othervm compiler.stringopts.TestStackedConcatsAppendUncommonTrap
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileOnly=compiler.stringopts.TestStackedConcatsAppendUncommonTrap::*
 *                   compiler.stringopts.TestStackedConcatsAppendUncommonTrap
 */

package compiler.stringopts;

public class TestStackedConcatsAppendUncommonTrap {

    public static void main (String... args) {
        for (int i = 0; i < 10000; i++) {
            String s = f(" ");
            if (!s.equals("    ")) {
                throw new RuntimeException("wrong result.");
            }
        }
    }

    static String f(String c) {
        String s = " ";
        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s == c ? s : "  ").toString();
        return s;
    }
}
