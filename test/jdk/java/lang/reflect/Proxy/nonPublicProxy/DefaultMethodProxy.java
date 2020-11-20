/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8159746
 * @summary Test invoking a default method in a non-public proxy interface
 * @build p.Foo p.Bar p.DefaultMethodInvoker
 * @run testng DefaultMethodProxy
 */
public class DefaultMethodProxy {
    public interface I {
        default String m() { return "I"; }
    }

    @Test
    public static void hasPackageAccess() throws Exception {
        Class<?> fooClass = Class.forName("p.Foo");
        Class<?> barClass = Class.forName("p.Bar");

        // create a proxy instance of a non-public proxy interface
        makeProxy(IH, fooClass).testDefaultMethod("foo");
        makeProxy(IH, barClass, fooClass).testDefaultMethod("bar");

        // create a proxy instance of a public proxy interface should succeed
        makeProxy(IH, I.class).testDefaultMethod("I");
    }

    @DataProvider(name = "nonPublicIntfs")
    private static Object[][] nonPublicIntfs() throws ClassNotFoundException {
        Class<?> fooClass = Class.forName("p.Foo");
        Class<?> barClass = Class.forName("p.Bar");
        return new Object[][]{
                new Object[]{new Class<?>[]{ fooClass }},
                new Object[]{new Class<?>[]{ barClass }},
                new Object[]{new Class<?>[]{ barClass, fooClass }},
        };
    }

    @Test(dataProvider = "nonPublicIntfs")
    public static void noPackageAccess(Class<?>[] intfs) throws Exception {
        makeProxy(IH_NO_ACCESS, intfs).testDefaultMethod("dummy");
    }

    final Object proxy;
    DefaultMethodProxy(Object proxy) {
        this.proxy = proxy;
    }

    /*
     * Verify if a default method "m" can be invoked successfully
     */
    void testDefaultMethod(String expected) throws ReflectiveOperationException {
        Method m = proxy.getClass().getDeclaredMethod("m");
        m.setAccessible(true);
        String name = (String)m.invoke(proxy);
        if (!expected.equals(name)) {
            throw new RuntimeException("return value: " + name + " expected: " + expected);
        }
    }

    // invocation handler with access to the non-public interface in package p
    private static final InvocationHandler IH = (proxy, method, params) -> {
        System.out.format("Proxy for %s: invoking %s%n",
                Arrays.stream(proxy.getClass().getInterfaces())
                      .map(Class::getName)
                      .collect(Collectors.joining(", ")), method.getName());
        if (method.isDefault()) {
            return p.DefaultMethodInvoker.invoke(proxy, method, params);
        }
        throw new UnsupportedOperationException(method.toString());
    };

    // invocation handler with no access to the non-public interface in package p
    // expect IllegalAccessException thrown
    private static final InvocationHandler IH_NO_ACCESS = (proxy, method, params) -> {
        System.out.format("Proxy for %s: invoking %s%n",
                Arrays.stream(proxy.getClass().getInterfaces())
                        .map(Class::getName)
                        .collect(Collectors.joining(", ")), method.getName());
        if (method.isDefault()) {
            try {
                InvocationHandler.invokeDefault(proxy, method, params);
                throw new RuntimeException("IAE not thrown in invoking: " + method);
            } catch (IllegalAccessException e) {
                return "dummy";
            }
        }
        throw new UnsupportedOperationException(method.toString());
    };

    private static DefaultMethodProxy makeProxy(InvocationHandler ih, Class<?>... intfs) {
        Object proxy = Proxy.newProxyInstance(DefaultMethodProxy.class.getClassLoader(), intfs, ih);
        return new DefaultMethodProxy(proxy);
    }
}
