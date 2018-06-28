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



package org.graalvm.compiler.hotspot.test;

import static org.graalvm.compiler.test.JLModule.uncheckedAddExports;

import java.lang.reflect.Method;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.test.JLModule;
import org.junit.BeforeClass;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ConstantPoolSubstitutionsTests extends GraalCompilerTest {

    public ConstantPoolSubstitutionsTests() {
        exportPackage(JAVA_BASE, "jdk.internal.org.objectweb.asm");
    }

    @SuppressWarnings("try")
    protected StructuredGraph test(final String snippet) {
        ResolvedJavaMethod method = getMetaAccess().lookupJavaMethod(getMethod(snippet));
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope s = debug.scope("ConstantPoolSubstitutionsTests", method)) {
            StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
            compile(graph.method(), graph);
            assertNotInGraph(graph, Invoke.class);
            debug.dump(DebugContext.BASIC_LEVEL, graph, snippet);
            return graph;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected static StructuredGraph assertNotInGraph(StructuredGraph graph, Class<?> clazz) {
        for (Node node : graph.getNodes()) {
            if (clazz.isInstance(node)) {
                fail(node.toString());
            }
        }
        return graph;
    }

    private static Object getConstantPoolForObject() {
        String miscPackage = Java8OrEarlier ? "sun.misc" : "jdk.internal.misc";
        try {
            Class<?> sharedSecretsClass = Class.forName(miscPackage + ".SharedSecrets");
            Class<?> javaLangAccessClass = Class.forName(miscPackage + ".JavaLangAccess");
            Object jla = sharedSecretsClass.getDeclaredMethod("getJavaLangAccess").invoke(null);
            return javaLangAccessClass.getDeclaredMethod("getConstantPool", Class.class).invoke(jla, Object.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Get the test methods from the generated class.
     */
    @Override
    protected Method getMethod(String methodName) {
        Class<?> cl;
        try {
            cl = LOADER.findClass(AsmLoader.NAME);
            addExports(cl);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
        return getMethod(cl, methodName);
    }

    @BeforeClass
    public static void beforeClass() {
        addExports(AsmLoader.class);
    }

    /**
     * This test uses some API hidden by the JDK9 module system.
     */
    private static void addExports(Class<?> c) {
        if (!Java8OrEarlier) {
            Object javaBaseModule = JLModule.fromClass(String.class);
            Object cModule = JLModule.fromClass(c);
            uncheckedAddExports(javaBaseModule, "jdk.internal.reflect", cModule);
            uncheckedAddExports(javaBaseModule, "jdk.internal.misc", cModule);
        }
    }

    @Test
    public void testGetSize() {
        Object cp = getConstantPoolForObject();
        test("getSize", cp);
    }

    @Test
    public void testGetIntAt() {
        test("getIntAt");
    }

    @Test
    public void testGetLongAt() {
        test("getLongAt");
    }

    @Test
    public void testGetFloatAt() {
        test("getFloatAt");
    }

    @Test
    public void testGetDoubleAt() {
        test("getDoubleAt");
    }

    private static final String PACKAGE_NAME = ConstantPoolSubstitutionsTests.class.getPackage().getName();
    private static final String PACKAGE_NAME_INTERNAL = PACKAGE_NAME.replace('.', '/');

    private static AsmLoader LOADER = new AsmLoader(ConstantPoolSubstitutionsTests.class.getClassLoader());

    public static class AsmLoader extends ClassLoader {
        Class<?> loaded;

        static final String NAME = PACKAGE_NAME + ".ConstantPoolTest";

        public AsmLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(NAME)) {
                if (loaded != null) {
                    return loaded;
                }
                byte[] bytes = Gen.generateClass();
                return (loaded = defineClass(name, bytes, 0, bytes.length));
            } else {
                return super.findClass(name);
            }
        }
    }

    static class Gen implements Opcodes {
        // @formatter:off
        /*
        static class ConstantPoolTest {
            public static int getSize(Object o) {
                ConstantPool cp = (ConstantPool) o;
                return cp.getSize();
            }

            public static int getIntAt(Object o) {
                ConstantPool cp = (ConstantPool) o;
                return cp.getIntAt(0);
            }

            public static long getLongAt(Object o) {
                ConstantPool cp = (ConstantPool) o;
                return cp.getLongAt(0);
            }

            public static float getFloatAt(Object o) {
                ConstantPool cp = (ConstantPool) o;
                return cp.getFloatAt(0);
            }

            public static double getDoubleAt(Object o) {
                ConstantPool cp = (ConstantPool) o;
                return cp.getDoubleAt(0);
            }

            public static String getUTF8At(Object o) {
                ConstantPool cp = (ConstantPool) o;
                return cp.getUTF8At(0);
            }
        }
        */
        // @formatter:on

        static byte[] generateClass() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(52, ACC_SUPER, PACKAGE_NAME_INTERNAL + "/ConstantPoolTest", null, "java/lang/Object", null);
            cw.visitInnerClass(PACKAGE_NAME_INTERNAL + "/ConstantPoolTest", PACKAGE_NAME_INTERNAL + "/ConstantPoolSubstitutionsTests", "ConstantPoolTest",
                            ACC_STATIC);
            String constantPool = Java8OrEarlier ? "sun/reflect/ConstantPool" : "jdk/internal/reflect/ConstantPool";

            mv = cw.visitMethod(0, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();

            mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "getSize", "(Ljava/lang/Object;)I", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitTypeInsn(CHECKCAST, constantPool);
            mv.visitVarInsn(ASTORE, 1);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, constantPool, "getSize", "()I", false);
            mv.visitInsn(IRETURN);
            mv.visitMaxs(1, 3);
            mv.visitEnd();

            mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "getIntAt", "(Ljava/lang/Object;)I", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitTypeInsn(CHECKCAST, constantPool);
            mv.visitVarInsn(ASTORE, 1);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitInsn(ICONST_0);
            mv.visitMethodInsn(INVOKEVIRTUAL, constantPool, "getIntAt", "(I)I", false);
            mv.visitInsn(IRETURN);
            mv.visitMaxs(2, 3);
            mv.visitEnd();

            mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "getLongAt", "(Ljava/lang/Object;)J", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitTypeInsn(CHECKCAST, constantPool);
            mv.visitVarInsn(ASTORE, 1);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitInsn(ICONST_0);
            mv.visitMethodInsn(INVOKEVIRTUAL, constantPool, "getLongAt", "(I)J", false);
            mv.visitInsn(LRETURN);
            mv.visitMaxs(2, 3);
            mv.visitEnd();

            mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "getFloatAt", "(Ljava/lang/Object;)F", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitTypeInsn(CHECKCAST, constantPool);
            mv.visitVarInsn(ASTORE, 1);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitInsn(ICONST_0);
            mv.visitMethodInsn(INVOKEVIRTUAL, constantPool, "getFloatAt", "(I)F", false);
            mv.visitInsn(FRETURN);
            mv.visitMaxs(2, 3);
            mv.visitEnd();

            mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "getDoubleAt", "(Ljava/lang/Object;)D", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitTypeInsn(CHECKCAST, constantPool);
            mv.visitVarInsn(ASTORE, 1);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitInsn(ICONST_0);
            mv.visitMethodInsn(INVOKEVIRTUAL, constantPool, "getDoubleAt", "(I)D", false);
            mv.visitInsn(DRETURN);
            mv.visitMaxs(2, 3);
            mv.visitEnd();

            mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "getUTF8At", "(Ljava/lang/Object;)Ljava/lang/String;", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitTypeInsn(CHECKCAST, constantPool);
            mv.visitVarInsn(ASTORE, 1);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitInsn(ICONST_0);
            mv.visitMethodInsn(INVOKEVIRTUAL, constantPool, "getUTF8At", "(I)Ljava/lang/String;", false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(2, 3);
            mv.visitEnd();
            cw.visitEnd();

            return cw.toByteArray();
        }
    }
}
