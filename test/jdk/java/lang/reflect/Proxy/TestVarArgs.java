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

/*
 * @test
 * @bug 8022795
 * @run junit TestVarArgs
 * @summary Verify if a method defined in a proxy interface has ACC_VARARGS set
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class TestVarArgs {
    interface I {
        Object call(Object... values);
    }
    interface J {
        Object call(Object[] values);
    }

    public static Object[][] proxyInterfaces() {
        return new Object[][] {
             { new Class<?>[] { I.class }, true},
             { new Class<?>[] { J.class }, false},
             { new Class<?>[] { I.class, J.class }, true},
             { new Class<?>[] { J.class, I.class }, false},
        };
    }

    @ParameterizedTest
    @MethodSource("proxyInterfaces")
    public void testMethod(Class<?>[] proxyInterfaces, boolean isVarArgs) throws Throwable {
        // check if the first proxy interface with the method named "call" declares var result
        Method m = proxyInterfaces[0].getMethod("call", Object[].class);
        assertEquals(isVarArgs, m.isVarArgs());

        // the method in the generated proxy class should match the method
        // declared in the proxy interface
        Object proxy = Proxy.newProxyInstance(TestVarArgs.class.getClassLoader(),
                                              proxyInterfaces, IH);
        Class<?> proxyClass = proxy.getClass();
        assertTrue(Proxy.isProxyClass(proxyClass));
        Method method = proxyClass.getMethod("call", Object[].class);
        assertEquals(isVarArgs, method.isVarArgs());

        Object[] params = new Object[] { "foo", "bar", "goo" };
        Object[] result;

        // test reflection
        result = (Object[]) method.invoke(proxy, new Object[] {params});
        assertEquals(params, result);

        // test method handle
        MethodHandle mh = MethodHandles.lookup().findVirtual(proxyClass, "call",
                      MethodType.methodType(Object.class, Object[].class));
        assertEquals(isVarArgs, mh.isVarargsCollector());
        MethodHandle mhVarArity = mh;
        MethodHandle mhFixedArity = mh;
        if (isVarArgs) {
            mhFixedArity = mh.asFixedArity();
        } else {
            mhVarArity = mh.asVarargsCollector(Object[].class);
        }
        result = (Object[]) mhVarArity.invoke(proxy, "foo", "bar", "goo");
        assertArrayEquals(params, result);

        result = (Object[]) mhFixedArity.invoke(proxy, params);
        assertArrayEquals(params, result);

        if (!isVarArgs) {
            MethodType mt = MethodType.methodType(Object.class, Object.class, String.class, String.class, String.class);
            mh = mh.asVarargsCollector(Object[].class).asType(mt);
        }
        result = (Object[]) mh.invoke(proxy, "foo", "bar", "goo");
        assertArrayEquals(params, result);
    }

    private static final InvocationHandler IH = new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return args[0];
        }
    };

}
