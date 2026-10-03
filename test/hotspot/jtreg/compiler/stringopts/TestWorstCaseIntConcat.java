/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug 8387067
 * @summary Exercise the worst-case per-argument node expansion of the C2 string
 *          concat optimization: a non-constant String prefix keeps the destination
 *          coder unknown, so int_getChars emits BOTH the Latin1 and UTF16 digit
 *          loops for every int argument. On a fastdebug build this validates the
 *          `nodes_created <= num_arguments * estimated_nodes_per_concat_arg` assert
 *          in PhaseStringOpts. The argument count is kept moderate so the concat
 *          stays under the node-budget guard and actually reaches
 *          replace_string_concat (otherwise the guard skips it and the assert
 *          never runs).
 * @run main/othervm -Xbatch compiler.stringopts.TestWorstCaseIntConcat
 * @run main/othervm -Xcomp
 *      -XX:CompileCommand=compileonly,compiler.stringopts.TestWorstCaseIntConcat::test
 *      compiler.stringopts.TestWorstCaseIntConcat
 */

package compiler.stringopts;

public class TestWorstCaseIntConcat {

    public static void main(String[] args) {
        // Non-constant prefix -> destination coder is unknown at compile time,
        // forcing both the Latin1 and UTF16 code paths in int_getChars.
        String prefix = args.length > 0 ? args[0] : "éprefix"; // Latin1-ish default
        String out = null;
        for (int i = 0; i < 20_000; i++) {
            out = test(prefix, i);
        }
        // Consume the result so nothing can be dead-code eliminated.
        if (out == null || out.isEmpty()) {
            throw new AssertionError("unexpected empty result");
        }
    }

    // One unknown-coder String argument + many int arguments. Integer.MIN_VALUE is
    // mixed in so the MIN_VALUE special-case branch is exercised too. Each int
    // argument therefore hits the most expensive path in int_getChars.
    public static String test(String prefix, int i) {
        return new StringBuilder()
            .append(prefix)
            .append(Integer.MIN_VALUE)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .append(i).append(i).append(i).append(i).append(i).append(i).append(i).append(i)
            .toString();
    }
}
