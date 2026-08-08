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
 * @bug 8384260
 * @summary Test that C2 does not crash when re-using an overflowed StringConcat.
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run main/othervm ${test.main.class}
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *                   -XX:CompileOnly=compiler.stringopts.TestStackedConcatsManyOverflow::*
 *                   -XX:CompileCommand=inline,compiler.stringopts.TestStackedConcatsManyOverflow::double30
 *                   -XX:CompileCommand=dontinline,java.lang.String::valueOf
 *                   ${test.main.class}
 */

package compiler.stringopts;

public class TestStackedConcatsManyOverflow {

    public static void main (String... args) {
        new StringBuilder(); // Trigger loading of the StringBuilder class.

        try {
            String s = f();
            throw new RuntimeException("unreachable");
        }
        catch (OutOfMemoryError e) {
          // expected OOME, for example "java.lang.OutOfMemoryError: Required array length 1073741824 + 1073741824 is too large"
          ;
        }

        try {
            String s = g();
            throw new RuntimeException("unreachable");
        }
        catch (OutOfMemoryError e) {}

        try {
            String s = h();
            throw new RuntimeException("unreachable");
        }
        catch (OutOfMemoryError e) {}

    }

    static String double30() {
        String s = "ab";
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
        s = new StringBuilder().append(s).append(s).toString();

        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();

        return s;
    }

    // SIGSEGV (0xb) in PhaseStringOpts::get_constant_coder(GraphKit&, Node*)
    static String f() {
        // Creating an overflow top()-producing StringConcat
        // and a few more doublings to get a target concat receiving top() as an input argument.
        String s = double30();
        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append(s).toString();
        // stringopts sets the toString result to top(). Next, it's used in append(s) but stringopts expected live input.
        s = new StringBuilder().append('a').append(s).toString();
        return s;
    }

    // assert(Compile::current()->inlining_incrementally()) failed: shouldn't happen during parsing
    static String g() {
        String s = double30();
        s = new StringBuilder().append(s).append(s).toString();
        // if valueOf is not inlined, append will stay as a live late inlining candidate but with a top() input.
        s = new StringBuilder().append(s).append(String.valueOf(s)).toString();
        return s;
    }

    // assert(Compile::current()->inlining_incrementally()) failed: shouldn't happen during parsing
    static String h() {
        String s = double30();
        s = new StringBuilder().append(s).append(s).toString();
        // Overflowed and have a StringConcat returning top()
        // Non-fluent chain that has an append late inline candidate that consumes top
        StringBuilder sb = new StringBuilder();
        sb.append(s);
        return sb.toString();
    }
}
