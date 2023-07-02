/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

/*
 * @test
 * @bug 6983726
 * @summary Ensures MethodHandleProxies creates a dynamic module for each interface instance
 * @run junit ProxyForMethodHandleTest
 */
public class ProxyForMethodHandleTest {
    /**
     * MethodHandleProxies will create a dynamic module for the proxy
     */
    @Test
    public void testRunnableMethodHandle() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mt = MethodType.methodType(void.class);
        MethodHandle mh = lookup.findStatic(ProxyForMethodHandleTest.class, "runForRunnable", mt);
        Runnable proxy = MethodHandleProxies.asInterfaceInstance(Runnable.class, mh);
        proxy.run();

        Class<?> proxyClass = proxy.getClass();
        Module target = proxyClass.getModule();
        assertDynamicModule(target, proxyClass.getClassLoader(), proxyClass);
    }

    static void runForRunnable() {
        System.out.println("runForRunnable");
    }

    public static void assertDynamicModule(Module m, ClassLoader ld, Class<?> proxyClass) {
        if (!m.isNamed() || !m.getName().startsWith("jdk.MHProxy")) {
            throw new RuntimeException(m.getName() + " not dynamic module");
        }

        if (ld != m.getClassLoader() || proxyClass.getClassLoader() != ld) {
            throw new RuntimeException("unexpected class loader");
        }

        try {
            Constructor<?> cons = proxyClass.getDeclaredConstructor(MethodHandles.Lookup.class,
                    MethodHandle.class, MethodHandle.class);
            cons.newInstance(MethodHandles.lookup(), MethodHandles.zero(int.class), MethodHandles.zero(int.class));
            throw new RuntimeException("Expected IllegalAccessException: " + proxyClass);
        } catch (IllegalAccessException e) {
            // expected
        } catch (NoSuchMethodException|InstantiationException|InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}