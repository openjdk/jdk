/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.except;

import org.junit.BeforeClass;
import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.test.ExportingClassLoader;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;

public class UntrustedInterfaces extends JTTTest {

    public interface CallBack {
        int callBack(TestInterface ti);
    }

    private interface TestInterface {
        int method();
    }

    /**
     * What a GoodPill would look like.
     *
     * <pre>
     * private static final class GoodPill extends Pill {
     *     public void setField() {
     *         field = new TestConstant();
     *     }
     *
     *     public void setStaticField() {
     *         staticField = new TestConstant();
     *     }
     *
     *     public int callMe(CallBack callback) {
     *         return callback.callBack(new TestConstant());
     *     }
     *
     *     public TestInterface get() {
     *         return new TestConstant();
     *     }
     * }
     *
     * private static final class TestConstant implements TestInterface {
     *     public int method() {
     *         return 42;
     *     }
     * }
     * </pre>
     */
    public abstract static class Pill {
        public static TestInterface staticField;
        public TestInterface field;

        public abstract void setField();

        public abstract void setStaticField();

        public abstract int callMe(CallBack callback);

        public abstract TestInterface get();
    }

    public int callBack(TestInterface list) {
        return list.method();
    }

    public int staticFieldInvoke(Pill pill) {
        pill.setStaticField();
        return Pill.staticField.method();
    }

    public int fieldInvoke(Pill pill) {
        pill.setField();
        return pill.field.method();
    }

    public int argumentInvoke(Pill pill) {
        return pill.callMe(ti -> ti.method());
    }

    public int returnInvoke(Pill pill) {
        return pill.get().method();
    }

    @SuppressWarnings("cast")
    public boolean staticFieldInstanceof(Pill pill) {
        pill.setStaticField();
        return Pill.staticField instanceof TestInterface;
    }

    @SuppressWarnings("cast")
    public boolean fieldInstanceof(Pill pill) {
        pill.setField();
        return pill.field instanceof TestInterface;
    }

    @SuppressWarnings("cast")
    public int argumentInstanceof(Pill pill) {
        return pill.callMe(ti -> ti instanceof TestInterface ? 42 : 24);
    }

    @SuppressWarnings("cast")
    public boolean returnInstanceof(Pill pill) {
        return pill.get() instanceof TestInterface;
    }

    public TestInterface staticFieldCheckcast(Pill pill) {
        pill.setStaticField();
        return TestInterface.class.cast(Pill.staticField);
    }

    public TestInterface fieldCheckcast(Pill pill) {
        pill.setField();
        return TestInterface.class.cast(pill.field);
    }

    public int argumentCheckcast(Pill pill) {
        return pill.callMe(ti -> TestInterface.class.cast(ti).method());
    }

    public TestInterface returnCheckcast(Pill pill) {
        return TestInterface.class.cast(pill.get());
    }

    private static Pill poisonPill;

    // Checkstyle: stop
    @BeforeClass
    public static void setUp() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        poisonPill = (Pill) new PoisonLoader().findClass(PoisonLoader.POISON_IMPL_NAME).newInstance();
    }

    // Checkstyle: resume

    @Test
    public void testStaticField0() {
        runTest("staticFieldInvoke", poisonPill);
    }

    @Test
    public void testStaticField1() {
        runTest("staticFieldInstanceof", poisonPill);
    }

    @Test
    public void testStaticField2() {
        runTest("staticFieldCheckcast", poisonPill);
    }

    @Test
    public void testField0() {
        runTest("fieldInvoke", poisonPill);
    }

    @Test
    public void testField1() {
        runTest("fieldInstanceof", poisonPill);
    }

    @Test
    public void testField2() {
        runTest("fieldCheckcast", poisonPill);
    }

    @Test
    public void testArgument0() {
        runTest("argumentInvoke", poisonPill);
    }

    @Test
    public void testArgument1() {
        runTest("argumentInstanceof", poisonPill);
    }

    @Test
    public void testArgument2() {
        runTest("argumentCheckcast", poisonPill);
    }

    @Test
    public void testReturn0() {
        runTest("returnInvoke", poisonPill);
    }

    @Test
    public void testReturn1() {
        runTest("returnInstanceof", poisonPill);
    }

    @Test
    public void testReturn2() {
        runTest("returnCheckcast", poisonPill);
    }

    private static class PoisonLoader extends ExportingClassLoader {
        public static final String POISON_IMPL_NAME = "org.graalvm.compiler.jtt.except.PoisonPill";

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(POISON_IMPL_NAME)) {
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

                cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, POISON_IMPL_NAME.replace('.', '/'), null, Type.getInternalName(Pill.class), null);
                // constructor
                MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                constructor.visitCode();
                constructor.visitVarInsn(Opcodes.ALOAD, 0);
                constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Pill.class), "<init>", "()V", false);
                constructor.visitInsn(Opcodes.RETURN);
                constructor.visitMaxs(0, 0);
                constructor.visitEnd();

                MethodVisitor setList = cw.visitMethod(Opcodes.ACC_PUBLIC, "setField", "()V", null, null);
                setList.visitCode();
                setList.visitVarInsn(Opcodes.ALOAD, 0);
                setList.visitTypeInsn(Opcodes.NEW, Type.getInternalName(Object.class));
                setList.visitInsn(Opcodes.DUP);
                setList.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
                setList.visitFieldInsn(Opcodes.PUTFIELD, Type.getInternalName(Pill.class), "field", Type.getDescriptor(TestInterface.class));
                setList.visitInsn(Opcodes.RETURN);
                setList.visitMaxs(0, 0);
                setList.visitEnd();

                MethodVisitor setStaticList = cw.visitMethod(Opcodes.ACC_PUBLIC, "setStaticField", "()V", null, null);
                setStaticList.visitCode();
                setStaticList.visitTypeInsn(Opcodes.NEW, Type.getInternalName(Object.class));
                setStaticList.visitInsn(Opcodes.DUP);
                setStaticList.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
                setStaticList.visitFieldInsn(Opcodes.PUTSTATIC, Type.getInternalName(Pill.class), "staticField", Type.getDescriptor(TestInterface.class));
                setStaticList.visitInsn(Opcodes.RETURN);
                setStaticList.visitMaxs(0, 0);
                setStaticList.visitEnd();

                MethodVisitor callMe = cw.visitMethod(Opcodes.ACC_PUBLIC, "callMe", Type.getMethodDescriptor(Type.INT_TYPE, Type.getType(CallBack.class)), null, null);
                callMe.visitCode();
                callMe.visitVarInsn(Opcodes.ALOAD, 1);
                callMe.visitTypeInsn(Opcodes.NEW, Type.getInternalName(Object.class));
                callMe.visitInsn(Opcodes.DUP);
                callMe.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
                callMe.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(CallBack.class), "callBack", Type.getMethodDescriptor(Type.INT_TYPE, Type.getType(TestInterface.class)), true);
                callMe.visitInsn(Opcodes.IRETURN);
                callMe.visitMaxs(0, 0);
                callMe.visitEnd();

                MethodVisitor getList = cw.visitMethod(Opcodes.ACC_PUBLIC, "get", Type.getMethodDescriptor(Type.getType(TestInterface.class)), null, null);
                getList.visitCode();
                getList.visitTypeInsn(Opcodes.NEW, Type.getInternalName(Object.class));
                getList.visitInsn(Opcodes.DUP);
                getList.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
                getList.visitInsn(Opcodes.ARETURN);
                getList.visitMaxs(0, 0);
                getList.visitEnd();

                cw.visitEnd();

                byte[] bytes = cw.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }
}
