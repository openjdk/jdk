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
 * @run driver comile_framework.examples.CombinedJavaJasmExample
 */

package comile_framework.examples;

import compiler.lib.compile_framework.*;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

/**
 * This test shows a compilation of multiple java and jasm source code files.
 * In this example, the classes even reference each other.
 */
public class CombinedJavaJasmExample {

    // Generate a source jasm file as String
    public static String generateJasm() {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println("package p/xyz;");
        out.println("");
        out.println("super public class XYZJasm {");
        out.println("    public static Method test:\"(I)I\"");
        out.println("    stack 20 locals 20");
        out.println("    {");
        out.println("        iload_0;");
        out.println("        iconst_2;");
        out.println("        imul;");
        out.println("        invokestatic Method p/xyz/XYZJava.\"mul3\":\"(I)I\";"); // reference java class
        out.println("        ireturn;");
        out.println("    }");
        out.println("");
        out.println("    public static Method mul5:\"(I)I\"");
        out.println("    stack 20 locals 20");
        out.println("    {");
        out.println("        iload_0;");
        out.println("        ldc 5;");
        out.println("        imul;");
        out.println("        ireturn;");
        out.println("    }");
        out.println("}");
        out.close();
        return writer.toString();
    }

    // Generate a source java file as String
    public static String generateJava() {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println("package p.xyz;");
        out.println("");
        out.println("public class XYZJava {");
        out.println("    public static int test(int i) {");
        out.println("        return p.xyz.XYZJasm.mul5(i * 7);"); // reference jasm class
        out.println("    }");
        out.println("");
        out.println("    public static int mul3(int i) {");
        out.println("        return i * 3;");
        out.println("    }");
        out.println("}");
        out.close();
        return writer.toString();
    }

    public static void main(String args[]) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Generate files.
        comp.add(SourceFile.newJasmSourceFile("p.xyz.XYZJasm", generateJasm()));
        comp.add(SourceFile.newJavaSourceFile("p.xyz.XYZJava", generateJava()));

        // Compile the source files.
        comp.compile();

        // Load the compiled class.
        Class xyzJasm = comp.getClass("p.xyz.XYZJasm");
        Class xyzJava = comp.getClass("p.xyz.XYZJava");

        test(xyzJasm, 11, 11 * 2 * 3);
        test(xyzJava, 13, 13 * 7 * 5);

        System.out.println("Success.");
    }

    public static void test(Class c, int input, int expected) {
        Object ret;
        try {
            ret = c.getDeclaredMethod("test", new Class[] { int.class }).invoke(null, new Object[] { input });
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("No such method:", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Illegal access:", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Invocation target:", e);
        }

        // Extract return value of invocation, verify its value.
        int i = (int)ret;
        System.out.println("Result of call: " + i + " vs expected: " + expected);
        if (i != expected) {
            throw new RuntimeException("wrong value: " + i);
        }
 
    }
}
