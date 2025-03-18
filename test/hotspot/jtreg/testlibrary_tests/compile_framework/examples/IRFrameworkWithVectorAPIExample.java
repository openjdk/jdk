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
 * @summary Example test to use the Compile Framework together with the IR Framework (i.e. TestFramework),
 *          and the VectorAPI.
 * @modules java.base/jdk.internal.misc
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @compile ../../../compiler/lib/ir_framework/TestFramework.java
 * @run driver compile_framework.examples.IRFrameworkWithVectorAPIExample
 */

package compile_framework.examples;

import compiler.lib.compile_framework.*;
import jdk.test.lib.Utils;
import jdk.incubator.vector.IntVector;
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
public class IRFrameworkWithVectorAPIExample {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("InnerTest", generateInnerTest(comp));

        // Compile the source file. "javac" needs to know that it is ok to compile with the
        // VectorAPI module.
        comp.compile("--add-modules=jdk.incubator.vector");

        // InnerTest.main();
        comp.invoke("InnerTest", "main", new Object[] {null});
    }

    // Generate a source java file as String
    public static String generateInnerTest(CompileFramework comp) {
        return String.format("""
               import compiler.lib.ir_framework.*;
               import jdk.incubator.vector.*;

               public class InnerTest {
                   public static void main(String args[]) {
                       TestFramework framework = new TestFramework(InnerTest.class);
                       // Also the TestFramework must allow the test VM to see the VectorAPI module.
                       framework.addFlags("-classpath", "%s", "--add-modules=jdk.incubator.vector");
                       framework.start();
                   }

                   @Test
                   static Object test() {
                       return IntVector.broadcast(IntVector.SPECIES_64, 42);
                   }
               }
               """, comp.getEscapedClassPathOfCompiledClasses());
    }
}
