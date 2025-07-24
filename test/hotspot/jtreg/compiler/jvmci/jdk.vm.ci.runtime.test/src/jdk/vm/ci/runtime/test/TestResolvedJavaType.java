/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @library /testlibrary/asm
 * @compile ../../../../../../../../../../../jdk/jdk/internal/vm/AnnotationEncodingDecoding/AnnotationTestInput.java
 *          ../../../../../../../../../../../jdk/jdk/internal/vm/AnnotationEncodingDecoding/MemberDeleted.java
 *          ../../../../../../../../../../../jdk/jdk/internal/vm/AnnotationEncodingDecoding/MemberTypeChanged.java
 * @clean jdk.internal.vm.test.AnnotationTestInput$Missing
 * @compile ../../../../../../../../../../../jdk/jdk/internal/vm/AnnotationEncodingDecoding/alt/MemberDeleted.java
 *          ../../../../../../../../../../../jdk/jdk/internal/vm/AnnotationEncodingDecoding/alt/MemberTypeChanged.java
 * @modules java.base/jdk.internal.reflect
 *          jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.hotspot
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *          jdk.internal.vm.ci/jdk.vm.ci.common
 *          java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm
 *          java.base/sun.reflect.annotation
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:-UseJVMCICompiler jdk.vm.ci.runtime.test.TestResolvedJavaType
 */

package jdk.vm.ci.runtime.test;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static jdk.vm.ci.meta.MetaUtil.internalNameToJava;
import static jdk.vm.ci.meta.MetaUtil.toInternalName;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import jdk.internal.reflect.ConstantPool;
import jdk.internal.vm.test.AnnotationTestInput;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Annotated;
import jdk.vm.ci.meta.AnnotationData;
import jdk.vm.ci.meta.EnumData;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;
import sun.reflect.annotation.AnnotationSupport;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

/**
 * Tests for {@link ResolvedJavaType}.
 */
public class TestResolvedJavaType extends TypeUniverse {
    private static final Class<? extends Annotation> SIGNATURE_POLYMORPHIC_CLASS = findPolymorphicSignatureClass();
    private static final HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();

    public TestResolvedJavaType() {
    }

    @Test
    public void getMirrorTest() {
        for (ResolvedJavaType type : javaTypes) {
            assertEquals(type.toClassName(), runtime.getMirror(type).getName());
        }
    }

    @Test
    public void equalsTest() {
        for (ResolvedJavaType t : javaTypes) {
            for (ResolvedJavaType that : javaTypes) {
                boolean expect = t == that;
                boolean actual = t.equals(that);
                assertEquals(expect, actual);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> findPolymorphicSignatureClass() {
        Class<? extends Annotation> signaturePolyAnnotation = null;
        try {
            for (Class<?> clazz : TestResolvedJavaType.class.getClassLoader().loadClass("java.lang.invoke.MethodHandle").getDeclaredClasses()) {
                if (clazz.getName().endsWith("PolymorphicSignature") && Annotation.class.isAssignableFrom(clazz)) {
                    signaturePolyAnnotation = (Class<? extends Annotation>) clazz;
                    break;
                }
            }
        } catch (Throwable e) {
            throw new AssertionError("Could not find annotation PolymorphicSignature in java.lang.invoke.MethodHandle", e);
        }
        assertNotNull(signaturePolyAnnotation);
        return signaturePolyAnnotation;
    }

    @Test
    public void findInstanceFieldWithOffsetTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            Set<Field> reflectionFields = Set.copyOf(getInstanceFields(c, true));
            for (Field f : reflectionFields) {
                ResolvedJavaField rf = lookupField(type.getInstanceFields(true), f);
                assertNotNull(rf);
                long offset = isStatic(f.getModifiers()) ? unsafe.staticFieldOffset(f) : unsafe.objectFieldOffset(f);
                ResolvedJavaField result = type.findInstanceFieldWithOffset(offset, rf.getJavaKind());
                assertNotNull(result);
                assertTrue(fieldsEqual(f, result));
            }
        }
    }

    @Test
    public void isAnnotationTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            boolean expected = c.isAnnotation();
            boolean actual = type.isAnnotation();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void isInterfaceTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            boolean expected = c.isInterface();
            boolean actual = type.isInterface();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void isEnumTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            boolean expected = c.isEnum();
            boolean actual = type.isEnum();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void isInstanceClassTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            boolean expected = !c.isArray() && !c.isPrimitive() && !c.isInterface();
            boolean actual = type.isInstanceClass();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void isArrayTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            boolean expected = c.isArray();
            boolean actual = type.isArray();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void isConcreteTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            boolean expected = c.isArray() || !isAbstract(c.getModifiers());
            boolean actual = type.isConcrete();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void lambdaInternalNameTest() {
        // Verify that the last dot in lambda types is properly handled when transitioning from
        // internal name to java
        // name and vice versa.
        Supplier<Runnable> lambda = () -> () -> System.out.println("run");
        ResolvedJavaType lambdaType = metaAccess.lookupJavaType(lambda.getClass());
        String typeName = lambdaType.getName();
        String javaName = lambda.getClass().getName();
        assertEquals(typeName, toInternalName(javaName));
        assertEquals(javaName, internalNameToJava(typeName, true, true));
    }

    @Test
    public void getModifiersTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            int mask = Modifier.classModifiers() & ~Modifier.STATIC;
            int expected = c.getModifiers() & mask;
            int actual = type.getModifiers() & mask;
            Class<?> elementalType = c;
            while (elementalType.isArray()) {
                elementalType = elementalType.getComponentType();
            }
            if (elementalType.isMemberClass()) {
                // member class get their modifiers from the inner-class attribute in the JVM and
                // from the classfile header in jvmci
                expected &= ~(Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED);
                actual &= ~(Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED);
            }
            assertEquals(String.format("%s: 0x%x != 0x%x", type, expected, actual), expected, actual);
        }
    }

    @Test
    public void isAssignableFromTest() {
        Class<?>[] all = classes.toArray(new Class<?>[classes.size()]);
        for (int i = 0; i < all.length; i++) {
            Class<?> c1 = all[i];
            for (int j = i; j < all.length; j++) {
                Class<?> c2 = all[j];
                ResolvedJavaType t1 = metaAccess.lookupJavaType(c1);
                ResolvedJavaType t2 = metaAccess.lookupJavaType(c2);
                boolean expected = c1.isAssignableFrom(c2);
                boolean actual = t1.isAssignableFrom(t2);
                assertEquals(expected, actual);
                if (expected && t1 != t2) {
                    assertFalse(t2.isAssignableFrom(t1));
                }
            }
        }
    }

    @Test
    public void isInstanceTest() {
        for (ConstantValue cv : constants()) {
            JavaConstant c = cv.value;
            if (c.getJavaKind() == JavaKind.Object && !c.isNull()) {
                ResolvedJavaType cType = metaAccess.lookupJavaType(c);
                for (ResolvedJavaType t : javaTypes) {
                    if (t.isAssignableFrom(cType)) {
                        assertTrue(t.isInstance(c));
                    } else {
                        assertFalse(t.isInstance(c));
                    }
                }
            }
        }
    }

    @Test
    public void getSuperclassTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            Class<?> expected = c.getSuperclass();
            ResolvedJavaType actual = type.getSuperclass();
            if (expected == null) {
                assertTrue(actual == null);
            } else {
                assertNotNull(actual);
                assertTrue(actual.equals(metaAccess.lookupJavaType(expected)));
            }
        }
    }

    @Test
    public void getInterfacesTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            Class<?>[] expected = c.getInterfaces();
            ResolvedJavaType[] actual = type.getInterfaces();
            assertEquals(expected.length, actual.length);
            for (int i = 0; i < expected.length; i++) {
                assertTrue(actual[i].equals(metaAccess.lookupJavaType(expected[i])));
            }
        }
    }

    public Class<?> getSupertype(Class<?> c) {
        assert !c.isPrimitive();
        if (c.isArray()) {
            Class<?> componentType = c.getComponentType();
            if (componentType.isPrimitive() || componentType == Object.class) {
                return Object.class;
            }
            return getArrayClass(getSupertype(componentType));
        }
        if (c.isInterface()) {
            return Object.class;
        }
        return c.getSuperclass();
    }

    public Class<?> findLeastCommonAncestor(Class<?> c1Initial, Class<?> c2Initial) {
        if (c1Initial.isPrimitive() || c2Initial.isPrimitive()) {
            return null;
        } else {
            Class<?> c1 = c1Initial;
            Class<?> c2 = c2Initial;
            while (true) {
                if (c1.isAssignableFrom(c2)) {
                    return c1;
                }
                if (c2.isAssignableFrom(c1)) {
                    return c2;
                }
                c1 = getSupertype(c1);
                c2 = getSupertype(c2);
            }
        }
    }

    @Test
    public void findLeastCommonAncestorTest() {
        Class<?>[] all = classes.toArray(new Class<?>[classes.size()]);
        for (int i = 0; i < all.length; i++) {
            Class<?> c1 = all[i];
            for (int j = i; j < all.length; j++) {
                Class<?> c2 = all[j];
                ResolvedJavaType t1 = metaAccess.lookupJavaType(c1);
                ResolvedJavaType t2 = metaAccess.lookupJavaType(c2);
                Class<?> expected = findLeastCommonAncestor(c1, c2);
                ResolvedJavaType actual = t1.findLeastCommonAncestor(t2);
                if (expected == null) {
                    assertTrue(actual == null);
                } else {
                    assertNotNull(actual);
                    assertTrue(actual.equals(metaAccess.lookupJavaType(expected)));
                }
            }
        }
    }

    @Test
    public void linkTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            type.link();
        }
    }

    private class HidingClassLoader extends ClassLoader {
        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            if (name.endsWith("MissingInterface")) {
                throw new ClassNotFoundException("missing");
            }
            byte[] classData = null;
            try {
                InputStream is = HidingClassLoader.class.getResourceAsStream("/" + name.replace('.', '/') + ".class");
                classData = new byte[is.available()];
                new DataInputStream(is).readFully(classData);
            } catch (IOException e) {
                Assert.fail("can't access class: " + name);
            }

            return defineClass(null, classData, 0, classData.length);
        }

        ResolvedJavaType lookupJavaType(String name) throws ClassNotFoundException {
            return metaAccess.lookupJavaType(loadClass(name));
        }

        HidingClassLoader() {
            super(null);
        }

    }

    interface MissingInterface {
    }

    static class MissingInterfaceImpl implements MissingInterface {
    }

    interface SomeInterface {
        default MissingInterface someMethod() {
            return new MissingInterfaceImpl();
        }
    }

    static class Wrapper implements SomeInterface {
    }

    @Test
    public void linkExceptionTest() throws ClassNotFoundException {
        HidingClassLoader cl = new HidingClassLoader();
        ResolvedJavaType inner = cl.lookupJavaType(Wrapper.class.getName());
        assertTrue("expected default methods", inner.hasDefaultMethods());
        try {
            inner.link();
            assertFalse("link should throw an exception", true);
        } catch (NoClassDefFoundError e) {
        }
    }

    @Test
    public void hasDefaultMethodsTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            assertEquals(hasDefaultMethods(type), type.hasDefaultMethods());
        }
    }

    @Test
    public void declaresDefaultMethodsTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            assertEquals(declaresDefaultMethods(type), type.declaresDefaultMethods());
        }
    }

    private static boolean hasDefaultMethods(ResolvedJavaType type) {
        if (!type.isInterface() && type.getSuperclass() != null && hasDefaultMethods(type.getSuperclass())) {
            return true;
        }
        for (ResolvedJavaType iface : type.getInterfaces()) {
            if (hasDefaultMethods(iface)) {
                return true;
            }
        }
        return declaresDefaultMethods(type);
    }

    static boolean declaresDefaultMethods(ResolvedJavaType type) {
        if (!type.isInterface()) {
            /* Only interfaces can declare default methods. */
            return false;
        }
        for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
            if (method.isDefault()) {
                assert !Modifier.isStatic(method.getModifiers()) : "Default method that is static?";
                return true;
            }
        }
        return false;
    }

    private static class Base {
    }

    abstract static class Abstract1 extends Base {
    }

    interface Interface1 {
    }

    static class Concrete1 extends Abstract1 {
    }

    static class Concrete2 extends Abstract1 implements Interface1 {
    }

    static class Concrete3 extends Concrete2 {
    }

    static final class Final1 extends Abstract1 {
    }

    abstract static class Abstract4 extends Concrete3 {
    }

    void checkConcreteSubtype(ResolvedJavaType type, ResolvedJavaType expected) {
        AssumptionResult<ResolvedJavaType> leafConcreteSubtype = type.findLeafConcreteSubtype();
        if (leafConcreteSubtype == null) {
            // findLeafConcreteSubtype() is conservative
        } else {
            if (expected == null) {
                assertNull(leafConcreteSubtype);
            } else {
                assertTrue(leafConcreteSubtype.getResult().equals(expected));
            }
            assertTrue(!type.isLeaf() || leafConcreteSubtype.isAssumptionFree());
        }

        if (!type.isArray()) {
            ResolvedJavaType arrayType = type.getArrayClass();
            AssumptionResult<ResolvedJavaType> arraySubtype = arrayType.findLeafConcreteSubtype();
            if (arraySubtype != null) {
                assertEquals(arraySubtype.getResult(), arrayType);
            } else {
                // findLeafConcreteSubtype() method is conservative
            }
        }
    }

    @Test
    public void findLeafConcreteSubtypeTest() {
        ResolvedJavaType base = metaAccess.lookupJavaType(Base.class);
        checkConcreteSubtype(base, base);

        ResolvedJavaType a1 = metaAccess.lookupJavaType(Abstract1.class);
        ResolvedJavaType c1 = metaAccess.lookupJavaType(Concrete1.class);

        checkConcreteSubtype(base, null);
        checkConcreteSubtype(a1, c1);
        checkConcreteSubtype(c1, c1);

        ResolvedJavaType i1 = metaAccess.lookupJavaType(Interface1.class);
        ResolvedJavaType c2 = metaAccess.lookupJavaType(Concrete2.class);

        checkConcreteSubtype(base, null);
        checkConcreteSubtype(a1, null);
        checkConcreteSubtype(c1, c1);
        checkConcreteSubtype(i1, c2);
        checkConcreteSubtype(c2, c2);

        ResolvedJavaType c3 = metaAccess.lookupJavaType(Concrete3.class);
        checkConcreteSubtype(c2, null);
        checkConcreteSubtype(c3, c3);

        ResolvedJavaType a4 = metaAccess.lookupJavaType(Abstract4.class);
        checkConcreteSubtype(c3, null);
        checkConcreteSubtype(a4, null);

        ResolvedJavaType a1a = metaAccess.lookupJavaType(Abstract1[].class);
        checkConcreteSubtype(a1a, null);
        ResolvedJavaType i1a = metaAccess.lookupJavaType(Interface1[].class);
        checkConcreteSubtype(i1a, null);
        ResolvedJavaType c1a = metaAccess.lookupJavaType(Concrete1[].class);
        checkConcreteSubtype(c1a, c1a);
        ResolvedJavaType f1a = metaAccess.lookupJavaType(Final1[].class);
        checkConcreteSubtype(f1a, f1a);

        ResolvedJavaType obja = metaAccess.lookupJavaType(Object[].class);
        checkConcreteSubtype(obja, null);

        ResolvedJavaType inta = metaAccess.lookupJavaType(int[].class);
        checkConcreteSubtype(inta, inta);
    }

    interface NoImplementor {
    }

    interface SingleImplementorInterface {
    }

    static class SingleConcreteImplementor implements SingleImplementorInterface {
    }

    interface SingleAbstractImplementorInterface {
    }

    abstract static class SingleAbstractImplementor implements SingleAbstractImplementorInterface {
    }

    interface MultiImplementorInterface {
    }

    static class ConcreteImplementor1 implements MultiImplementorInterface {
    }

    static class ConcreteImplementor2 implements MultiImplementorInterface {
    }

    interface MultipleAbstractImplementorInterface {
    }

    abstract static class MultiAbstractImplementor1 implements MultipleAbstractImplementorInterface {
    }

    abstract static class MultiAbstractImplementor2 implements MultipleAbstractImplementorInterface {
    }

    interface SingleAbstractImplementorInterface2 {
    }

    interface ExtendedSingleImplementorInterface {
    }

    abstract static class SingleAbstractImplementor2 implements SingleAbstractImplementorInterface2 {
    }

    static class ConcreteTransitiveImplementor1 extends SingleAbstractImplementor2 implements ExtendedSingleImplementorInterface {
    }

    static class ConcreteTransitiveImplementor2 extends SingleAbstractImplementor2 implements ExtendedSingleImplementorInterface {
    }

    @Test
    public void getSingleImplementorTest() {
        ResolvedJavaType iNi = metaAccess.lookupJavaType(NoImplementor.class);
        assertNull(iNi.getSingleImplementor());

        ResolvedJavaType iSi = metaAccess.lookupJavaType(SingleImplementorInterface.class);
        ResolvedJavaType cSi = metaAccess.lookupJavaType(SingleConcreteImplementor.class);
        assertEquals(cSi, iSi.getSingleImplementor());

        ResolvedJavaType iSai = metaAccess.lookupJavaType(SingleAbstractImplementorInterface.class);
        ResolvedJavaType aSai = metaAccess.lookupJavaType(SingleAbstractImplementor.class);
        assertEquals(aSai, iSai.getSingleImplementor());

        ResolvedJavaType iMi = metaAccess.lookupJavaType(MultiImplementorInterface.class);
        metaAccess.lookupJavaType(ConcreteImplementor1.class);
        metaAccess.lookupJavaType(ConcreteImplementor2.class);
        assertEquals(iMi, iMi.getSingleImplementor());

        ResolvedJavaType iMai = metaAccess.lookupJavaType(MultipleAbstractImplementorInterface.class);
        metaAccess.lookupJavaType(MultiAbstractImplementor1.class);
        metaAccess.lookupJavaType(MultiAbstractImplementor2.class);
        assertEquals(iMai, iMai.getSingleImplementor());

        ResolvedJavaType iSai2 = metaAccess.lookupJavaType(SingleAbstractImplementorInterface2.class);
        ResolvedJavaType aSai2 = metaAccess.lookupJavaType(SingleAbstractImplementor2.class);
        metaAccess.lookupJavaType(ConcreteTransitiveImplementor1.class);
        metaAccess.lookupJavaType(ConcreteTransitiveImplementor2.class);
        assertEquals(aSai2, iSai2.getSingleImplementor());

        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            try {
                type.getSingleImplementor();
                if (!c.isInterface()) {
                    throw new AssertionError("Expected exception for calling getSingleImplmentor on " + c.getName());
                }
            } catch (JVMCIError e) {
                if (c.isInterface()) {
                    throw new AssertionError("Unexpected exception", e);
                }
            }
        }
    }

    @Test(expected = JVMCIError.class)
    public void getSingleImplementorTestClassReceiver() {
        ResolvedJavaType base = metaAccess.lookupJavaType(Base.class);
        base.getSingleImplementor();
    }

    @Test(expected = JVMCIError.class)
    public void getSingleImplementorTestPrimitiveReceiver() {
        ResolvedJavaType primitive = metaAccess.lookupJavaType(int.class);
        primitive.getSingleImplementor();
    }

    @Test
    public void getComponentTypeTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            Class<?> expected = c.getComponentType();
            ResolvedJavaType actual = type.getComponentType();
            if (expected == null) {
                assertNull(actual);
            } else {
                assertTrue(actual.equals(metaAccess.lookupJavaType(expected)));
            }
        }
    }

    @Test
    public void getArrayClassTest() {
        for (Class<?> c : classes) {
            if (c != void.class) {
                ResolvedJavaType type = metaAccess.lookupJavaType(c);
                Class<?> expected = getArrayClass(c);
                ResolvedJavaType actual = type.getArrayClass();
                assertTrue(actual.equals(metaAccess.lookupJavaType(expected)));
            }
        }
    }

    static class Declarations {

        final Method implementation;
        final Set<Method> declarations;

        Declarations(Method impl) {
            this.implementation = impl;
            declarations = new HashSet<>();
        }
    }

    /**
     * See <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-5.html#jvms-5.4.5">Method
     * overriding</a>.
     */
    static boolean isOverriderOf(Method impl, Method m) {
        if (!isPrivate(m.getModifiers()) && !isFinal(m.getModifiers())) {
            if (m.getName().equals(impl.getName())) {
                if (m.getReturnType() == impl.getReturnType()) {
                    if (Arrays.equals(m.getParameterTypes(), impl.getParameterTypes())) {
                        if (isPublic(m.getModifiers()) || isProtected(m.getModifiers())) {
                            // m is public or protected
                            return isPublic(impl.getModifiers()) || isProtected(impl.getModifiers());
                        } else {
                            // m is package-private
                            return impl.getDeclaringClass().getPackage() == m.getDeclaringClass().getPackage();
                        }
                    }
                }
            }
        }
        return false;
    }

    static final Map<Class<?>, VTable> vtables = new HashMap<>();

    static class VTable {

        final Map<NameAndSignature, Method> methods = new HashMap<>();
    }

    static synchronized VTable getVTable(Class<?> c) {
        VTable vtable = vtables.get(c);
        if (vtable == null) {
            vtable = new VTable();
            if (c != Object.class) {
                VTable superVtable = getVTable(c.getSuperclass());
                vtable.methods.putAll(superVtable.methods);
            }
            for (Method m : c.getDeclaredMethods()) {
                if (!isStatic(m.getModifiers()) && !isPrivate(m.getModifiers())) {
                    if (isAbstract(m.getModifiers())) {
                        // A subclass makes a concrete method in a superclass abstract
                        vtable.methods.remove(new NameAndSignature(m));
                    } else {
                        vtable.methods.put(new NameAndSignature(m), m);
                    }
                }
            }
            vtables.put(c, vtable);
        }
        return vtable;
    }

    static Set<Method> findDeclarations(Method impl, Class<?> c) {
        Set<Method> declarations = new HashSet<>();
        NameAndSignature implSig = new NameAndSignature(impl);
        if (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (new NameAndSignature(m).equals(implSig)) {
                    declarations.add(m);
                    break;
                }
            }
            if (!c.isInterface()) {
                declarations.addAll(findDeclarations(impl, c.getSuperclass()));
            }
            for (Class<?> i : c.getInterfaces()) {
                declarations.addAll(findDeclarations(impl, i));
            }
        }
        return declarations;
    }

    @Test
    public void resolveMethodTest() {
        ResolvedJavaType context = metaAccess.lookupJavaType(TestResolvedJavaType.class);
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            if (c.isInterface()) {
                for (Method m : c.getDeclaredMethods()) {
                    ResolvedJavaMethod resolved = metaAccess.lookupJavaMethod(m);
                    ResolvedJavaMethod impl = type.resolveMethod(resolved, context);
                    assertEquals(m.toString(), null, impl);
                }
            } else if (c.isPrimitive()) {
                assertEquals("No methods expected", c.getDeclaredMethods().length, 0);
            } else {
                VTable vtable = getVTable(c);
                for (Method impl : vtable.methods.values()) {
                    Set<Method> decls = findDeclarations(impl, c);
                    for (Method decl : decls) {
                        ResolvedJavaMethod m = metaAccess.lookupJavaMethod(decl);
                        if (m.isPublic()) {
                            ResolvedJavaMethod resolvedMethod = type.resolveMethod(m, context);
                            if (isSignaturePolymorphic(m)) {
                                // Signature polymorphic methods must not be resolved
                                assertNull(resolvedMethod);
                            } else {
                                ResolvedJavaMethod i = metaAccess.lookupJavaMethod(impl);
                                assertEquals(m.toString(), i, resolvedMethod);
                            }
                        }
                    }
                }
                // For backwards compatibility treat constructors as resolvable even though they
                // aren't virtually dispatched.
                ResolvedJavaType declaringClass = metaAccess.lookupJavaType(c);
                for (Constructor<?> m : c.getDeclaredConstructors()) {
                    ResolvedJavaMethod decl = metaAccess.lookupJavaMethod(m);
                    ResolvedJavaMethod impl = type.resolveMethod(decl, declaringClass);
                    assertEquals(m.toString(), decl, impl);
                }
                for (Method m : c.getDeclaredMethods()) {
                    if (isStatic(m.getModifiers())) {
                        // resolveMethod really shouldn't be called with static methods and the
                        // result is is somewhat inconsistent so just ignore them
                        continue;
                    }
                    ResolvedJavaMethod decl = metaAccess.lookupJavaMethod(m);
                    ResolvedJavaMethod impl = type.resolveMethod(decl, declaringClass);
                    ResolvedJavaMethod expected = isSignaturePolymorphic(decl) ? null : decl;
                    assertEquals(m.toString(), expected, impl);
                }
            }
        }
    }

    @Test
    public void resolveConcreteMethodTest() {
        ResolvedJavaType context = metaAccess.lookupJavaType(TestResolvedJavaType.class);
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            if (c.isInterface()) {
                for (Method m : c.getDeclaredMethods()) {
                    ResolvedJavaMethod resolved = metaAccess.lookupJavaMethod(m);
                    ResolvedJavaMethod impl = type.resolveConcreteMethod(resolved, context);
                    assertEquals(m.toString(), null, impl);
                }
            } else if (c.isPrimitive()) {
                assertEquals("No methods expected", c.getDeclaredMethods().length, 0);
            } else {
                VTable vtable = getVTable(c);
                for (Method impl : vtable.methods.values()) {
                    Set<Method> decls = findDeclarations(impl, c);
                    for (Method decl : decls) {
                        ResolvedJavaMethod m = metaAccess.lookupJavaMethod(decl);
                        if (m.isPublic()) {
                            ResolvedJavaMethod resolvedMethod = type.resolveConcreteMethod(m, context);
                            if (isSignaturePolymorphic(m)) {
                                // Signature polymorphic methods must not be resolved
                                assertNull(String.format("Got: %s", resolvedMethod), resolvedMethod);
                            } else {
                                ResolvedJavaMethod i = metaAccess.lookupJavaMethod(impl);
                                assertEquals(i, resolvedMethod);
                            }
                        }
                    }
                }
                for (Method m : c.getDeclaredMethods()) {
                    ResolvedJavaMethod impl = type.resolveConcreteMethod(metaAccess.lookupJavaMethod(m), context);
                    ResolvedJavaMethod expected = isAbstract(m.getModifiers()) ? null : impl;
                    assertEquals(type + " " + m.toString(), expected, impl);
                }
            }
        }
    }

    @Test
    public void findUniqueConcreteMethodTest() throws NoSuchMethodException {
        ResolvedJavaMethod thisMethod = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("findUniqueConcreteMethodTest"));
        ResolvedJavaMethod ucm = metaAccess.lookupJavaType(getClass()).findUniqueConcreteMethod(thisMethod).getResult();
        assertEquals(thisMethod, ucm);
    }

    public static List<Field> getInstanceFields(Class<?> c, boolean includeSuperclasses) {
        if (c.isArray() || c.isPrimitive() || c.isInterface()) {
            return List.of();
        }
        List<Field> result = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                result.add(f);
            }
        }
        if (includeSuperclasses && c != Object.class) {
            List<Field> allFields = getInstanceFields(c.getSuperclass(), true);
            allFields.addAll(result);
            result = allFields;
        }
        return result;
    }

    public static Set<Field> getStaticFields(Class<?> c) {
        Set<Field> result = new HashSet<>();
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                result.add(f);
            }
        }
        return result;
    }

    public boolean fieldsEqual(Field f, ResolvedJavaField rjf) {
        return rjf.getDeclaringClass().equals(metaAccess.lookupJavaType(f.getDeclaringClass())) && rjf.getName().equals(f.getName()) &&
                        rjf.getType().resolve(rjf.getDeclaringClass()).equals(metaAccess.lookupJavaType(f.getType()));
    }

    public ResolvedJavaField lookupField(ResolvedJavaField[] fields, Field key) {
        for (ResolvedJavaField rf : fields) {
            if (fieldsEqual(key, rf)) {
                return rf;
            }
        }
        return null;
    }

    public Field lookupField(Collection<Field> fields, ResolvedJavaField key) {
        for (Field f : fields) {
            if (fieldsEqual(f, key)) {
                return f;
            }
        }
        return null;
    }

    /**
     * Replicates the semantics of jdk.internal.reflect.Reflection#fieldFilterMap.
     */
    private static boolean isHiddenFromReflection(ResolvedJavaField f) {
        if (f.getDeclaringClass().equals(metaAccess.lookupJavaType(ConstantPool.class)) && f.getName().equals("constantPoolOop")) {
            return true;
        }
        if (f.getDeclaringClass().equals(metaAccess.lookupJavaType(Class.class))) {
            String name = f.getName();
            return name.equals("classLoader") ||
                   name.equals("classData") ||
                   name.equals("modifiers") ||
                   name.equals("protectionDomain") ||
                   name.equals("primitive");
        }
        if (f.getDeclaringClass().equals(metaAccess.lookupJavaType(Lookup.class))) {
            return f.getName().equals("allowedModes") || f.getName().equals("lookupClass");
        }
        if (f.getDeclaringClass().equals(metaAccess.lookupJavaType(ClassLoader.class)) ||
                        f.getDeclaringClass().equals(metaAccess.lookupJavaType(AccessibleObject.class)) ||
                        f.getDeclaringClass().equals(metaAccess.lookupJavaType(Constructor.class)) ||
                        f.getDeclaringClass().equals(metaAccess.lookupJavaType(Field.class)) ||
                        f.getDeclaringClass().equals(metaAccess.lookupJavaType(Method.class)) ||
                        f.getDeclaringClass().equals(metaAccess.lookupJavaType(Module.class))) {
            return true;
        }
        return false;
    }

    @Test
    public void getInstanceFieldsTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            for (boolean includeSuperclasses : new boolean[]{true, false}) {
                List<Field> reflectFields = getInstanceFields(c, includeSuperclasses);
                ResolvedJavaField[] fields = type.getInstanceFields(includeSuperclasses);
                int reflectFieldIndex = 0;
                for (int i = 0; i < fields.length; i++) {
                    ResolvedJavaField field = fields[i];
                    var mirror = runtime.getMirror(field);
                    if (field.isInternal()) {
                        assertNull(field.toString(), mirror);
                        continue;
                    }
                    assertNotNull(field.toString(), mirror);
                    if (isHiddenFromReflection(field)) {
                        continue;
                    }
                    Field reflectField = reflectFields.get(reflectFieldIndex++);
                    ResolvedJavaField field2 = lookupField(fields, reflectField);

                    assertEquals("ResolvedJavaType.getInstanceFields order differs from Class.getDeclaredFields", field, field2);
                }
                for (ResolvedJavaField rf : fields) {
                    if (!isHiddenFromReflection(rf)) {
                        assertEquals(rf.toString(), lookupField(reflectFields, rf) != null, !rf.isInternal());
                    }
                }

                // Test stability of getInstanceFields
                ResolvedJavaField[] fields2 = type.getInstanceFields(includeSuperclasses);
                assertArrayEquals(fields, fields2);
            }
        }
    }

    @Test
    public void getStaticFieldsTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            Set<Field> expected = getStaticFields(c);
            ResolvedJavaField[] actual = type.getStaticFields();
            for (Field f : expected) {
                assertNotNull(lookupField(actual, f));
            }
            for (ResolvedJavaField rf : actual) {
                var mirror = runtime.getMirror(rf);
                assertNotNull(rf.toString(), mirror);
                if (!isHiddenFromReflection(rf)) {
                    assertEquals(lookupField(expected, rf) != null, !rf.isInternal());
                }
            }

            // Test stability of getStaticFields
            ResolvedJavaField[] actual2 = type.getStaticFields();
            assertArrayEquals(actual, actual2);
        }
    }

    @Test
    public void getDeclaredMethodsTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            Method[] raw = c.getDeclaredMethods();
            Set<ResolvedJavaMethod> expected = new HashSet<>();
            for (Method m : raw) {
                ResolvedJavaMethod resolvedMethod = metaAccess.lookupJavaMethod(m);
                assertNotNull(resolvedMethod);
                expected.add(resolvedMethod);
            }
            Set<ResolvedJavaMethod> actual = new HashSet<>(Arrays.asList(type.getDeclaredMethods()));
            for (ResolvedJavaMethod method : actual) {
                assertNotNull(method.toString(), runtime.getMirror(method));
            }
            assertEquals(expected, actual);
        }
    }

    @Test
    public void getDeclaredConstructorsTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            Constructor<?>[] raw = c.getDeclaredConstructors();
            Set<ResolvedJavaMethod> expected = new HashSet<>();
            for (Constructor<?> m : raw) {
                ResolvedJavaMethod resolvedMethod = metaAccess.lookupJavaMethod(m);
                assertNotNull(resolvedMethod);
                expected.add(resolvedMethod);
            }
            Set<ResolvedJavaMethod> actual = new HashSet<>(Arrays.asList(type.getDeclaredConstructors()));
            for (ResolvedJavaMethod method : actual) {
                assertNotNull(runtime.getMirror(method));
            }
            assertEquals(expected, actual);
        }
    }

    @Test
    public void getAllMethodsTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            Set<ResolvedJavaMethod> allMethods = new HashSet<>(type.getAllMethods(true));
            Stream<ResolvedJavaMethod> allKnownMethods = Stream.concat(Arrays.stream(type.getDeclaredMethods()), Arrays.stream(type.getDeclaredConstructors()));
            allKnownMethods = Stream.concat(allKnownMethods, Stream.ofNullable(type.getClassInitializer()));
            List<ResolvedJavaMethod> missingMethods = allKnownMethods.filter(m -> !allMethods.contains(m)).toList();
            assertTrue(missingMethods.toString(), missingMethods.isEmpty());
        }
    }

    static class A {
        static String name = "foo";
    }

    static class B extends A {
    }

    static class C {
    }

    static class D {
        void foo() {
            // use of assertions causes the class to have a <clinit>
            assert getClass() != null;
        }
    }

    static class SubD extends D {

    }

    private static ResolvedJavaMethod getClassInitializer(Class<?> c) {
        ResolvedJavaMethod clinit = metaAccess.lookupJavaType(c).getClassInitializer();
        if (clinit != null) {
            assertEquals(0, clinit.getAnnotations().length);
            assertEquals(0, clinit.getDeclaredAnnotations().length);
            assertNull(runtime.getMirror(clinit));
        }
        return clinit;
    }

    @Test
    public void getClassInitializerTest() {
        assertNotNull(getClassInitializer(A.class));
        assertNotNull(getClassInitializer(D.class));
        assertNull(getClassInitializer(B.class));
        assertNull(getClassInitializer(C.class));
        assertNull(getClassInitializer(int.class));
        assertNull(getClassInitializer(void.class));
        for (Class<?> c : classes) {
            getClassInitializer(c);
        }
    }

    @Test
    public void getAnnotationsTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            assertArrayEquals(c.getAnnotations(), type.getAnnotations());
        }
    }

    @Test
    public void getAnnotationTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            for (Annotation a : c.getAnnotations()) {
                assertEquals(a, type.getAnnotation(a.annotationType()));
            }
        }
    }

    @Test
    public void getSourceFileNameTest() {
        Class<?> c = Object.class;
        ResolvedJavaType type = metaAccess.lookupJavaType(c);
        assertEquals(type.getSourceFileName(), "Object.java");
    }

    @Test
    public void memberClassesTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            assertEquals(c.isLocalClass(), type.isLocal());
            assertEquals(c.isMemberClass(), type.isMember());
            Class<?> enclc = c.getEnclosingClass();
            ResolvedJavaType enclt = type.getEnclosingType();
            assertFalse(enclc == null ^ enclt == null);
            if (enclc != null) {
                assertEquals(enclt, metaAccess.lookupJavaType(enclc));
            }
        }
    }

    @Test
    public void isLeafTest() {
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            ResolvedJavaType arrayType = c != void.class ? metaAccess.lookupJavaType(getArrayClass(c)) : null;
            if (c.isPrimitive()) {
                assertTrue(type.isLeaf());
                assertTrue(arrayType == null || arrayType.isLeaf());
            } else {
                assertTrue(c.toString(), type.isLeaf() == arrayType.isLeaf());
                if (!c.isArray()) {
                    assertTrue(c.toString(), type.isLeaf() == Modifier.isFinal(c.getModifiers()));
                }
            }
        }
    }

    static class TrivialCloneable implements Cloneable {
        @Override
        protected Object clone() {
            return new TrivialCloneable();
        }
    }

    @Test
    public void isCloneableWithAllocationTest() {
        ResolvedJavaType cloneable = metaAccess.lookupJavaType(Cloneable.class);
        for (Class<?> c : classes) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            if (type.isCloneableWithAllocation()) {
                // Only Cloneable types should be allocation cloneable
                assertTrue(c.toString(), cloneable.isAssignableFrom(type));
            }
        }
        /*
         * We can't know for sure which types should be allocation cloneable on a particular
         * platform but assume that at least totally trivial objects should be.
         */
        ResolvedJavaType trivialCloneable = metaAccess.lookupJavaType(TrivialCloneable.class);
        assertTrue(trivialCloneable.toString(), trivialCloneable.isCloneableWithAllocation());
    }

    @Test
    public void findMethodTest() {
        try {
            ResolvedJavaMethod findFoo = metaAccess.lookupJavaType(D.class).findMethod("foo", metaAccess.parseMethodDescriptor("()V"));
            ResolvedJavaMethod expectedFoo = metaAccess.lookupJavaMethod(D.class.getDeclaredMethod("foo"));
            assertEquals(expectedFoo, findFoo);

            ResolvedJavaMethod wrongReturnTypeFoo = metaAccess.lookupJavaType(D.class).findMethod("foo", metaAccess.parseMethodDescriptor("()I"));
            assertNull(wrongReturnTypeFoo);

            ResolvedJavaMethod wrongArgumentsFoo = metaAccess.lookupJavaType(D.class).findMethod("foo", metaAccess.parseMethodDescriptor("(I)V"));
            assertNull(wrongArgumentsFoo);

            ResolvedJavaMethod wrongNameFoo = metaAccess.lookupJavaType(D.class).findMethod("bar", metaAccess.parseMethodDescriptor("()V"));
            assertNull(wrongNameFoo);

            ResolvedJavaMethod wrongClassFoo = metaAccess.lookupJavaType(SubD.class).findMethod("foo", metaAccess.parseMethodDescriptor("()V"));
            assertNull(wrongClassFoo);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
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

    @Test
    public void getAnnotationDataTest() throws Exception {
        getAnnotationDataTest(AnnotationTestInput.AnnotatedClass.class);
        getAnnotationDataTest(int.class);
        getAnnotationDataTest(void.class);
        for (Class<?> c : classes) {
            getAnnotationDataTest(c);
        }

        // Primitive classes have no annotations but we cannot directly
        // test absence of annotations. Instead, just ensure empty answers
        // are returned when looking up an arbitrary annotation type.
        Class<?>[] prims = {void.class, byte.class, int.class, double.class, float.class, short.class, char.class, long.class};
        ResolvedJavaType overrideType = metaAccess.lookupJavaType(Override.class);
        for (Class<?> c : prims) {
            ResolvedJavaType type = metaAccess.lookupJavaType(c);
            AnnotationData ad = type.getAnnotationData(overrideType);
            Assert.assertNull(String.valueOf(ad), ad);
            List<AnnotationData> adArray = type.getAnnotationData(overrideType, overrideType);
            Assert.assertEquals(0, adArray.size());
        }

        // Test that inherited annotations are handled properly.
        ResolvedJavaType namedType = metaAccess.lookupJavaType(AnnotationTestInput.Named.class);
        AnnotationData ad = metaAccess.lookupJavaType(AnnotationTestInput.OwnName.class).getAnnotationData(namedType);
        Assert.assertEquals("NonInheritedValue", ad.get("value", String.class));
        ad = metaAccess.lookupJavaType(AnnotationTestInput.InheritedName1.class).getAnnotationData(namedType);
        Assert.assertEquals("Super1", ad.get("value", String.class));
        ad = metaAccess.lookupJavaType(AnnotationTestInput.InheritedName2.class).getAnnotationData(namedType);
        Assert.assertEquals("Super2", ad.get("value", String.class));
        ad = metaAccess.lookupJavaType(AnnotationTestInput.InheritedName3.class).getAnnotationData(namedType);
        Assert.assertEquals("Super1", ad.get("value", String.class));
    }

    // @formatter:off
    private static final String[] untestedApiMethods = {
        "initialize",
        "isPrimitive",
        "newArray",
        "isInitialized",
        "isLinked",
        "getJavaClass",
        "getObjectHub",
        "getHostClass",
        "hasFinalizableSubclass",
        "hasFinalizer",
        "isLocal",
        "isJavaLangObject",
        "isMember",
        "getElementalType",
        "getEnclosingType",
        "lookupType",
        "resolveField",
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
        for (Method m : ResolvedJavaType.class.getDeclaredMethods()) {
            if (findTestMethod(m) == null) {
                assertTrue("test missing for " + m, known.contains(m.getName()));
            } else {
                assertFalse("test should be removed from untestedApiMethods" + m, known.contains(m.getName()));
            }
        }
    }

    private static boolean isSignaturePolymorphic(ResolvedJavaMethod method) {
        return method.getAnnotation(SIGNATURE_POLYMORPHIC_CLASS) != null;
    }

    private static void getAnnotationDataExpectedToFail(Annotated annotated, ResolvedJavaType... annotationTypes) {
        try {
            if (annotationTypes.length == 1) {
                annotated.getAnnotationData(annotationTypes[0]);
            } else {
                var tail = Arrays.copyOfRange(annotationTypes, 2, annotationTypes.length);
                annotated.getAnnotationData(annotationTypes[0], annotationTypes[1], tail);
            }
            String s = Stream.of(annotationTypes).map(ResolvedJavaType::toJavaName).collect(Collectors.joining(", "));
            throw new AssertionError("Expected IllegalArgumentException for retrieving (" + s + " from " + annotated);
        } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage(), iae.getMessage().contains("not an annotation interface"));
        }
    }

    /**
     * Tests that {@link AnnotationData} obtained from a {@link Class}, {@link Method} or
     * {@link Field} matches {@link AnnotatedElement#getAnnotations()} for the corresponding JVMCI
     * object.
     *
     * @param annotatedElement a {@link Class}, {@link Method} or {@link Field} object
     */
    public static void getAnnotationDataTest(AnnotatedElement annotatedElement) throws Exception {
        Annotated annotated = toAnnotated(annotatedElement);
        ResolvedJavaType objectType = metaAccess.lookupJavaType(Object.class);
        ResolvedJavaType suppressWarningsType = metaAccess.lookupJavaType(SuppressWarnings.class);
        getAnnotationDataExpectedToFail(annotated, objectType);
        getAnnotationDataExpectedToFail(annotated, suppressWarningsType, objectType);
        getAnnotationDataExpectedToFail(annotated, suppressWarningsType, suppressWarningsType, objectType);

        // Check that querying a missing annotation returns null or an empty list
        assertNull(annotated.getAnnotationData(suppressWarningsType));
        List<AnnotationData> data = annotated.getAnnotationData(suppressWarningsType, suppressWarningsType);
        assertTrue(data.toString(), data.isEmpty());
        data = annotated.getAnnotationData(suppressWarningsType, suppressWarningsType, suppressWarningsType, suppressWarningsType);
        assertTrue(data.toString(), data.isEmpty());

        testGetAnnotationData(annotatedElement, annotated, List.of(annotatedElement.getAnnotations()));
    }

    private static void testGetAnnotationData(AnnotatedElement annotatedElement, Annotated annotated, List<Annotation> annotations) throws AssertionError {
        ResolvedJavaType suppressWarningsType = metaAccess.lookupJavaType(SuppressWarnings.class);
        for (Annotation a : annotations) {
            var annotationType = metaAccess.lookupJavaType(a.annotationType());
            AnnotationData ad = annotated.getAnnotationData(annotationType);
            assertAnnotationsEquals(a, ad);

            // Check that encoding/decoding produces a stable result
            AnnotationData ad2 = annotated.getAnnotationData(annotationType);
            assertEquals(ad, ad2);

            List<AnnotationData> annotationData = annotated.getAnnotationData(annotationType, suppressWarningsType, suppressWarningsType);
            assertEquals(1, annotationData.size());
        }
        if (annotations.size() < 2) {
            return;
        }
        ResolvedJavaType type1 = metaAccess.lookupJavaType(annotations.get(0).annotationType());
        ResolvedJavaType type2 = metaAccess.lookupJavaType(annotations.get(1).annotationType());
        for (int i = 2; i < annotations.size(); i++) {

            ResolvedJavaType[] types = annotations.//
                            subList(2, i + 1).//
                            stream().map(a -> metaAccess.lookupJavaType(a.annotationType())).//
                            toArray(ResolvedJavaType[]::new);
            List<AnnotationData> annotationData = annotated.getAnnotationData(type1, type2, types);
            assertEquals(2 + types.length, annotationData.size());

            for (int j = 0; j < annotationData.size(); j++) {
                Annotation a = annotations.get(j);
                AnnotationData ad = annotationData.get(j);
                assertAnnotationsEquals(a, ad);
            }
        }
    }

    private static Annotated toAnnotated(AnnotatedElement element) {
        if (element instanceof Class<?> t) {
            return metaAccess.lookupJavaType(t);
        } else if (element instanceof Method m) {
            return metaAccess.lookupJavaMethod(m);
        } else {
            Field f = (Field) element;
            return metaAccess.lookupJavaField(f);
        }
    }

    private static UnresolvedJavaType asType(Class<?> valueType) {
        return UnresolvedJavaType.create(MetaUtil.toInternalName(valueType.getName()));
    }

    private static void assertAnnotationsEquals(Annotation a, AnnotationData ad) {
        Map<String, Object> values = AnnotationSupport.memberValues(a);
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String name = e.getKey();
            Object aValue = e.getValue();
            Object adValue;
            try {
                adValue = ad.get(name, Object.class);
            } catch (IllegalArgumentException ex) {
                assertEquals(aValue.toString(), ex.getMessage());
                continue;
            }
            try {
                assertAnnotationElementsEqual(aValue, adValue);
            } catch (ClassCastException ex) {
                throw new AssertionError(a.getClass().getName() + "." + name + " has wrong type: " + adValue.getClass().getName(), ex);
            }
        }
    }

    private static void assertAnnotationElementsEqual(Object aValue, Object adValue) {
        Class<?> valueType = aValue.getClass();
        if (valueType.isEnum()) {
            assertEnumObjectsEquals(aValue, adValue);
        } else if (aValue instanceof Class) {
            assertClassObjectsEquals(aValue, adValue);
        } else if (aValue instanceof Annotation) {
            assertAnnotationObjectsEquals(aValue, adValue);
        } else if (valueType.isArray()) {
            List<?> adList = (List<?>) adValue;
            int length = Array.getLength(aValue);
            assertEquals(length, adList.size());
            for (int i = 0; i < length; i++) {
                assertAnnotationElementsEqual(Array.get(aValue, i), adList.get(i));
            }
        } else {
            assertEquals(aValue.getClass(), adValue.getClass());
            assertEquals(aValue, adValue);
        }
    }

    private static void assertClassObjectsEquals(Object aValue, Object adValue) {
        String aName = ((Class<?>) aValue).getName();
        String adName = ((JavaType) adValue).toClassName();
        assertEquals(aName, adName);
    }

    private static void assertEnumObjectsEquals(Object aValue, Object adValue) {
        EnumData adEnum = (EnumData) adValue;
        String adEnumName = adEnum.getName();
        String aEnumName = ((Enum<?>) aValue).name();
        assertEquals(adEnumName, aEnumName);
    }

    private static void assertAnnotationObjectsEquals(Object aValue, Object adValue) {
        Annotation aAnnotation = (Annotation) aValue;
        AnnotationData adAnnotation = (AnnotationData) adValue;
        assertAnnotationsEquals(aAnnotation, adAnnotation);
    }

    private static void assertArraysEqual(Object aValue, Object adValue, int length, BiConsumer<Object, Object> assertEqualty) {
        Object[] aArray = (Object[]) aValue;
        Object[] adArray = (Object[]) adValue;
        for (int i = 0; i < length; i++) {
            assertEqualty.accept(aArray[i], adArray[i]);
        }
    }
}
