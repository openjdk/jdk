/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @requires vm.jvmci
 * @library ../../../../../
 * @compile ../../../../../../../../../../../jdk/jdk/internal/vm/AnnotationEncodingDecoding/AnnotationTestInput.java
 *          ../../../../../../../../../../../jdk/jdk/internal/vm/AnnotationEncodingDecoding/MemberDeleted.java
 *          ../../../../../../../../../../../jdk/jdk/internal/vm/AnnotationEncodingDecoding/MemberTypeChanged.java
 *          TestResolvedJavaType.java
 * @clean jdk.internal.vm.test.AnnotationTestInput$Missing
 * @compile ../../../../../../../../../../../jdk/jdk/internal/vm/AnnotationEncodingDecoding/alt/MemberDeleted.java
 *          ../../../../../../../../../../../jdk/jdk/internal/vm/AnnotationEncodingDecoding/alt/MemberTypeChanged.java
 * @enablePreview
 * @modules jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *          jdk.internal.vm.ci/jdk.vm.ci.common
 *          jdk.internal.vm.ci/jdk.vm.ci.hotspot
 *          java.base/jdk.internal.reflect
 *          java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm
 *          java.base/sun.reflect.annotation
 * @run junit/othervm/timeout=240 -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:-UseJVMCICompiler jdk.vm.ci.runtime.test.TestResolvedJavaMethod
 */

package jdk.vm.ci.runtime.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import jdk.internal.vm.test.AnnotationTestInput;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.attribute.CodeAttribute;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.test.TestResolvedJavaMethod.AnnotationDataTest.Annotation1;
import jdk.vm.ci.runtime.test.TestResolvedJavaMethod.AnnotationDataTest.Annotation2;
import jdk.vm.ci.runtime.test.TestResolvedJavaMethod.AnnotationDataTest.Annotation3;
import jdk.vm.ci.runtime.test.TestResolvedJavaMethod.AnnotationDataTest.NumbersDE;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;

/**
 * Tests for {@link ResolvedJavaMethod}.
 */
public class TestResolvedJavaMethod extends MethodUniverse {

    public TestResolvedJavaMethod() {
    }

    /**
     * @see ResolvedJavaMethod#getCode()
     */
    @Test
    public void getCodeTest() {
        for (ResolvedJavaMethod m : joinValues(methods, constructors)) {
            String ms = m.toString();
            byte[] code = m.getCode();
            if (code == null) {
                assertEquals(ms, m.getCodeSize(), 0);
            } else {
                assertTrue(ms, code.length > 0);
            }
        }
    }

    /**
     * @see ResolvedJavaMethod#getCodeSize()
     */
    @Test
    public void getCodeSizeTest() {
        ResolvedJavaType unlinkedType = metaAccess.lookupJavaType(UnlinkedType.class);
        assertTrue(!unlinkedType.isLinked());
        for (ResolvedJavaMethod m : addExecutables(joinValues(methods, constructors), unlinkedType, false)) {
            int codeSize = m.getCodeSize();
            String ms = m.toString();
            if (m.isAbstract() || m.isNative()) {
                assertEquals(ms, codeSize, 0);
            } else if (!m.getDeclaringClass().isLinked()) {
                assertEquals(ms, -1, codeSize);
            } else {
                assertTrue(ms, codeSize > 0);
            }
        }
        assertTrue(!unlinkedType.isLinked());
    }

    @Test
    public void equalsTest() {
        List<ResolvedJavaMethod> executables = joinValues(methods, constructors);
        for (ResolvedJavaMethod m : executables) {
            for (ResolvedJavaMethod that : executables) {
                boolean expect = m == that;
                boolean actual = m.equals(that);
                assertEquals(expect, actual);
            }
        }
    }

    @Test
    public void getModifiersTest() {
        for (Map.Entry<Executable, ResolvedJavaMethod> e : join(methods, constructors).entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            int expected = e.getKey().getModifiers();
            int actual = m.getModifiers();
            assertEquals(String.format("%s: 0x%x != 0x%x", m, expected, actual), expected, actual);
        }
    }

    /**
     * @see ResolvedJavaMethod#isClassInitializer()
     */
    @Test
    public void isClassInitializerTest() {
        for (Map.Entry<Executable, ResolvedJavaMethod> e : join(methods, constructors).entrySet()) {
            // Class initializers are hidden from reflection
            ResolvedJavaMethod m = e.getValue();
            assertFalse(m.isClassInitializer());
        }
    }

    @Test
    public void isConstructorTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertFalse(m.isConstructor());
        }
        for (Map.Entry<Constructor<?>, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertTrue(m.isConstructor());
        }
    }

    @Test
    public void isSyntheticTest() {
        for (Map.Entry<Executable, ResolvedJavaMethod> e : join(methods, constructors).entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(e.getKey().isSynthetic(), m.isSynthetic());
        }
    }

    @Test
    public void isBridgeTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(e.getKey().isBridge(), m.isBridge());
        }
        for (Map.Entry<Constructor<?>, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(false, m.isBridge());
        }
    }

    @Test
    public void isVarArgsTest() {
        for (Map.Entry<Executable, ResolvedJavaMethod> e : join(methods, constructors).entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(e.getKey().isVarArgs(), m.isVarArgs());
        }
    }

    @Test
    public void isSynchronizedTest() {
        for (Map.Entry<Executable, ResolvedJavaMethod> e : join(methods, constructors).entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(Modifier.isSynchronized(e.getKey().getModifiers()), m.isSynchronized());
        }
    }

    @Test
    public void canBeStaticallyBoundTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(m.canBeStaticallyBound(), canBeStaticallyBound(e.getKey()));
        }
        for (Map.Entry<Constructor<?>, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            boolean expect = m.canBeStaticallyBound();
            boolean actual = canBeStaticallyBound(e.getKey());
            assertEquals(m.toString(), expect, actual);
        }
    }

    private static boolean canBeStaticallyBound(Member method) {
        int modifiers = method.getModifiers();
        return (Modifier.isFinal(modifiers) ||
                        Modifier.isPrivate(modifiers) ||
                        Modifier.isStatic(modifiers) ||
                        method instanceof Constructor ||
                        Modifier.isFinal(method.getDeclaringClass().getModifiers())) &&
                        !Modifier.isAbstract(modifiers);
    }

    private static String methodWithExceptionHandlers(String p1, Object o2) {
        try {
            return p1.substring(100) + o2.toString();
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Test
    public void getExceptionHandlersTest() throws NoSuchMethodException {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("methodWithExceptionHandlers", String.class, Object.class));
        ExceptionHandler[] handlers = method.getExceptionHandlers();
        assertNotNull(handlers);
        assertEquals(handlers.length, 3);
        handlers[0].getCatchType().equals(metaAccess.lookupJavaType(IndexOutOfBoundsException.class));
        handlers[1].getCatchType().equals(metaAccess.lookupJavaType(NullPointerException.class));
        handlers[2].getCatchType().equals(metaAccess.lookupJavaType(RuntimeException.class));
    }

    private static String nullPointerExceptionOnFirstLine(Object o, String ignored) {
        return o.toString() + ignored;
    }

    @Test
    public void asStackTraceElementTest() throws NoSuchMethodException {
        try {
            nullPointerExceptionOnFirstLine(null, "ignored");
            Assert.fail("should not reach here");
        } catch (NullPointerException e) {
            StackTraceElement expected = e.getStackTrace()[0];
            ResolvedJavaMethod method = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("nullPointerExceptionOnFirstLine", Object.class, String.class));
            StackTraceElement actual = method.asStackTraceElement(0);
            // JVMCI StackTraceElements omit the class loader and module info
            assertEquals(expected.toString(), actual.toString());
        }
    }

    @Test
    public void getConstantPoolTest() {
        for (Map.Entry<Executable, ResolvedJavaMethod> e : join(methods, constructors).entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            ConstantPool cp = m.getConstantPool();
            assertTrue(cp.length() > 0);
        }
    }

    @Test
    public void getParametersTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            java.lang.reflect.Parameter[] expected = e.getKey().getParameters();
            Parameter[] actual = e.getValue().getParameters();
            assertEquals(actual.length, expected.length);
            for (int i = 0; i < actual.length; i++) {
                java.lang.reflect.Parameter exp = expected[i];
                Parameter act = actual[i];
                assertEquals(exp.getName(), act.getName());
                assertEquals(exp.isNamePresent(), act.isNamePresent());
                assertEquals(exp.getModifiers(), act.getModifiers());
                assertArrayEquals(exp.getAnnotations(), act.getAnnotations());
                assertEquals(exp.getType().getName(), act.getType().toClassName());
                assertEquals(exp.getParameterizedType(), act.getParameterizedType());
                assertEquals(metaAccess.lookupJavaMethod(exp.getDeclaringExecutable()), act.getDeclaringMethod());
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface TestAnnotation {
        long value();
    }

    @Test
    @TestAnnotation(value = 1000L)
    public void getAnnotationTest() throws NoSuchMethodException {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("getAnnotationTest"));
        TestAnnotation annotation = method.getAnnotation(TestAnnotation.class);
        assertNotNull(annotation);
        assertEquals(1000L, annotation.value());
    }

    @Test
    @TestAnnotation(value = 1000L)
    public void getAnnotationsTest() throws NoSuchMethodException {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("getAnnotationsTest"));
        Annotation[] annotations = method.getAnnotations();
        assertNotNull(annotations);
        assertEquals(2, annotations.length);
        TestAnnotation annotation = null;
        for (Annotation a : annotations) {
            if (a instanceof TestAnnotation) {
                annotation = (TestAnnotation) a;
                break;
            }
        }
        assertNotNull(annotation);
        assertEquals(1000L, annotation.value());
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface NonNull {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Special {
    }

    private static native void methodWithAnnotatedParameters(@NonNull HashMap<String, String> p1, @Special @NonNull Class<? extends Annotation> p2);

    @Test
    public void getParameterAnnotationsTest() throws NoSuchMethodException {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("methodWithAnnotatedParameters", HashMap.class, Class.class));
        Annotation[][] annotations = method.getParameterAnnotations();
        assertEquals(2, annotations.length);
        assertEquals(1, annotations[0].length);
        assertEquals(NonNull.class, annotations[0][0].annotationType());
        assertEquals(2, annotations[1].length);
        assertEquals(Special.class, annotations[1][0].annotationType());
        assertEquals(NonNull.class, annotations[1][1].annotationType());
    }

    @Test
    public void getGenericParameterTypesTest() throws NoSuchMethodException {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("methodWithAnnotatedParameters", HashMap.class, Class.class));
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        assertEquals(2, genericParameterTypes.length);
        assertEquals("java.util.HashMap<java.lang.String, java.lang.String>", genericParameterTypes[0].toString());
        assertEquals("java.lang.Class<? extends java.lang.annotation.Annotation>", genericParameterTypes[1].toString());
    }

    @Test
    public void getMaxLocalsTest() throws NoSuchMethodException {
        ResolvedJavaMethod method1 = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("methodWithAnnotatedParameters", HashMap.class, Class.class));
        ResolvedJavaMethod method2 = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("nullPointerExceptionOnFirstLine", Object.class, String.class));
        assertEquals(0, method1.getMaxLocals());
        assertEquals(2, method2.getMaxLocals());

    }

    @Test
    public void getMaxStackSizeTest() throws NoSuchMethodException {
        ResolvedJavaMethod method1 = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("methodWithAnnotatedParameters", HashMap.class, Class.class));
        ResolvedJavaMethod method2 = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("nullPointerExceptionOnFirstLine", Object.class, String.class));
        assertEquals(0, method1.getMaxStackSize());
        // some versions of javac produce bytecode with a stacksize of 2 for this method
        // JSR 292 also sometimes need one more stack slot
        int method2StackSize = method2.getMaxStackSize();
        assertTrue(2 <= method2StackSize && method2StackSize <= 4);
    }

    @Test
    public void isDefaultTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(e.getKey().isDefault(), m.isDefault());
        }
        for (Map.Entry<Constructor<?>, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertFalse(m.isDefault());
        }
    }

    @Test
    public void hasReceiverTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertTrue(m.hasReceiver() != Modifier.isStatic(e.getKey().getModifiers()));
        }
        for (Map.Entry<Constructor<?>, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertTrue(m.hasReceiver());
        }
    }

    public static List<ResolvedJavaMethod> addExecutables(List<ResolvedJavaMethod> to, ResolvedJavaType declaringType, boolean forceLink) {
        to.addAll(List.of(declaringType.getDeclaredMethods(forceLink)));
        to.addAll(List.of(declaringType.getDeclaredConstructors(forceLink)));
        return to;
    }

    @Test
    public void hasBytecodesTest() {
        ResolvedJavaType unlinkedType = metaAccess.lookupJavaType(UnlinkedType.class);
        assertTrue(!unlinkedType.isLinked());
        for (ResolvedJavaMethod m : addExecutables(joinValues(methods, constructors), unlinkedType, false)) {
            boolean expect = m.getDeclaringClass().isLinked() && m.isConcrete() && !m.isNative();
            boolean actual = m.hasBytecodes();
            assertEquals(m.toString(), expect, actual);
        }
        assertTrue(!unlinkedType.isLinked());
    }

    @Test
    public void isJavaLangObjectInitTest() throws NoSuchMethodException {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(Object.class.getConstructor());
        assertTrue(method.isJavaLangObjectInit());
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertFalse(m.isJavaLangObjectInit());
        }
        for (Map.Entry<Constructor<?>, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            Constructor<?> key = e.getKey();
            if (key.getDeclaringClass() == Object.class && key.getParameters().length == 0) {
                assertTrue(m.isJavaLangObjectInit());
            } else {
                assertFalse(m.isJavaLangObjectInit());
            }
        }
    }

    @Test
    public void isScopedTest() throws NoSuchMethodException, ClassNotFoundException {
        // Must use reflection as ScopedMemoryAccess$Scoped is package-private
        Class<? extends Annotation> scopedAnnotationClass = Class.forName("jdk.internal.misc.ScopedMemoryAccess$Scoped").asSubclass(Annotation.class);
        boolean scopedMethodFound = false;
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            Method key = e.getKey();
            boolean expect = key.isAnnotationPresent(scopedAnnotationClass);
            boolean actual = m.isScoped();
            assertEquals(m.toString(), expect, actual);
            if (expect) {
                scopedMethodFound = true;
            }
        }
        assertTrue("At least one scoped method must be present", scopedMethodFound);
    }

    abstract static class UnlinkedType {
        abstract void abstractMethod();

        void concreteMethod() {
        }

        native void nativeMethod();
    }

    /**
     * All public non-final methods should be available in the vtable.
     */
    @Test
    public void testVirtualMethodTableAccess() {
        ResolvedJavaType unlinkedType = metaAccess.lookupJavaType(UnlinkedType.class);
        assertTrue(!unlinkedType.isLinked());
        for (Class<?> c : classes) {
            if (c.isInterface()) {
                for (Method m : c.getDeclaredMethods()) {
                    ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                    method.isInVirtualMethodTable(unlinkedType);
                }
            }
        }
        for (Class<?> c : classes) {
            if (c.isPrimitive() || c.isInterface()) {
                continue;
            }
            ResolvedJavaType receiverType = metaAccess.lookupJavaType(c);
            for (Method m : c.getMethods()) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                if (!method.isStatic() && !method.isFinal() && !method.getDeclaringClass().isLeaf() && !method.getDeclaringClass().isInterface()) {
                    assertTrue(method + " not available in " + receiverType, method.isInVirtualMethodTable(receiverType));
                }
            }
        }
    }

    /**
     * Encapsulates input for {@link TestResolvedJavaMethod#getAnnotationDataTest}.
     */
    static class AnnotationDataTest {

        public enum NumbersEN {
            One,
            Two;
        }

        public enum NumbersDE {
            Eins,
            Zwei;
        }

        public enum NumbersUA {
            Odyn,
            Dva;
        }

        @Retention(RetentionPolicy.RUNTIME)
        public @interface Annotation1 {
            NumbersEN value() default NumbersEN.One;
        }

        @Retention(RetentionPolicy.RUNTIME)
        public @interface Annotation2 {
            NumbersDE value() default NumbersDE.Eins;
        }

        @Retention(RetentionPolicy.RUNTIME)
        public @interface Annotation3 {
            NumbersUA value() default NumbersUA.Odyn;
        }

        @Annotation1
        @Annotation2
        @Annotation3(NumbersUA.Dva)
        static void methodWithThreeAnnotations() {

        }
    }

    @Test
    public void getAnnotationDataTest() throws Exception {
        TestResolvedJavaType.getAnnotationDataTest(AnnotationTestInput.class.getDeclaredMethod("annotatedMethod"));
        TestResolvedJavaType.getAnnotationDataTest(AnnotationTestInput.class.getDeclaredMethod("missingAnnotation"));
        try {
            TestResolvedJavaType.getAnnotationDataTest(AnnotationTestInput.class.getDeclaredMethod("missingNestedAnnotation"));
            throw new AssertionError("expected " + NoClassDefFoundError.class.getName());
        } catch (NoClassDefFoundError e) {
            Assert.assertEquals("jdk/internal/vm/test/AnnotationTestInput$Missing", e.getMessage());
        }
        TestResolvedJavaType.getAnnotationDataTest(AnnotationTestInput.class.getDeclaredMethod("missingTypeOfClassMember"));
        TestResolvedJavaType.getAnnotationDataTest(AnnotationTestInput.class.getDeclaredMethod("missingMember"));
        TestResolvedJavaType.getAnnotationDataTest(AnnotationTestInput.class.getDeclaredMethod("changeTypeOfMember"));

        for (Method m : methods.keySet()) {
            TestResolvedJavaType.getAnnotationDataTest(m);
        }

        ResolvedJavaMethod m = metaAccess.lookupJavaMethod(AnnotationDataTest.class.getDeclaredMethod("methodWithThreeAnnotations"));
        ResolvedJavaType a1 = metaAccess.lookupJavaType(Annotation1.class);
        ResolvedJavaType a2 = metaAccess.lookupJavaType(Annotation2.class);
        ResolvedJavaType a3 = metaAccess.lookupJavaType(Annotation3.class);
        ResolvedJavaType a4 = metaAccess.lookupJavaType(AnnotationDataTest.class);
        ResolvedJavaType numbersDEType = metaAccess.lookupJavaType(NumbersDE.class);

        // Ensure NumbersDE is not initialized before Annotation2 is requested
        Assert.assertFalse(numbersDEType.isInitialized());
        Assert.assertEquals(2, m.getAnnotationData(a1, a3).size());

        // Ensure NumbersDE is initialized after Annotation2 is requested
        Assert.assertNotNull(m.getAnnotationData(a2));
        Assert.assertTrue(numbersDEType.isInitialized());
    }

    private static ClassModel readClassfile(Class<?> c) throws Exception {
        String name = c.getName();
        final int lastDot = name.lastIndexOf('.');
        if (lastDot != -1) {
            name = name.substring(lastDot + 1);
        }
        URI uri = c.getResource(name + ".class").toURI();
        if (uri.getScheme().equals("jar")) {
            final String[] parts = uri.toString().split("!");
            if (parts.length == 2) {
                try (FileSystem fs = FileSystems.newFileSystem(URI.create(parts[0]), new HashMap<>())) {
                    return ClassFile.of().parse(fs.getPath(parts[1]));
                }
            }
        }
        return ClassFile.of().parse(Paths.get(uri));
    }

    public static void methodWithManyArgs(
        Object   o0, int   i1, int  i2,  int   i3, int   i4, int   i5, int   i6, int  i7,
           int   i8, int   i9, int  i10, int  i11, int  i12, int  i13, int  i14, int  i15,
           int  i16, int  i17, int  i18, int  i19, int  i20, int  i21, int  i22, int  i23,
           int  i24, int  i25, int  i26, int  i27, int  i28, int  i29, int  i30, int  i31,
           int  i32, int  i33, int  i34, int  i35, int  i36, int  i37, int  i38, int  i39,
           int  i40, int  i41, int  i42, int  i43, int  i44, int  i45, int  i46, int  i47,
           int  i48, int  i49, int  i50, int  i51, int  i52, int  i53, int  i54, int  i55,
           int  i56, int  i57, int  i58, int  i59, int  i60, int  i61, int  i62, int  i63,
        Object  o64, int  i65, int  i66, int  i67, int  i68, int  i69, int  i70, int  i71,
           int  i72, int  i73, int  i74, int  i75, int  i76, int  i77, int  i78, int  i79,
           int  i80, int  i81, int  i82, int  i83, int  i84, int  i85, int  i86, int  i87,
           int  i88, int  i89, int  i90, int  i91, int  i92, int  i93, int  i94, int  i95,
           int  i96, int  i97, int  i98, int  i99, int i100, int i101, int i102, int i103,
           int i104, int i105, int i106, int i107, int i108, int i109, int i110, int i111,
           int i112, int i113, int i114, int i115, int i116, int i117, int i118, int i119,
           int i120, int i121, int i122, int i123, int i124, int i125, int i126, int i127,
        Object o128)
    {
        o0.hashCode();
        o64.hashCode();
        if (o128 != null) {
            Object t1 = "tmp val";
            t1.hashCode();
        } else {
            int t1 = 42 + i1;
            String.valueOf(t1);
        }
        o128.hashCode();
    }

    private static Map<String, ResolvedJavaMethod> buildMethodMap(ResolvedJavaType type) {
        Map<String, ResolvedJavaMethod> methodMap = new HashMap<>();
        for (ResolvedJavaMethod m : type.getDeclaredMethods()) {
            if (m.hasBytecodes()) {
                String key = m.getName() + ":" + m.getSignature().toMethodDescriptor();
                methodMap.put(key, m);
            }
        }
        for (ResolvedJavaMethod m : type.getDeclaredConstructors()) {
            if (m.hasBytecodes()) {
                String key = "<init>:" + m.getSignature().toMethodDescriptor();
                methodMap.put(key, m);
            }
        }
        ResolvedJavaMethod clinit = type.getClassInitializer();
        if (clinit != null) {
            String key = "<clinit>:()V";
            methodMap.put(key, clinit);
        }
        return methodMap;
    }

    @Test
    public void getOopMapAtTest() throws Exception {
        Collection<Class<?>> allClasses = new ArrayList<>(classes);

        // Add this class so that methodWithManyArgs is processed
        allClasses.add(getClass());

        boolean[] processedMethodWithManyArgs = {false};

        for (Class<?> c : allClasses) {
            if (c.isArray() || c.isPrimitive() || c.isHidden()) {
                continue;
            }
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            Map<String, ResolvedJavaMethod> methodMap = buildMethodMap(type);
            ClassModel cf = readClassfile(c);
            for (MethodModel cm : cf.methods()) {
                cm.findAttribute(Attributes.code()).ifPresent(codeAttr -> {
                    String key = cm.methodName().stringValue() + ":" + cm.methodType().stringValue();
                    HotSpotResolvedJavaMethod m = (HotSpotResolvedJavaMethod) Objects.requireNonNull(methodMap.get(key));
                    boolean isMethodWithManyArgs = c == getClass() && m.getName().equals("methodWithManyArgs");
                    if (isMethodWithManyArgs) {
                        processedMethodWithManyArgs[0] = true;
                    }
                    int maxSlots = m.getMaxLocals() + m.getMaxStackSize();

                    int bci = 0;
                    Map<String, int[]> expectOopMaps = !isMethodWithManyArgs ? null : Map.of(
                        "{0, 64, 128}",      new int[] {0},
                        "{0, 64, 128, 130}", new int[] {0},
                        "{0, 64, 128, 129}", new int[] {0});
                    for (CodeElement i : codeAttr.elementList()) {
                        if (i instanceof Instruction ins) {
                            BitSet oopMap = m.getOopMapAt(bci);
                            if (isMethodWithManyArgs) {
                                System.out.printf("methodWithManyArgs@%d [%d]: %s%n", bci, maxSlots, oopMap);
                                System.out.printf("methodWithManyArgs@%d [%d]: %s%n", bci, maxSlots, ins);

                                // Assumes stability of javac output
                                String where = "methodWithManyArgs@" + bci;
                                String oopMapString = String.valueOf(oopMap);
                                int[] count = expectOopMaps.get(oopMapString);
                                if (count == null) {
                                    throw new AssertionError(where + ": unexpected oop map: " + oopMapString);
                                }
                                count[0]++;
                            }

                            // Requesting an oop map at an invalid BCI must throw an exception
                            if (ins.sizeInBytes() > 1) {
                                try {
                                    oopMap = m.getOopMapAt(bci + 1);
                                    throw new AssertionError("expected exception for illegal bci %d in %s: %s".formatted(bci + 1, m.format("%H.%n(%p)"), oopMap));
                                } catch(IllegalArgumentException e) {
                                    // expected
                                }
                            }
                            bci += ins.sizeInBytes();
                        }
                    }
                    if (isMethodWithManyArgs) {
                        for (var e : expectOopMaps.entrySet()) {
                            if (e.getValue()[0] == 0) {
                                throw new AssertionError(m.format("%H.%n(%p)") + "did not find expected oop map: " + e.getKey());
                            }
                            System.out.printf("methodWithManyArgs: %s = %d%n", e.getKey(), e.getValue()[0]);
                        }
                    }
                });
            }
        }

        Assert.assertTrue(processedMethodWithManyArgs[0]);
    }

    @Test
    public void getLocalVariableTableTest() {
        for (ResolvedJavaMethod m : methods.values()) {
            LocalVariableTable table = m.getLocalVariableTable();
            if (table == null) {
                continue;
            }
            for (Local l : table.getLocals()) {
                if (l.getStartBCI() < 0) {
                    throw new AssertionError(m.format("%H.%n(%p)") + " local " + l.getName() + " starts at " + l.getStartBCI());
                }
                if (l.getEndBCI() >= m.getCodeSize()) {
                    throw new AssertionError(m.format("%H.%n(%p)") + " (" + m.getCodeSize() + "bytes) local " + l.getName() + " ends at " + l.getEndBCI());
                }
            }
        }
    }

    private Method findTestMethod(Method apiMethod) {
        String testName = apiMethod.getName() + "Test";
        for (Method m : getClass().getDeclaredMethods()) {
            if (m.getName().equals(testName) && m.getAnnotation(Test.class) != null) {
                return m;
            }
        }
        return null;
    }

    // @formatter:off
    private static final String[] untestedApiMethods = {
        "newInstance",
        "getDeclaringClass",
        "getEncoding",
        "getProfilingInfo",
        "reprofile",
        "getCompilerStorage",
        "hasNeverInlineDirective",
        "canBeInlined",
        "shouldBeInlined",
        "getLineNumberTable",
        "isInVirtualMethodTable",
        "toParameterTypes",
        "getParameterAnnotation",
        "getSpeculationLog",
        "isFinal",
        "invoke",
        "$jacocoInit"
    };
    // @formatter:on

    /**
     * Ensures that any new methods added to {@link ResolvedJavaMethod} either have a test written
     * for them or are added to {@link #untestedApiMethods}.
     */
    @Test
    public void testCoverage() {
        Set<String> known = new HashSet<>(Arrays.asList(untestedApiMethods));
        for (Method m : ResolvedJavaMethod.class.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            if (findTestMethod(m) == null) {
                assertTrue("test missing for " + m, known.contains(m.getName()));
            } else {
                assertFalse("test should be removed from untestedApiMethods" + m, known.contains(m.getName()));
            }
        }
    }

    @SafeVarargs
    public static <K, V> Map<K, V> join(Map<? extends K, V>... maps) {
        Map<K, V> res = new HashMap<>();
        for (Map<? extends K, V> e : maps) {
            res.putAll(e);
        }
        return res;
    }

    @SafeVarargs
    public static <V> List<V> joinValues(Map<?, V>... maps) {
        List<V> res = new ArrayList<>();
        for (Map<?, V> e : maps) {
            res.addAll(e.values());
        }
        return res;
    }
}
