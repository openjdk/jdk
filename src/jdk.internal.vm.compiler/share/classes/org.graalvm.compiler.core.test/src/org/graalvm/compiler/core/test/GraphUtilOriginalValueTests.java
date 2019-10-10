/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import org.graalvm.compiler.nodes.util.GraphUtil;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Unit tests derived from https://github.com/oracle/graal/pull/1690.
 */
public class GraphUtilOriginalValueTests extends CustomizedBytecodePatternTest {

    static class LinkedNode {
        LinkedNode next;
    }

    static class A extends LinkedNode {
    }

    static class B extends LinkedNode {
    }

    static class C extends LinkedNode {
    }

    public static Class<?> getLastClass(A a) {
        LinkedNode current = a;
        Class<?> currentKlass = null;
        while (current != null) {
            // This must not be folded to A.class
            currentKlass = current.getClass();

            current = current.next;
        }
        return currentKlass;
    }

    @Test
    public void testGetClass() {
        A a = new A();
        a.next = new B();

        test("getLastClass", a);
    }

    static final ConstantCallSite cs1 = init(A.class);
    static final ConstantCallSite cs2 = init(B.class);
    static final ConstantCallSite cs3 = init(C.class);

    static ConstantCallSite init(Class<?> c) {
        try {
            return new ConstantCallSite(MethodHandles.lookup().unreflectConstructor(c.getDeclaredConstructor()));
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    public static boolean findTarget(MethodHandle key) {
        ConstantCallSite cs = cs1;
        while (cs != null) {
            if (cs.getTarget() == key) {
                return true;
            }
            if (cs == cs1) {
                cs = cs2;
            } else if (cs == cs2) {
                cs = cs3;
            } else {
                cs = null;
            }
        }
        return false;
    }

    @Test
    public void testGetTarget() {
        cs1.getTarget();
        cs2.getTarget();
        test("findTarget", cs3.getTarget());
    }

    @Override
    protected byte[] generateClass(String internalClassName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(52, ACC_SUPER | ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

        String getDescriptor = "(Ljava/lang/Object;)V";
        MethodVisitor m = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "unbalancedMonitors", getDescriptor, null, null);
        Label loopHead = new Label();
        Label end = new Label();
        m.visitCode();

        // @formatter:off
        /*
         * void unbalancedMonitors(Object o) {
         *     monitorenter(o);
         *     while (o.toString() != o) {
         *         monitorexit(o);
         *         o = o.toString();
         *     }
         * }
         */
        // @formatter:on

        m.visitVarInsn(ALOAD, 0);
        m.visitInsn(MONITORENTER);
        m.visitLabel(loopHead);
        m.visitVarInsn(ALOAD, 0);
        m.visitInsn(MONITOREXIT);
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
        m.visitVarInsn(ALOAD, 0);
        m.visitJumpInsn(IF_ACMPEQ, end);
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
        m.visitVarInsn(ASTORE, 0);
        m.visitJumpInsn(GOTO, loopHead);
        m.visitLabel(end);
        m.visitInsn(RETURN);
        m.visitMaxs(2, 2);
        m.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Tests that the use of {@link GraphUtil#originalValue} in parsing MONITOREXIT correctly
     * detects unbalanced monitors.
     */
    @Test
    public void testUnbalancedMonitors() throws ClassNotFoundException {
        Class<?> testClass = getClass("UnbalancedMonitors");
        ResolvedJavaMethod t1 = getResolvedJavaMethod(testClass, "unbalancedMonitors");
        try {
            parseForCompile(t1);
            Assert.fail("expected a " + BailoutException.class.getName());
        } catch (BailoutException e) {
            String msg = e.getMessage();
            Assert.assertTrue(msg, msg.contains("unbalanced monitors"));
        }
    }
}
