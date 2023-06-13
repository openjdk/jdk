/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package p1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.stream.Collectors;

import p2.TestIntf;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class Main {
    public interface A {
        default String aConcat(Object... objs) { return Arrays.deepToString(objs); }
    }

    public interface B {
        default String bConcat(Object[] objs) { return Arrays.deepToString(objs); }
    }

    public interface C extends A, B {
        String c(Object... objs);
    }

    private static String concat(Object... objs) {
        return Arrays.stream(objs).map(Object::toString).collect(Collectors.joining());
    }

    /*
     * Test the invocation of default methods with varargs
     */
    @Test
    public static void testVarargsMethods() throws Throwable {
        MethodHandle target = MethodHandles.lookup().findStatic(Main.class,
                "concat", MethodType.methodType(String.class, Object[].class));
        C proxy = MethodHandleProxies.asInterfaceInstance(C.class, target);

        assertEquals(proxy.c("a", "b", "c"), "abc");
        assertEquals(proxy.aConcat("a", "b", "c"), "[a, b, c]");
        assertEquals(proxy.aConcat(new Object[] { "a", "b", "c" }), "[a, b, c]");
        assertEquals(proxy.bConcat(new Object[] { "a", "b", "c" }), "[a, b, c]");
    }

    /*
     * Test the invocation of a default method of an accessible interface
     */
    @Test
    public static void modulePrivateInterface() {
        MethodHandle target = MethodHandles.constant(String.class, "test");
        TestIntf t = MethodHandleProxies.asInterfaceInstance(TestIntf.class, target);
        assertEquals(t.test(), "test");
    }
}
