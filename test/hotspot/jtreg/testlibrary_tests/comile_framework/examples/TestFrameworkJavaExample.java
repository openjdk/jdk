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
 * @summary Example test to use the Compile Framework together with the TestFramework.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/ir_framework/TestFramework.java
 * @run driver comile_framework.examples.TestFrameworkJavaExample
 */

package comile_framework.examples;

import compiler.lib.compile_framework.*;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

/**
 * This test shows that the IR verification can be done on code compiled by the Compile Framework.
 * The "@compile" command for JTREG is required so that the TestFramework is compiled, other javac
 * might not compile it because it is not present in the class, only in the dynamically compiled
 * code.
 */
public class TestFrameworkJavaExample {

    public static void main(String args[]) {
        test_X1();
        test_X2();
    }

    // Generate a source java file as String
    public static String generate_X1() {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println("import compiler.lib.ir_framework.*;");
        out.println("");
        out.println("public class X1 {");
        out.println("    public static void main(String args[]) {");
        out.println("        TestFramework.run(X1.class);");
        out.println("    }");
        out.println("");
        out.println("    @Test"); // Test with working IR rule testing for vectorization.
        out.println("    @IR(counts = {IRNode.LOAD_VECTOR_F, \"> 0\"},");
        out.println("        applyIfCPUFeatureOr = {\"sse2\", \"true\", \"asimd\", \"true\"})");
        out.println("    static float[] test() {");
        out.println("        float[] a = new float[1024*8];");
        out.println("        for (int i = 0; i < a.length; i++) {");
        out.println("            a[i]++;");
        out.println("        }");
        out.println("        return a;");
        out.println("    }");
        out.println("}");
        out.close();
        return writer.toString();
    }

    static void test_X1() {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        String src = generate_X1();
        SourceFile file = new SourceFile("X1", src);
        comp.add(file);

        // Compile the source file.
        comp.compile();

        // Load the compiled class.
        Class c = comp.getClass("X1");

        // Invoke the "X1.main" method from the compiled and loaded class.
        try {
            c.getDeclaredMethod("main", new Class[] { String[].class }).invoke(null, new Object[] { null });
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("No such method:", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Illegal access:", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Invocation target:", e);
        }
    }

    // Generate a source java file as String
    public static String generate_X2() {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println("import compiler.lib.ir_framework.*;");
        out.println("");
        out.println("public class X2 {");
        out.println("    public static void main(String args[]) {");
        out.println("        TestFramework.run(X2.class);");
        out.println("    }");
        out.println("");
        out.println("    @Test");
        out.println("    @IR(counts = {IRNode.LOAD, \"> 0\"})"); // Conflicting IR rules
        out.println("    @IR(failOn = IRNode.LOAD)");            //  -> one must fail.
        out.println("    static void test() {");
        out.println("    }");
        out.println("}");
        out.close();
        return writer.toString();
    }

    static void test_X2() {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        String src = generate_X2();
        SourceFile file = new SourceFile("X2", src);
        comp.add(file);

        // Compile the source file.
        comp.compile();

        // Load the compiled class.
        Class c = comp.getClass("X2");

        // Invoke the "X2.main" method from the compiled and loaded class.
        try {
            c.getDeclaredMethod("main", new Class[] { String[].class }).invoke(null, new Object[] { null });
            throw new RuntimeException("IRViolationException expected.");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("No such method:", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Illegal access:", e);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t == null) {
                throw new RuntimeException("IRViolationException expected:", e);
            }
            if (!t.getClass().getSimpleName().equals("IRViolationException")) {
                throw new RuntimeException("IRViolationException expected:", e);
            }
            System.out.println("Success, we got a IRViolationException.");
        }
    }
}
