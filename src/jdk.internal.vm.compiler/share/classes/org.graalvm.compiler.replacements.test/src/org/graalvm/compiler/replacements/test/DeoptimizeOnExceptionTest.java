/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import java.util.Random;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.phases.HighTier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.AbstractInliningPhase;
import org.graalvm.compiler.test.ExportingClassLoader;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that deoptimization upon exception handling works.
 */
public class DeoptimizeOnExceptionTest extends GraalCompilerTest {

    public DeoptimizeOnExceptionTest() {
        createSuites(getInitialOptions()).getHighTier().findPhase(AbstractInliningPhase.class).remove();
    }

    private static void raiseException(String m1, String m2, String m3, String m4, String m5) {
        throw new RuntimeException(m1 + m2 + m3 + m4 + m5);
    }

    @Test
    public void test1() {
        test("test1Snippet", "m1", "m2", "m3", "m4", "m5");
    }

    // no local exception handler - will deopt
    public static String test1Snippet(String m1, String m2, String m3, String m4, String m5) {
        if (m1 != null) {
            raiseException(m1, m2, m3, m4, m5);
        }
        return m1 + m2 + m3 + m4 + m5;
    }

    @Test
    public void test2() {
        test("test2Snippet");
    }

    public String test2Snippet() throws Exception {
        try {
            ClassLoader testCl = new MyClassLoader();
            @SuppressWarnings("unchecked")
            Class<Runnable> c = (Class<Runnable>) testCl.loadClass(name);
            Runnable r = c.newInstance();
            ct = Long.MAX_VALUE;
            // warmup
            for (int i = 0; i < 100; i++) {
                r.run();
            }
            // compile
            ResolvedJavaMethod method = getResolvedJavaMethod(c, "run");
            getCode(method);
            ct = 0;
            r.run();
        } catch (Throwable e) {
            e.printStackTrace(System.out);
            Assert.fail();
        }
        return "SUCCESS";
    }

    @Test
    public void test3() {
        Assume.assumeTrue("Only works on jdk8 right now", Java8OrEarlier);
        ResolvedJavaMethod method = getResolvedJavaMethod("test3Snippet");

        for (int i = 0; i < 2; i++) {
            Result actual;
            boolean expectedCompiledCode = (method.getProfilingInfo().getDeoptimizationCount(DeoptimizationReason.NotCompiledExceptionHandler) != 0);
            InstalledCode code = getCode(method, null, false, true, new OptionValues(getInitialOptions(), HighTier.Options.Inline, false));
            assertTrue(code.isValid());

            try {
                actual = new Result(code.executeVarargs(false), null);
            } catch (Exception e) {
                actual = new Result(null, e);
            }

            assertTrue(i > 0 == expectedCompiledCode, "expect compiled code to stay around after the first iteration");
            assertEquals(new Result(expectedCompiledCode, null), actual);
            assertTrue(expectedCompiledCode == code.isValid());
        }
    }

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (method.getName().equals("throwException")) {
            if (b.getMethod().getProfilingInfo().getDeoptimizationCount(DeoptimizationReason.NotCompiledExceptionHandler) != 0) {
                return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            } else {
                return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }
        }
        return super.bytecodeParserShouldInlineInvoke(b, method, args);
    }

    private static void throwException() throws Exception {
        throw new Exception();
    }

    static int footprint;

    public static boolean test3Snippet(boolean rethrowException) throws Exception {
        try {
            footprint = 1;
            throwException();
        } catch (Exception e) {
            footprint = 2;
            if (rethrowException) {
                throw e;
            }
        }

        return GraalDirectives.inCompiledCode();
    }

    public static class MyClassLoader extends ExportingClassLoader {
        @Override
        protected Class<?> findClass(String className) throws ClassNotFoundException {
            return defineClass(name.replace('/', '.'), clazz, 0, clazz.length);
        }
    }

    public static void methodB() {
        Random r = new Random(System.currentTimeMillis());
        while (r.nextFloat() > .03f) {
            // Empty
        }

        return;
    }

    public static void methodA() {
        Random r = new Random(System.currentTimeMillis());
        while (r.nextDouble() > .05) {
            // Empty
        }
        return;
    }

    private static Object m = new Object();
    static long ct = Long.MAX_VALUE;

    public static Object getM() {
        if (ct-- > 0) {
            return m;
        } else {
            return null;
        }
    }

    private static String name = "t/TestJSR";

    private static final byte[] clazz = makeClazz();

    private static byte[] makeClazz() {
        // Code generated the class below using asm.
        String clazzName = DeoptimizeOnExceptionTest.class.getName().replace('.', '/');
        final ClassWriter w = new ClassWriter(0);
        w.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC,
                        "t/TestJSR", null, "java/lang/Object",
                        new String[]{"java/lang/Runnable"});
        MethodVisitor mv = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, new String[]{});
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(10, 10);
        mv.visitEnd();

        mv = w.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, clazzName, "getM", "()Ljava/lang/Object;", false);
        Label l1 = new Label();
        mv.visitJumpInsn(Opcodes.JSR, l1);
        mv.visitInsn(Opcodes.RETURN);

        mv.visitLabel(l1);
        mv.visitVarInsn(Opcodes.ASTORE, 1);

        Label lElse = new Label();
        Label lEnd = new Label();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        mv.visitInsn(Opcodes.POP2);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, clazzName, "getM", "()Ljava/lang/Object;", false);
        mv.visitInsn(Opcodes.DUP);
        mv.visitJumpInsn(Opcodes.IFNULL, lElse);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, clazzName, "methodA", "()V", false);
        mv.visitJumpInsn(Opcodes.GOTO, lEnd);
        mv.visitLabel(lElse);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, clazzName, "methodB", "()V", false);
        mv.visitLabel(lEnd);

        mv.visitVarInsn(Opcodes.RET, 1);
        mv.visitMaxs(10, 10);
        mv.visitEnd();
        return w.toByteArray();
    }
}
