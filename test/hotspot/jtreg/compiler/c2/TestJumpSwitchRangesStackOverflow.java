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
 * @bug 8366138
 * @summary C2: jump_switch_ranges could cause stack overflow when compiling
 *          huge switch statement with zero-count profiling data.
 * @library /test/lib /
 * @run main/othervm -Xbatch
 *                   -XX:-DontCompileHugeMethods
 *                   -XX:-TieredCompilation
 *                   -XX:CompilerThreadStackSize=512
 *                   ${test.main.class}
 */

package compiler.c2;

import compiler.lib.compile_framework.CompileFramework;

public class TestJumpSwitchRangesStackOverflow {
    static final int NUM_CASES = 5000;

    // Generate a class with a large tableswitch method and a main that
    // triggers recompilation with zero-count profiling data:
    //   Phase 1: warm up with default-only path -> C2 compiles
    //   Phase 2: hit a case -> triggers unstable_if deopt
    //   Phase 3: recompile with all counts zero (except the default case)
    static String generate() {
        StringBuilder sb = new StringBuilder();
        sb.append("public class Test {\n");
        sb.append("    static int test(int x) {\n");
        sb.append("        switch (x) {\n");
        for (int i = 0; i < NUM_CASES; i++) {
            sb.append("            case ").append(i).append(": return ").append(i).append(";\n");
        }
        sb.append("            default: return -1;\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        for (int i = 0; i < 50000; i++) { test(-1); }\n");
        sb.append("        test(42);\n");
        sb.append("        for (int i = 0; i < 50000; i++) { test(-1); }\n");
        sb.append("        System.out.println(\"Done\");\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static void main(String[] args) {
        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("Test", generate());
        comp.compile();
        comp.invoke("Test", "main", new Object[] { new String[0] });
    }
}
