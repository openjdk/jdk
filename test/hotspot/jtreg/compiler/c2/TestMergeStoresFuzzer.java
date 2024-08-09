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
 * @summary Targetted Fuzzer for MergeStores address parsing.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../lib/ir_framework/TestFramework.java
 * @run driver compile_framework.examples.TestMergeStoresFuzzer
 */

package compile_framework.examples;

import compiler.lib.compile_framework.*;
import jdk.test.lib.Utils;
import java.lang.reflect.InvocationTargetException;

public class TestMergeStoresFuzzer {

    public static void main(String args[]) {
        CompileFramework comp = new CompileFramework();
        comp.add(SourceCode.newJavaSourceCode("FuzzMe", generate(comp)));
        comp.compile();
        comp.invoke("FuzzMe", "main", new Object[] {null});
    }

    public static String generate(CompileFramework comp) {
        return String.format("""
               import compiler.lib.ir_framework.*;

               public class FuzzMe {
                   public static void main(String args[]) {
                       TestFramework framework = new TestFramework(FuzzMe.class);
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
               """, comp.getClassPathOfCompiledClasses());
    }
}
