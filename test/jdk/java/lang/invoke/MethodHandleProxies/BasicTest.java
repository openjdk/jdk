/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassFile;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.invoke.MethodHandleProxies.*;
import static java.lang.invoke.MethodType.genericMethodType;
import static java.lang.invoke.MethodType.methodType;
import static java.lang.classfile.ClassFile.*;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 6983726 8206955 8269351 8350549
 * @summary Basic sanity tests for MethodHandleProxies
 * @build BasicTest Client
 * @run junit BasicTest
 */
public class BasicTest {

    @Test
    public void testUsual() throws Throwable {
        AtomicInteger ai = new AtomicInteger(5);
        var mh = MethodHandles.lookup().findVirtual(AtomicInteger.class, "getAndIncrement", methodType(int.class));
        IntSupplier is = asInterfaceInstance(IntSupplier.class, mh.bindTo(ai));
        assertEquals(5, is.getAsInt());
        assertEquals(6, is.getAsInt());
        assertEquals(7, is.getAsInt());
    }

    /**
     * Established null behaviors of MHP API.
     */
    @Test
    public void testNulls() {
        assertThrows(NullPointerException.class, () ->
                        asInterfaceInstance(null, MethodHandles.zero(void.class)),
                "asInterfaceInstance - intfc");
        assertThrows(NullPointerException.class, () ->
                        asInterfaceInstance(Runnable.class, null),
                "asInterfaceInstance - target");

        assertFalse(isWrapperInstance(null), "isWrapperInstance");

        assertThrows(IllegalArgumentException.class, () -> wrapperInstanceTarget(null),
                "wrapperInstanceTarget");
        assertThrows(IllegalArgumentException.class, () -> wrapperInstanceType(null),
                "wrapperInstanceType");
    }

    @Test
    public void testWrapperInstance() throws Throwable {
        var mh = MethodHandles.publicLookup()
                .findVirtual(Integer.class, "compareTo", methodType(int.class, Integer.class));
        @SuppressWarnings("unchecked")
        Comparator<Integer> proxy = (Comparator<Integer>) asInterfaceInstance(Comparator.class, mh);

        assertTrue(isWrapperInstance(proxy));
        assertSame(mh, wrapperInstanceTarget(proxy));
        assertSame(Comparator.class, wrapperInstanceType(proxy));
    }

    /**
     * Tests undeclared exceptions and declared exceptions in proxies.
     */
    @Test
    public void testThrowables() {
        // don't wrap
        assertThrows(Error.class, throwing(Error.class, new Error())::close,
                "Errors should be propagated");
        assertThrows(RuntimeException.class, throwing(RuntimeException.class, new RuntimeException())::close,
                "RuntimeException should be propagated");
        assertThrows(IOException.class, throwing(IOException.class, new IOException())::close,
                "Declared IOException should be propagated");
        // wrap
        assertThrows(UndeclaredThrowableException.class, throwing(IllegalAccessException.class,
                        new IllegalAccessException())::close,
                "Undeclared IllegalAccessException should be wrapped");
    }

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

    /**
     * Tests primitive type conversions in proxies.
     */
    @Test
    public void testPrimitiveConversion() throws Throwable {
        var mh = MethodHandles.lookup().findStatic(BasicTest.class, "mul",
                methodType(long.class, int.class));
        @SuppressWarnings("unchecked")
        Function<Integer, Long> func = (Function<Integer, Long>) asInterfaceInstance(Function.class, mh);
        assertEquals(32423432L * 32423432L, func.apply(32423432));
        @SuppressWarnings("unchecked")
        ToLongFunction<Integer> func1 = (ToLongFunction<Integer>) asInterfaceInstance(ToLongFunction.class, mh);
        assertEquals(32423432L * 32423432L, func1.applyAsLong(32423432));
        @SuppressWarnings("unchecked")
        IntFunction<Long> func2 = (IntFunction<Long>) asInterfaceInstance(IntFunction.class, mh);
        assertEquals(32423432L * 32423432L, func2.apply(32423432));
    }

    /**
     * Tests common type conversions in proxies.
     */
    @Test
    public void testBasicConversion() {
        var mh = MethodHandles.constant(String.class, "42");
        asInterfaceInstance(Client.class, mh).exec(); // return value dropped, runs fine

        var nullMh = MethodHandles.zero(String.class);
        var badIterable = asInterfaceInstance(Iterable.class, nullMh);
        assertNull(badIterable.iterator()); // null is convertible
    }

    /**
     * Tests incompatible type conversions in proxy construction.
     */
    @Test
    public void testWrongConversion() {
        var mh = MethodHandles.constant(String.class, "42");
        assertThrows(WrongMethodTypeException.class, () -> asInterfaceInstance(IntSupplier.class, mh),
                "cannot convert String return to int under any circumstance");

        var proxy = asInterfaceInstance(Iterable.class, mh);
        assertThrows(ClassCastException.class, proxy::iterator);
    }

    /**
     * Verifies {@code isWrapperInstance} works under race and is thread safe
     * like {@code Class} objects are.
     */
    @Test
    public void testRacyWrapperCheck() {
        MethodHandle noop = MethodHandles.zero(void.class);
        var lookup = MethodHandles.lookup();
        AtomicInteger counter = new AtomicInteger();
        Stream.generate(() -> {
            String name = "MHPRaceIface" + counter.getAndIncrement();
            var bytes = ClassFile.of().build(ClassDesc.of(name), clb ->
                    clb.withFlags(ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE)
                       .withMethod("sam", MTD_void, ACC_PUBLIC | ACC_ABSTRACT, _ -> {}));
            try {
                return lookup.defineClass(bytes);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }).parallel()
                .map(cl -> MethodHandleProxies.asInterfaceInstance(cl, noop))
                .limit(100)
                .forEach(inst -> assertTrue(MethodHandleProxies.isWrapperInstance(inst),
                        () -> Objects.toIdentityString(inst) + " should pass wrapper test"));
    }

    private static <T extends Throwable> Closeable throwing(Class<T> clz, T value) {
        return asInterfaceInstance(Closeable.class, MethodHandles.throwException(void.class, clz).bindTo(value));
    }

    private static long mul(int i) {
        return (long) i * i;
    }

    void checkMethods(Method[] methods) {
        assertTrue(methods.length > 1, () -> "Should have more than 1 declared methods, found only " + Arrays.toString(methods));
        for (Method method : methods) {
            assertTrue(method.accessFlags().contains(AccessFlag.ABSTRACT), () -> method + " is not abstract");
        }
    }

    private Class<?> loadHidden() {
        try (var is = BasicTest.class.getResourceAsStream("Client.class")) {
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
        ClassDesc baseCd = ClassDesc.of("BasicTest$Base");
        ClassDesc childCd = ClassDesc.of("BasicTest$Child");
        var objMtd = MethodTypeDesc.of(CD_Object);
        var integerMtd = MethodTypeDesc.of(CD_Integer);
        var intMtd = MethodTypeDesc.of(CD_int);
        var classfile = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.defaultResolver().orElse(
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
}
