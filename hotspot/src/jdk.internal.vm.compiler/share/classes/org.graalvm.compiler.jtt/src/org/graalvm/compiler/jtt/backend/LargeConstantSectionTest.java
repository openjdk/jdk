/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.backend;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.LRETURN;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.test.ExportingClassLoader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import junit.framework.AssertionFailedError;

/**
 * This test let the compiler deal with a large amount of constant data in a method. This data is
 * stored typically in the constant section of the native method. Especially on the SPARC platform
 * the backend can address only 8k of memory with an immediate offset. Beyond this barrier, a
 * different addressing mode must be used.
 *
 * In order to do this this test generates a large method containing a large switch statement in
 * form of
 *
 * <code>
 *  static long run(long a) {
 *    switch(a) {
 *    case 1:
 *    return 0xF0F0F0F0F0L + 1;
 *    case 2:
 *    return 0xF0F0F0F0F0L + 2;
 *    ....
 *    default:
 *    return 0;
 *    }
 *
 *  }
 *  </code>
 *
 */
@RunWith(Parameterized.class)
public class LargeConstantSectionTest extends JTTTest {
    private static final String NAME = "LargeConstantSection";
    private static final long LARGE_CONSTANT = 0xF0F0F0F0F0L;
    private static LargeConstantClassLoader LOADER;
    @Parameter(value = 0) public int numberBlocks;

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> parameters = new ArrayList<>();
        for (int i = 4; i < 13; i += 2) {
            parameters.add(new Object[]{1 << i});
        }
        return parameters;
    }

    @Before
    public void before() {
        LOADER = new LargeConstantClassLoader(LargeConstantSectionTest.class.getClassLoader());
    }

    public class LargeConstantClassLoader extends ExportingClassLoader {
        public LargeConstantClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(NAME)) {
                ClassWriter cw = new ClassWriter(0);
                MethodVisitor mv;
                cw.visit(52, ACC_PUBLIC + ACC_SUPER, NAME, null, "java/lang/Object", null);

                mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, "run", "(I)J", null, null);
                mv.visitCode();
                Label begin = new Label();
                mv.visitLabel(begin);
                mv.visitVarInsn(ILOAD, 0);
                Label[] labels = new Label[numberBlocks];
                int[] keys = new int[numberBlocks];
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = new Label();
                    keys[i] = i;
                }
                Label defaultLabel = new Label();
                mv.visitLookupSwitchInsn(defaultLabel, keys, labels);
                for (int i = 0; i < labels.length; i++) {
                    mv.visitLabel(labels[i]);
                    mv.visitFrame(Opcodes.F_NEW, 1, new Object[]{Opcodes.INTEGER}, 0, new Object[]{});
                    mv.visitLdcInsn(new Long(LARGE_CONSTANT + i));
                    mv.visitInsn(LRETURN);
                }
                mv.visitLabel(defaultLabel);
                mv.visitFrame(Opcodes.F_NEW, 1, new Object[]{Opcodes.INTEGER}, 0, new Object[]{});
                mv.visitLdcInsn(new Long(3L));
                mv.visitInsn(LRETURN);
                Label end = new Label();
                mv.visitLabel(end);
                mv.visitLocalVariable("a", "I", null, begin, end, 0);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
                byte[] bytes = cw.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            } else {
                return super.findClass(name);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void run0() throws Exception {
        test("run", numberBlocks - 3);
    }

    @Override
    protected ResolvedJavaMethod getResolvedJavaMethod(String methodName) {
        try {
            for (Method method : LOADER.findClass(NAME).getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    return asResolvedJavaMethod(method);
                }
            }
        } catch (ClassNotFoundException e) {
            throw new AssertionFailedError("Cannot find class " + NAME);
        }
        throw GraalError.shouldNotReachHere();
    }
}
