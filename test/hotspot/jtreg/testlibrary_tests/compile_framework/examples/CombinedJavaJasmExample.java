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
 * @run driver compile_framework.examples.CombinedJavaJasmExample
 */

package compile_framework.examples;

import compiler.lib.compile_framework.*;

/**
 * This test shows a compilation of multiple Java and Jasm source code files.
 * In this example, the classes even reference each other.
 */
public class CombinedJavaJasmExample {

    // Generate a source jasm file as String
    public static String generateJasm() {
        return """
               package p/xyz;

               super public class XYZJasm {
                   public static Method test:"(I)I"
                   stack 20 locals 20
                   {
                       iload_0;
                       iconst_2;
                       imul;
                       invokestatic Method p/xyz/XYZJava."mul3":"(I)I";
                       ireturn;
                   }

                   public static Method mul5:"(I)I"
                   stack 20 locals 20
                   {
                       iload_0;
                       ldc 5;
                       imul;
                       ireturn;
                   }
               }
               """;
    }

    // Generate a source java file as String
    public static String generateJava() {
        return """
               package p.xyz;

               public class XYZJava {
                   public static int test(int i) {
                       return p.xyz.XYZJasm.mul5(i * 7);
                   }

                   public static int mul3(int i) {
                       return i * 3;
                   }
               }
               """;
    }

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Generate files.
        comp.addJasmSourceCode("p.xyz.XYZJasm", generateJasm());
        comp.addJavaSourceCode("p.xyz.XYZJava", generateJava());

        // Compile the source files.
        comp.compile();

        test(comp, "p.xyz.XYZJasm", "test", 11, 11 * 2 * 3);
        test(comp, "p.xyz.XYZJava", "test", 13, 13 * 7 * 5);

        System.out.println("Success.");
    }

    public static void test(CompileFramework comp, String className, String methodName, int input, int expected) {
        Object ret = comp.invoke(className, methodName, new Object[] {input});

        // Extract return value of invocation, verify its value.
        int i = (int)ret;
        System.out.println("Result of call: " + i + " vs expected: " + expected);
        if (i != expected) {
            throw new RuntimeException("wrong value: " + i);
        }
    }
}
