/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 * @test
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @library /testlibrary /../../test/lib /
 * @compile ../common/CompilerToVMHelper.java
 * @run main ClassFileInstaller jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *      -Xbootclasspath/a:.
 *      compiler.jvmci.compilerToVM.GetLineNumberTableTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.CTVMUtilities;
import compiler.jvmci.common.testcases.TestCase;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethodImpl;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class GetLineNumberTableTest {
    public static void main(String[] args) {
        TestCase.getAllExecutables()
                .forEach(GetLineNumberTableTest::runSanityTest);
    }

    public static void runSanityTest(Executable aMethod) {
        HotSpotResolvedJavaMethodImpl method = CTVMUtilities
                .getResolvedMethod(aMethod);
        long[] lineNumbers = CompilerToVMHelper.getLineNumberTable(method);
        long[] expectedLineNumbers = getExpectedLineNumbers(aMethod);

        Asserts.assertTrue(Arrays.equals(lineNumbers, expectedLineNumbers),
                String.format("%s : unequal table values : %n%s%n%s%n",
                        aMethod,
                        Arrays.toString(lineNumbers),
                        Arrays.toString(expectedLineNumbers)));
    }

    public static long[] getExpectedLineNumbers(Executable aMethod) {
        try {
            ClassReader cr = new ClassReader(aMethod.getDeclaringClass()
                    .getName());
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.EXPAND_FRAMES);

            Map<Label, Integer> lineNumbers = new HashMap<>();
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitorForLabels(cw, lineNumbers,
                    aMethod);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);

            long[] result = null;
            if (!lineNumbers.isEmpty()) {
                Map<Integer, Integer> labels = new TreeMap<>();
                lineNumbers.forEach((k, v) -> labels.put(k.getOffset(), v));

                result = new long[2 * labels.size()];
                int i = 0;
                for (Integer key : labels.keySet()) {
                    result[i++] = key.longValue();
                    result[i++] = labels.get(key).longValue();
                }
            }
            // compilerToVM::getLineNumberTable returns null in case empty table
            return result;
        } catch (IOException e) {
            throw new Error("TEST BUG " + e, e);
        }
    }

    private static class ClassVisitorForLabels extends ClassVisitor {
        private final Map<Label, Integer> lineNumbers;
        private final String targetName;
        private final String targetDesc;

        public ClassVisitorForLabels(ClassWriter cw, Map<Label, Integer> lines,
                                     Executable target) {
            super(Opcodes.ASM5, cw);
            this.lineNumbers = lines;

            StringBuilder builder = new StringBuilder("(");
            for (Parameter parameter : target.getParameters()) {
                builder.append(Utils.toJVMTypeSignature(parameter.getType()));
            }
            builder.append(")");
            if (target instanceof Constructor) {
                targetName = "<init>";
                builder.append("V");
            } else {
                targetName = target.getName();
                builder.append(Utils.toJVMTypeSignature(
                        ((Method) target).getReturnType()));
            }
            targetDesc = builder.toString();
        }

        @Override
        public final MethodVisitor visitMethod(int access, String name,
                                               String desc, String signature,
                                               String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, desc, signature,
                    exceptions);
            if (targetDesc.equals(desc) && targetName.equals(name)) {
                return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override
                    public void visitLineNumber(int i, Label label) {
                        super.visitLineNumber(i, label);
                        lineNumbers.put(label, i);
                    }
                };
            }
            return  mv;
        }
    }
}
