/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Example test to use the Compile Framework.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver comile_framework.examples.MultiFileJasmExample
 */

package comile_framework.examples;

import compiler.lib.compile_framework.*;
import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * This test shows a compilation of multiple jasm source code files.
 */
public class MultiFileJasmExample {

    // Generate a source jasm file as String
    public static String generate(int i) {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println("package p/xyz;");
        out.println("");
        out.println("super public class XYZ" + i + " {");
        out.println("    public static Method test:\"(I)I\"");
        out.println("    stack 20 locals 20");
        out.println("    {");
        out.println("        iload_0;");
        out.println("        iconst_2;"); // every call multiplies by 2, in total 2^10 = 1024
        out.println("        imul;");
        if (i != 0) {
            out.println("        invokestatic Method p/xyz/XYZ" + (i-1) + ".\"test\":\"(I)I\";");
        }
        out.println("        ireturn;");
        out.println("    }");
        out.println("}");
        return writer.toString();
    }

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Generate 10 files.
        for (int i = 0; i < 10; i++) {
            comp.addJasmSourceCode("p.xyz.XYZ" + i, generate(i));
        }

        // Compile the source files.
        comp.compile();

        // Object ret = XYZ9.test(5);
        Object ret = comp.invoke("p.xyz.XYZ9", "test", new Object[] { 5 });

        // Extract return value of invocation, verify its value.
        int i = (int)ret;
        System.out.println("Result of call: " + i);
        if (i != 5 * 1024) {
            throw new RuntimeException("wrong value: " + i);
        }
        System.out.println("Success.");
    }
}
