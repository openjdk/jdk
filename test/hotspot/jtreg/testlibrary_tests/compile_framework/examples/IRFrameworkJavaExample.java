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
 * @summary Example test to use the Compile Framework together with the IR Framework (i.e. TestFramework).
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/ir_framework/TestFramework.java
 * @run driver compile_framework.examples.IRFrameworkJavaExample
 */

package compile_framework.examples;

import compiler.lib.compile_framework.*;
import jdk.test.lib.Utils;
import jdk.test.lib.Platform;
import java.lang.reflect.InvocationTargetException;

/**
 * This test shows that the IR verification can be done on code compiled by the Compile Framework.
 * The "@compile" command for JTREG is required so that the IRFramework is compiled, other javac
 * might not compile it because it is not present in the class, only in the dynamically compiled
 * code.
 * <p>
 * Additionally, we must set the classpath for the Test-VM, so that it has access to all compiled
 * classes (see {@link CompileFramework#getEscapedClassPathOfCompiledClasses}).
 */
public class IRFrameworkJavaExample {

    public static void main(String[] args) {
        testX1();
        testX2();
    }

    // Generate a source java file as String
    public static String generateX1(CompileFramework comp) {
        return String.format("""
               import compiler.lib.ir_framework.*;

               public class X1 {
                   public static void main(String args[]) {
                       TestFramework framework = new TestFramework(X1.class);
                       framework.addFlags("-classpath", "%s");
                       framework.start();
                   }

                   @Test
                   @IR(counts = {IRNode.LOAD_VECTOR_F, "> 0"},
                       applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
                   static float[] test() {
                       float[] a = new float[1024*8];
                       for (int i = 0; i < a.length; i++) {
                           a[i]++;
                       }
                       return a;
                   }
               }
               """, comp.getEscapedClassPathOfCompiledClasses());
    }

    static void testX1() {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("X1", generateX1(comp));

        // Compile the source file.
        comp.compile();

        // X1.main();
        comp.invoke("X1", "main", new Object[] {null});
    }

    // Generate a source java file as String
    public static String generateX2(CompileFramework comp) {
        // Example with conflicting "@IR" rules -> expect a IRViolationException.
        return String.format("""
               import compiler.lib.ir_framework.*;

               public class X2 {
                   public static void main(String args[]) {
                       TestFramework framework = new TestFramework(X2.class);
                       framework.addFlags("-classpath", "%s");
                       framework.start();
                   }

                   @Test
                   @IR(counts = {IRNode.LOAD, "> 0"})
                   @IR(failOn = IRNode.LOAD)
                   static void test() {
                   }
               }
               """, comp.getEscapedClassPathOfCompiledClasses());
    }

    static void testX2() {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("X2", generateX2(comp));

        // Compile the source file.
        comp.compile();

        // Load the compiled class.
        Class<?> c = comp.getClass("X2");

        // Invoke the "X2.main" method from the compiled and loaded class.
        try {
            c.getDeclaredMethod("main", new Class[] { String[].class }).invoke(null, new Object[] { null });

            // Check if IR framework is expected to execute the IR rules.
            if (Utils.getTestJavaOpts().length == 0 && Platform.isDebugBuild() && !Platform.isInt() && !Platform.isComp()) {
                throw new RuntimeException("IRViolationException expected.");
            } else {
                System.out.println("Got no IRViolationException, but was also not expected.");
            }
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
