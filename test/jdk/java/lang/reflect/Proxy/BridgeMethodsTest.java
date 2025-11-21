/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8370839
 * @summary Behavior of bridge methods in interfaces
 * @run junit BridgeMethodsTest
 */
public class BridgeMethodsTest {

    interface StringCallable extends Callable<String> {
        @Override
        String call(); // throws no exception
    }

    @Test
    void testExceptionTypes() throws Throwable {
        class MyException extends Exception {}
        // This proxy has two distinct methods, even though the
        // Java language would treat the first one as overridden:
        // Object call() throws Exception;  - from Callable
        // String call();                   - from StringCallable
        var instance = Proxy.newProxyInstance(StringCallable.class.getClassLoader(),
                new Class[] { StringCallable.class }, (_, _, _) -> { throw new MyException(); });
        // The exception can't be thrown through StringCallable.call which has no throws
        var undeclared = assertThrows(UndeclaredThrowableException.class, () -> ((StringCallable) instance).call());
        assertInstanceOf(MyException.class, undeclared.getCause());
        // But it can be thrown through Callable.call which permits Exception
        assertThrows(MyException.class, () -> ((Callable<?>) instance).call());
    }

    interface SpecificConsumer extends Consumer<String> {
        @Override
        void accept(String s);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMethodObjects() throws Throwable {
        List<Method> methods = new ArrayList<>();
        // This proxy has two distinct methods, even though the
        // Java language would treat the first one as overridden:
        // void accept(Object);    - from Consumer
        // void accept(String);    - from SpecificConsumer
        var instance = Proxy.newProxyInstance(SpecificConsumer.class.getClassLoader(),
                new Class[] { SpecificConsumer.class }, (_, m, _) -> methods.add(m));
        ((Consumer<Object>) instance).accept(null);
        ((SpecificConsumer) instance).accept(null);
        assertEquals(2, methods.size());
        // invocation handler gets different method due to covariant parameter types
        assertNotEquals(methods.getFirst(), methods.getLast());
    }
}
