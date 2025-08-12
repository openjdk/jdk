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
 * @run driver compile_framework.examples.MultiFileJavaExample
 */

package compile_framework.examples;

import compiler.lib.compile_framework.*;
import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * This test shows a compilation of multiple java source code files.
 */
public class MultiFileJavaExample {

    // Generate a source java file as String
    public static String generate(int i) {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println("package p.xyz;");
        out.println("");
        out.println("public class XYZ" + i + " {");
        if (i > 0) {
            out.println("    public XYZ" + (i - 1) + " xyz = new XYZ" + (i - 1) + "();");
        }
        out.println("");
        out.println("    public static Object test() {");
        out.println("        return new XYZ" + i + "();");
        out.println("    }");
        out.println("}");
        return writer.toString();
    }

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Generate 10 files.
        for (int i = 0; i < 10; i++) {
            comp.addJavaSourceCode("p.xyz.XYZ" + i, generate(i));
        }

        // Compile the source files.
        comp.compile();

        // Object ret = XYZ9.test();
        Object ret = comp.invoke("p.xyz.XYZ9", "test", new Object[] {});

        if (!ret.getClass().getSimpleName().equals("XYZ9")) {
            throw new RuntimeException("wrong result:" + ret);
        }
        System.out.println("Success.");
    }
}
