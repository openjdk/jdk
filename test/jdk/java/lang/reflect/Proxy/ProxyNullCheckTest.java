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

/*
 * @test
 * @bug 8371953
 * @summary Basic API null checks for Proxy.
 * @run junit ProxyNullCheckTest
 */

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProxyNullCheckTest {
    @SuppressWarnings("deprecation")
    @Test
    void nullChecks() throws ReflectiveOperationException {
        InvocationHandler h = (_, _, _) -> null;
        // newProxyInstance
        assertDoesNotThrow(() -> Proxy.newProxyInstance(null, new Class[0], h));
        assertThrows(NullPointerException.class, () -> Proxy.newProxyInstance(null, null, h));
        assertThrows(NullPointerException.class, () -> Proxy.newProxyInstance(null, new Class[] { null }, h));
        assertThrows(NullPointerException.class, () -> Proxy.newProxyInstance(null, new Class[0], null));
        // getProxyClass
        assertDoesNotThrow(() -> Proxy.getProxyClass(null, new Class[0]));
        assertThrows(NullPointerException.class, () -> Proxy.getProxyClass(null, (Class[]) null));
        assertThrows(NullPointerException.class, () -> Proxy.getProxyClass(null, new Class[] { null }));
        // isProxyClass
        assertFalse(Proxy.isProxyClass(Object.class));
        assertThrows(NullPointerException.class, () -> Proxy.isProxyClass(null));
        // getInvocationHandler
        assertThrows(NullPointerException.class, () -> Proxy.getInvocationHandler(null));
        assertThrows(IllegalArgumentException.class, () -> Proxy.getInvocationHandler(42));
    }
}
