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
 */
package org.graalvm.compiler.jtt.backend;

import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SUPER;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.IFNE;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.LADD;
import static jdk.internal.org.objectweb.asm.Opcodes.LCMP;
import static jdk.internal.org.objectweb.asm.Opcodes.LCONST_0;
import static jdk.internal.org.objectweb.asm.Opcodes.LLOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.LRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;

import org.junit.BeforeClass;
import org.junit.Test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;
import org.graalvm.compiler.test.ExportingClassLoader;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;

public class LargeConstantSectionTest extends JTTTest {
    private static final String NAME = "LargeConstantSection";
    private static final long LARGE_CONSTANT = 0xF0F0F0F0F0L;
    private static LargeConstantClassLoader LOADER;

    @BeforeClass
    public static void before() {
        LOADER = new LargeConstantClassLoader(LargeConstantSectionTest.class.getClassLoader());
    }

    public abstract static class LargeConstantAbstract {
        public abstract long run(long i);
    }

    public static long test(LargeConstantAbstract a, long i) throws Exception {
        return a.run(GraalDirectives.opaque(i));
    }

    public static class LargeConstantClassLoader extends ExportingClassLoader {
        public LargeConstantClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(NAME)) {
                String graalDirectivesClassName = GraalDirectives.class.getName().replace('.', '/');
                int numberIfBlocks = 1100; // Each if block contains three constants
                ClassWriter cw = new ClassWriter(0);
                MethodVisitor mv;
                String abstractClassName = Type.getInternalName(LargeConstantAbstract.class);
                cw.visit(52, ACC_PUBLIC + ACC_SUPER, NAME, null, abstractClassName, null);

                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                Label l0 = new Label();
                mv.visitLabel(l0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, abstractClassName, "<init>", "()V", false);
                mv.visitInsn(RETURN);
                Label l1 = new Label();
                mv.visitLabel(l1);
                mv.visitMaxs(1, 1);
                mv.visitEnd();

                mv = cw.visitMethod(ACC_PUBLIC, "run", "(J)J", null, null);
                mv.visitCode();
                Label nextIf = new Label();
                for (int i = 0; i < numberIfBlocks; i++) {
                    mv.visitLabel(nextIf);
                    mv.visitFrame(Opcodes.F_NEW, 2, new Object[]{abstractClassName, Opcodes.LONG}, 0, new Object[]{});
                    mv.visitVarInsn(LLOAD, 1);
                    mv.visitLdcInsn(new Long(LARGE_CONSTANT + i));
                    mv.visitInsn(LCMP);
                    nextIf = new Label();
                    mv.visitJumpInsn(IFNE, nextIf);
                    mv.visitLdcInsn(new Long(LARGE_CONSTANT + i + numberIfBlocks));
                    mv.visitMethodInsn(INVOKESTATIC, graalDirectivesClassName, "opaque", "(J)J", false);
                    mv.visitLdcInsn(new Long(LARGE_CONSTANT + i + numberIfBlocks * 2));
                    mv.visitMethodInsn(INVOKESTATIC, graalDirectivesClassName, "opaque", "(J)J", false);
                    mv.visitInsn(LADD);
                    mv.visitInsn(LRETURN);
                }
                mv.visitLabel(nextIf);
                mv.visitFrame(Opcodes.F_NEW, 2, new Object[]{abstractClassName, Opcodes.LONG}, 0, new Object[]{});
                mv.visitInsn(LCONST_0);
                mv.visitInsn(LRETURN);
                Label l9 = new Label();
                mv.visitLabel(l9);
                mv.visitMaxs(4, 6);
                mv.visitEnd();

                cw.visitEnd();

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
        try (OverrideScope os = OptionValue.override(GraalOptions.InlineEverything, true)) {
            runTest("test", LOADER.findClass(NAME).newInstance(), 0L);
        }
    }
}
