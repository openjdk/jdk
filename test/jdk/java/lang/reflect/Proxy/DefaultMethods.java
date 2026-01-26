/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/*
 * @test
 * @bug 8159746
 * @run junit DefaultMethods
 * @summary Basic tests for Proxy::invokeSuper default method
 */

public class DefaultMethods {
    public interface I1 {
        default int m() {
            return 10;
        }
    }

    public interface I2 {
        default int m() {
            return 20;
        }

        private void privateMethod() {
            throw new Error("should not reach here");
        }
    }

    // I3::m inherits from I2:m
    public interface I3 extends I2 {
        default int m3(String... s) {
            return Arrays.stream(s).mapToInt(String::length).sum();
        }
    }

    public interface I4 extends I1, I2 {
        default int m() {
            return 40;
        }

        default int mix(int a, String b) {
            return 0;
        }
    }

    public interface I12 extends I1, I2 {
        @Override
        int m();

        default int sum(int a, int b) {
            return a + b;
        }

        default Object[] concat(Object first, Object... rest) {
            Object[] result = new Object[1 + rest.length];
            result[0] = first;
            System.arraycopy(rest, 0, result, 1, rest.length);
            return result;
        }
    }

    public interface IX {
        default void doThrow(Throwable exception) throws Throwable {
            throw exception;
        }
    }

    private static Method findDefaultMethod(Class<?> refc, Method m) {
        assertTrue(refc.isInterface());

        Method method = assertDoesNotThrow(() -> refc.getMethod(m.getName(), m.getParameterTypes()));
        assertTrue(method.isDefault());
        return method;
    }

    @Test
    public void test() {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        Object proxy = Proxy.newProxyInstance(loader, new Class<?>[] { I1.class, I2.class},
                (o, method, params) -> {
                    return InvocationHandler.invokeDefault(o, findDefaultMethod(I2.class, method), params);
                });
        I1 i1 = (I1) proxy;
        assertEquals(20, i1.m());
    }

    // a default method is declared in one of the proxy interfaces
    private static Object[][] defaultMethods() {
        return new Object[][]{
            new Object[]{new Class<?>[]{I1.class, I2.class}, true, 10},
            new Object[]{new Class<?>[]{I1.class, I3.class}, true, 10},
            new Object[]{new Class<?>[]{I1.class, I12.class}, true, 10},
            new Object[]{new Class<?>[]{I2.class, I12.class}, true, 20},
            new Object[]{new Class<?>[]{I4.class}, true, 40},
            new Object[]{new Class<?>[]{I4.class, I3.class}, true, 40},
            new Object[]{new Class<?>[]{I12.class}, false, -1},
            new Object[]{new Class<?>[]{I12.class, I1.class, I2.class}, false, -1}
        };
    }

    @ParameterizedTest
    @MethodSource("defaultMethods")
    public void testDefaultMethod(Class<?>[] intfs, boolean isDefault, int expected) throws Throwable {
        InvocationHandler ih = (proxy, method, params) -> {
            System.out.format("invoking %s with parameters: %s%n", method, Arrays.toString(params));
            switch (method.getName()) {
                case "m":
                    assertEquals(isDefault, method.isDefault());
                    assertTrue(Arrays.stream(proxy.getClass().getInterfaces())
                                     .anyMatch(intf -> method.getDeclaringClass() == intf),
                               Arrays.toString(proxy.getClass().getInterfaces()));
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, params);
                    } else {
                        return -1;
                    }
                default:
                    throw new UnsupportedOperationException(method.toString());
            }
        };

        Object proxy = Proxy.newProxyInstance(DefaultMethods.class.getClassLoader(), intfs, ih);
        Method m = proxy.getClass().getMethod("m");
        int result = (int)m.invoke(proxy);
        assertEquals(expected, result);
    }

    // a default method may be declared in a proxy interface or
    // inherited from a superinterface of a proxy interface
    private static Object[][] supers() {
        return new Object[][]{
            // invoke "m" implemented in the first proxy interface
            // same as the method passed to InvocationHandler::invoke
            new Object[]{new Class<?>[]{I1.class}, I1.class, 10},
            new Object[]{new Class<?>[]{I2.class}, I2.class, 20},
            new Object[]{new Class<?>[]{I1.class, I2.class}, I1.class, 10},
            // "m" is implemented in I2, an indirect superinterface of I3
            new Object[]{new Class<?>[]{I3.class}, I3.class, 20},
            // "m" is implemented in I1, I2 and overridden in I4
            new Object[]{new Class<?>[]{I4.class}, I4.class, 40},
            // invoke "m" implemented in the second proxy interface
            // different from the method passed to InvocationHandler::invoke
            new Object[]{new Class<?>[]{I1.class, I2.class}, I2.class, 20},
            new Object[]{new Class<?>[]{I1.class, I3.class}, I3.class, 20},
            // I2::m is implemented in more than one proxy interface directly or indirectly
            // I3::m resolves to I2::m (indirect superinterface)
            // I2 is the superinterface of I4 and I4 overrides m
            // the proxy class can invoke I4::m and I2::m
            new Object[]{new Class<?>[]{I3.class, I4.class}, I3.class, 20},
            new Object[]{new Class<?>[]{I3.class, I4.class}, I4.class, 40},
            new Object[]{new Class<?>[]{I4.class, I3.class}, I3.class, 20},
            new Object[]{new Class<?>[]{I4.class, I3.class}, I4.class, 40}
        };
    }

    @ParameterizedTest
    @MethodSource("supers")
    public void testSuper(Class<?>[] intfs, Class<?> proxyInterface, int expected) throws Throwable {
        final InvocationHandler ih = (proxy, method, params) -> {
            switch (method.getName()) {
                case "m":
                    assertTrue(method.isDefault());
                    return InvocationHandler.invokeDefault(proxy, findDefaultMethod(proxyInterface, method), params);
                default:
                    throw new UnsupportedOperationException(method.toString());
            }
        };
        ClassLoader loader = proxyInterface.getClassLoader();
        Object proxy = Proxy.newProxyInstance(loader, intfs, ih);
        if (proxyInterface == I1.class) {
            I1 i1 = (I1) proxy;
            assertEquals(expected, i1.m());
        } else if (proxyInterface == I2.class) {
            I2 i2 = (I2) proxy;
            assertEquals(expected, i2.m());
        } else if (proxyInterface == I3.class) {
            I3 i3 = (I3) proxy;
            assertEquals(expected, i3.m());
        } else if (proxyInterface == I4.class) {
            I4 i4 = (I4) proxy;
            assertEquals(expected, i4.m());
        } else {
            throw new UnsupportedOperationException(proxyInterface.toString());
        }
        // invoke via InvocationHandler.invokeDefaultMethod directly
        assertEquals(expected, InvocationHandler.invokeDefault(proxy, proxyInterface.getMethod("m")));
    }

    // invoke I12 default methods with parameters and var args
    @Test
    public void testI12() throws Throwable {
        final InvocationHandler ih = (proxy, method, params) -> {
            System.out.format("invoking %s with parameters: %s%n", method, Arrays.toString(params));
            switch (method.getName()) {
                case "sum":
                case "concat":
                    assertTrue(method.isDefault());
                    return InvocationHandler.invokeDefault(proxy, method, params);
                default:
                    throw new UnsupportedOperationException(method.toString());
            }
        };
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        I12 i12 = (I12) Proxy.newProxyInstance(loader, new Class<?>[] { I12.class }, ih);
        assertEquals(3, i12.sum(1, 2));
        assertArrayEquals(new Object[]{1, 2, 3, 4}, i12.concat(1, 2, 3, 4));
        Method m = I12.class.getMethod("concat", Object.class, Object[].class);
        assertTrue(m.isDefault());
        assertArrayEquals(new Object[] {100, "foo", true, "bar"}, (Object[])
                InvocationHandler.invokeDefault(i12, m, 100, new Object[] {"foo", true, "bar"}));
    }

    // test a no-arg default method with and without arguments passed in the invocation
    @Test
    public void testEmptyArgument() throws Throwable {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{I4.class}, HANDLER);
        Method m1 = I4.class.getMethod("m");
        assertSame(I4.class, m1.getDeclaringClass());
        assertTrue(m1.isDefault());
        InvocationHandler.invokeDefault(proxy, m1);
        InvocationHandler.invokeDefault(proxy, m1, new Object[0]);

        Method m2 = I4.class.getMethod("mix", int.class, String.class);
        assertSame(I4.class, m1.getDeclaringClass());
        assertTrue(m1.isDefault());
        InvocationHandler.invokeDefault(proxy, m2, Integer.valueOf(100), "foo");
    }

    @Test
    public void testVarArgs() throws Throwable {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        I3 proxy = (I3)Proxy.newProxyInstance(loader, new Class<?>[]{I3.class}, HANDLER);
        Method m = I3.class.getMethod("m3", String[].class);
        assertTrue(m.isVarArgs() && m.isDefault());
        assertEquals(5, proxy.m3("a", "b", "cde"));
        assertEquals(3, InvocationHandler.invokeDefault(proxy, m, (Object)new String[] { "a", "bc" }));
    }

    /*
     * Invoke I12::m which is an abstract method
     */
    @Test
    public void invokeAbstractMethod() throws Exception {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        I12 proxy = (I12) Proxy.newProxyInstance(loader, new Class<?>[]{I12.class}, HANDLER);
        Method method = I12.class.getMethod("m");
        assertSame(I12.class, method.getDeclaringClass());
        assertFalse(method.isDefault());
        assertThrows(IllegalArgumentException.class, () -> proxy.m());
    }

    /*
     * Invoke a non proxy (default) method with parameters
     */
    @Test
    public void invokeNonProxyMethod() throws Throwable {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        I3 proxy = (I3) Proxy.newProxyInstance(loader, new Class<?>[]{I3.class}, HANDLER);
        Method m = I4.class.getMethod("mix", int.class, String.class);
        assertTrue(m.isDefault());
        assertThrows(IllegalArgumentException.class, () -> InvocationHandler.invokeDefault(proxy, m));
    }

    // negative cases
    private static Object[][] negativeCases() {
        return new Object[][]{
            // I4::m overrides I1::m and I2::m
            new Object[] { new Class<?>[]{I4.class}, I1.class, "m" },
            new Object[] { new Class<?>[]{I4.class}, I2.class, "m" },
            // I12::m is not a default method
            new Object[] { new Class<?>[]{I12.class}, I12.class, "m" },
            // non-proxy default method
            new Object[] { new Class<?>[]{I3.class}, I1.class, "m" },
            // not a default method and not a proxy interface
            new Object[] { new Class<?>[]{I12.class}, DefaultMethods.class, "test" },
            new Object[] { new Class<?>[]{I12.class}, Runnable.class, "run" },
            // I2::privateMethod is a private method
            new Object[] { new Class<?>[]{I3.class}, I2.class, "privateMethod" }
        };
    }

    @ParameterizedTest
    @MethodSource("negativeCases")
    public void testNegativeCase(Class<?>[] interfaces, Class<?> defc, String name)
            throws Throwable {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        Object proxy = Proxy.newProxyInstance(loader, interfaces, HANDLER);
        Method method = defc.getDeclaredMethod(name);
        assertThrows(IllegalArgumentException.class, () ->
                InvocationHandler.invokeDefault(proxy, method));
    }

    private static Object[] illegalArguments() {
        return new Object[] {
            new Object[] { null },
            new Object[] { new Object[] { 100 }},
            new Object[] { new Object[] { 100, "foo", 100 }},
            new Object[] { new Object[] { 100L, "foo" }},
            new Object[] { new Object[] { "foo", 100 }},
            new Object[] { new Object[] { null, "foo" }}
        };
    }

    @ParameterizedTest
    @MethodSource("illegalArguments")
    public void testIllegalArgument(Object[] args) throws Throwable {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        I4 proxy = (I4)Proxy.newProxyInstance(loader, new Class<?>[]{I4.class}, HANDLER);
        Method m = I4.class.getMethod("mix", int.class, String.class);
        assertTrue(m.isDefault());
        assertThrows(IllegalArgumentException.class, () ->
                InvocationHandler.invokeDefault(proxy, m, args));
    }

    private static Object[][] throwables() {
        return new Object[][] {
            new Object[] { new IOException() },
            new Object[] { new IllegalArgumentException() },
            new Object[] { new ClassCastException() },
            new Object[] { new NullPointerException() },
            new Object[] { new AssertionError() },
            new Object[] { new Throwable() }
        };
    }

    @ParameterizedTest
    @MethodSource("throwables")
    public void testInvocationException(Throwable exception) throws Throwable {
        ClassLoader loader = DefaultMethods.class.getClassLoader();
        IX proxy = (IX)Proxy.newProxyInstance(loader, new Class<?>[]{IX.class}, HANDLER);
        Method m = IX.class.getMethod("doThrow", Throwable.class);
        assertSame(exception, assertThrows(Throwable.class, () ->
                InvocationHandler.invokeDefault(proxy, m, exception)));
    }

    private static final InvocationHandler HANDLER = (proxy, method, params) -> {
        System.out.format("invoking %s with parameters: %s%n", method, Arrays.toString(params));
        return InvocationHandler.invokeDefault(proxy, method, params);
    };
}
