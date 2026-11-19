/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8333854 8370839
 * @summary Behavior of methods whose signature has package-private
 *          class or interfaces but the proxy interface is public
 * @run junit NonPublicMethodTypeTest
 */
public class NonPublicMethodTypeTest {
    // Java language and JVM allow using fields and methods with inaccessible
    // classes or interfaces in its signature, as long as the field or method
    // is accessible and its declaring class or interface is accessible.
    // Such inaccessible classes and interfaces are treated as if an arbitrary
    // subtype of their accessible types, or an arbitrary supertype of their
    // accessible subtypes.
    // java.lang.invoke is stricter - MethodType constant pool entry resolution
    // for such signatures fail, so they can't be used for MethodHandle or indy.
    enum Internal { INSTANCE }

    public interface InternalParameter {
        void call(Internal parameter);
    }

    @Test
    void testNonPublicParameter() throws Throwable {
        // Creation should be successful
        // 8333854 - BSM usage fails for looking up such methods
        InternalParameter instance = (InternalParameter) Proxy.newProxyInstance(
                InternalParameter.class.getClassLoader(),
                new Class[] { InternalParameter.class },
                (_, _, _) -> null);
        assertNotSame(Internal.class.getPackage(),
                instance.getClass().getPackage(),
                "Proxy class should not be able to access method parameter " +
                        "Internal class's package");
        // Calls should be always successful
        instance.call(null);
        instance.call(Internal.INSTANCE);
    }

    public interface InternalReturn {
        Internal call();
    }

    @Test
    void testNonPublicReturn() throws Throwable {
        AtomicReference<Internal> returnValue = new AtomicReference<>();
        // Creation should be successful
        // A lot of annotation interfaces are implemented by such proxy classes,
        // due to presence of package-private annotation interface or enum-typed
        // elements in public annotation interfaces.
        InternalReturn instance = (InternalReturn) Proxy.newProxyInstance(
                InternalReturn.class.getClassLoader(),
                new Class[] { InternalReturn.class },
                (_, _, _) -> returnValue.get());
        assertNotSame(Internal.class.getPackage(),
                instance.getClass().getPackage(),
                "Proxy class should not be able to access method parameter " +
                        "Internal class's package");

        // The generated call() implementation is as follows:
        // aload0, getfield Proxy.h, aload 0, getstatic (Method), aconst_null,
        // invokevirtual InvocationHandler::invoke(Object, Method, Object[])Object,
        // checkcast Internal.class, areturn
        // In this bytecode, checkcast Internal.class will fail with a
        // IllegalAccessError as a result of resolution of Internal.class
        // if and only if the incoming reference is non-null.

        // checkcast does not perform access check for null
        returnValue.set(null);
        instance.call();
        // checkcast fails - proxy class cannot access the return type
        // See JDK-8349716
        returnValue.set(Internal.INSTANCE);
        assertThrows(IllegalAccessError.class, instance::call);
    }
}
