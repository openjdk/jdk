/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.classfile.ClassHierarchyResolver;
import jdk.internal.classfile.Classfile;
import org.junit.jupiter.api.Test;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.invoke.MethodHandleProxies.asInterfaceInstance;
import static java.lang.invoke.MethodType.genericMethodType;
import static java.lang.invoke.MethodType.methodType;
import static jdk.internal.classfile.Classfile.*;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 6983726 8206955 8269351
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 * @summary Tests MethodHandleProxies against various types of interfaces
 * @build ProxiesInterfaceTest Client
 * @run junit ProxiesInterfaceTest
 */
public class ProxiesInterfaceTest {

    /**
     * Tests that invalid interfaces are rejected.
     */
    @Test
    public void testRejects() {
        var mh = MethodHandles.constant(String.class, "42");
        assertThrows(IllegalArgumentException.class, () -> asInterfaceInstance(PackagePrivate.class, mh),
                "non-public interface");
        assertThrows(IllegalArgumentException.class, () -> asInterfaceInstance(loadHidden(), mh),
                "hidden interface");
        assertThrows(IllegalArgumentException.class, () -> asInterfaceInstance(MultiAbstractMethods.class, mh),
                "multiple abstract method names");
        assertThrows(IllegalArgumentException.class, () -> asInterfaceInstance(NoAbstractMethods.class, mh),
                "no abstract method");
        assertThrows(IllegalArgumentException.class, () -> asInterfaceInstance(Sealed.class, mh),
                "sealed interface");
    }

    /**
     * Tests that non-sealed interfaces can be implemented.
     */
    @Test
    public void testNonSealed() {
        MethodHandle target = MethodHandles.constant(String.class, "Non-Sealed");
        NonSealed proxy = asInterfaceInstance(NonSealed.class, target);
        assertEquals(proxy.m(), "Non-Sealed");
    }

    /**
     * Tests that Proxy correctly proxies potential bridge abstract methods.
     */
    @Test
    public void testMultiSameName() throws Throwable {
        var baseAndChild = loadBaseAndChild();
        var baseClass = baseAndChild.get(0);
        var childClass = baseAndChild.get(1);
        checkMethods(childClass.getMethods());
        checkMethods(childClass.getDeclaredMethods());

        var lookup = MethodHandles.lookup();
        var baseValueMh = lookup.findVirtual(baseClass, "value", genericMethodType(0))
                .asType(genericMethodType(1));
        var childIntegerValueMh = lookup.findVirtual(childClass, "value", methodType(Integer.class))
                .asType(methodType(Integer.class, Object.class));
        var childIntValueMh = lookup.findVirtual(childClass, "value", methodType(int.class))
                .asType(methodType(int.class, Object.class));

        Object child = asInterfaceInstance(childClass, MethodHandles.constant(Integer.class, 7));

        assertEquals(7, (Object) baseValueMh.invokeExact(child));
        assertEquals(7, (Integer) childIntegerValueMh.invokeExact(child));
        assertEquals(7, (int) childIntValueMh.invokeExact(child));
    }

    /**
     * Tests that default methods can be used.
     */
    @Test
    public void testDefaultMethods() {
        MethodHandle target = MethodHandles.constant(String.class, "F");
        C proxy = asInterfaceInstance(C.class, target);

        assertEquals(proxy.f(), "F");
        assertEquals(proxy.a(), "A");
        assertEquals(proxy.b(), "B");
        assertEquals(proxy.c(), "C");
        assertEquals(proxy.concat(), "ABC");
    }

    /**
     * Tests that correct implementation of default methods are called,
     * and correct abstract methods are implemented.
     */
    @Test
    public void testOverrides() {
        MethodHandle target = MethodHandles.constant(String.class, "concat");
        D proxy = asInterfaceInstance(D.class, target);

        assertEquals(proxy.a(), "OA");
        assertEquals(proxy.b(), "OB");
        assertEquals(proxy.c(), "OC");
        assertEquals(proxy.f(), "OF");
        assertEquals(proxy.concat(), "concat");
    }

    //<editor-fold desc="Infrastructure">
    void checkMethods(Method[] methods) {
        assertTrue(methods.length > 1, () -> "Should have more than 1 declared methods, found only " + Arrays.toString(methods));
        for (Method method : methods) {
            assertTrue(method.accessFlags().contains(AccessFlag.ABSTRACT), () -> method + " is not abstract");
        }
    }

    private Class<?> loadHidden() {
        try (var is = ProxiesInterfaceTest.class.getResourceAsStream("Client.class")) {
            var bytes = Objects.requireNonNull(is).readAllBytes();
            var lookup = MethodHandles.lookup();
            return lookup.defineHiddenClass(bytes, true).lookupClass();
        } catch (Throwable ex) {
            return fail("Hidden interface loading failure", ex);
        }
    }

    // Base: Object value();
    // Child: Integer value(); int value();
    private List<Class<?>> loadBaseAndChild() throws IllegalAccessException {
        ClassDesc baseCd = ClassDesc.of("ProxiesInterfaceTest$Base");
        ClassDesc childCd = ClassDesc.of("ProxiesInterfaceTest$Child");
        var objMtd = MethodTypeDesc.of(CD_Object);
        var integerMtd = MethodTypeDesc.of(CD_Integer);
        var intMtd = MethodTypeDesc.of(CD_int);
        var classfile = Classfile.of(ClassHierarchyResolverOption.of(ClassHierarchyResolver.defaultResolver().orElse(
                ClassHierarchyResolver.of(List.of(baseCd, childCd), Map.ofEntries(Map.entry(baseCd, CD_Object),
                        Map.entry(childCd, CD_Object))))));

        var baseBytes = classfile.build(baseCd, clb -> {
            clb.withSuperclass(CD_Object);
            clb.withFlags(ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT);
            clb.withMethod("value", objMtd, ACC_PUBLIC | ACC_ABSTRACT, mb -> {});
        });

        var lookup = MethodHandles.lookup();
        var base = lookup.ensureInitialized(lookup.defineClass(baseBytes));

        var childBytes = classfile.build(childCd, clb -> {
            clb.withSuperclass(CD_Object);
            clb.withInterfaceSymbols(baseCd);
            clb.withFlags(ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT);
            clb.withMethod("value", integerMtd, ACC_PUBLIC | ACC_ABSTRACT, mb -> {});
            clb.withMethod("value", intMtd, ACC_PUBLIC | ACC_ABSTRACT, mb -> {});
        });

        var child = lookup.ensureInitialized(lookup.defineClass(childBytes));
        return List.of(base, child);
    }

    public interface MultiAbstractMethods {
        String a();
        String b();
    }

    public interface NoAbstractMethods {
        String toString();
    }

    interface PackagePrivate {
        Object value();
    }

    public interface A {
        default String a() {
            return "A";
        }
    }

    public interface B {
        default String b() {
            return "B";
        }
    }

    public interface C extends A, B {
        String f();

        default String c() {
            return "C";
        }

        default String concat() {
            return a() + b() + c();
        }
    }

    public interface D extends C {
        String concat();

        default String f() {
            return "OF";
        }

        default String a() {
            return "OA";
        }

        default String b() {
            return "OB";
        }

        default String c() {
            return "OC";
        }
    }

    public sealed interface Sealed permits NonSealed {
        String m();
    }

    public non-sealed interface NonSealed extends Sealed {
    }
    //</editor-fold>
}

