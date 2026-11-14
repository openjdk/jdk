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

/*
 * @test
 * @bug 8397251
 * @summary Test that 
 * @requires vm.flagless & vm.compiler1.enabled
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver ${test.main.class} generated
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:-ProfileInterpreter
 *                   -XX:CompileCommand=compileonly,${test.main.class}::testLoop,1100
 *                   ${test.main.class} loop
 */

package compiler.c1;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestSameBranchTargetProfiling {
    static final ClassDesc CD_GEN =
        ClassDesc.of("compiler.c1.TestSameBranchTargetProfilingGenerated");

    public static void main(String[] args) throws Exception {
        if (args[0].equals("generated")) {

            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintMethodData", "-XX:-ProfileInterpreter",
                "-XX:CompileCommand=compileonly,compiler.c1.TestSameBranchTargetProfilingGenerated::test",
                Launcher.class.getName());

            OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
            analyzer.shouldHaveExitValue(0);
            analyzer.shouldContain("Generated::test(Z)V"); // We have the expected method in the method data dump
            analyzer.shouldContain("BranchData"); // t
            analyzer.shouldMatch("not taken\\(\\d+\\)"); // All branches should be not taken.
            analyzer.shouldNotContain("not taken(0)");
            analyzer.shouldContain("taken(0)"); // No branch should be taken.
        } else if (args[0].equals("loop")) {
            final WhiteBox wb = WhiteBox.getWhiteBox();
            final Method testMethod = TestSameBranchTargetProfiling.class.getDeclaredMethod("testLoop", int[].class, boolean.class);

            int[] arr = new int[128];
            for (int i = 0; i < 20_000; i++) {
                testLoop(arr, true);
            }
            // Checking the PrintMethodData output is tedious and brittle since
            // there are multiple branches. Instead, we check that the method
            // never deoptimized, which would definitely happen if the branch
            // profiling data was swapped.
            if (!wb.isMethodCompiled(testMethod)) {
                throw new RuntimeException("test method should be compiled by now");
            }
            if (wb.getMethodDecompileCount(testMethod) > 0) {
                throw new RuntimeException("test method testLoop should not deoptimize with correct branch profile");
            }
        } else {
            throw new IllegalArgumentException("unrecognized subtest " + args[0]);
        }
    }

    // Must always be called with flag == true!
    static void testLoop(int[] arr, boolean flag) {
        boolean b = flag;
        for (int i = 0; i < arr.length; i++) {
            // javac compiles this to 
            //  n  : ifeq (n+1)
            //  n+1: iinc      // i++
            //  n+2: goto #loopHead
            // This means that both the taken and not taken branch jump to the
            // same block.
            if (b) {
                continue;
            }
        }
    }

    static class Launcher {
        public static void main(String[] args) throws Throwable {
            byte[] classBytes = buildGeneratedTest();
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> klass = lookup.defineClass(classBytes);
            MethodHandle handle = lookup.findStatic(klass, "test",
                MethodType.methodType(void.class, boolean.class));

            for (int i = 0; i < 20_000; i++) {
                handle.invokeExact(true);
            }
        }
    }

    static byte[] buildGeneratedTest() {
        return ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
            .build(CD_GEN, cb -> {
                cb.withVersion(50, 0);
                cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);

                cb.withMethod("test", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_boolean),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                    mb -> mb.withCode(code -> {
                        Label target = code.newLabel();

                        // Generate a branch where the target and fall-through bci
                        // are the same.
                        code.iload(0)
                            .ifeq(target)
                            .labelBinding(target)
                            .return_();
                    }));
            });
    }

}
