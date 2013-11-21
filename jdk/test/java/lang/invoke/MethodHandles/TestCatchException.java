/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/**
 * @test
 * @bug 8027823
 * @run junit test.java.lang.invoke.TestCatchException
 */
package test.java.lang.invoke;

import java.lang.invoke.*;
import org.junit.*;
import static org.junit.Assert.*;

public class TestCatchException {
    final static MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    final static MethodType M_TYPE = MethodType.methodType(int.class, Object.class, Object.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class);

    private static int noThrow(Object o1, Object o2, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
        return 42;
    }

    private static int throwEx(Object o1, Object o2, int i1, int i2, int i3, int i4, int i5, int i6, int i7) throws Exception {
        throw new Exception();
    }

    private static int handler(Exception e) {
        return 17;
    }

    @Test
    public void testNoThrowPath() throws Throwable {
        MethodHandle target = LOOKUP.findStatic(TestCatchException.class, "noThrow", M_TYPE);
        MethodHandle handler = LOOKUP.findStatic(TestCatchException.class, "handler", MethodType.methodType(int.class, Exception.class));

        MethodHandle h = MethodHandles.catchException(target, Exception.class, handler);

        int x = (int)h.invokeExact(new Object(), new Object(), 1, 2, 3, 4, 5, 6, 7);
        assertEquals(x, 42);
    }

    @Test
    public void testThrowPath() throws Throwable {
        MethodHandle target = LOOKUP.findStatic(TestCatchException.class, "throwEx", M_TYPE);
        MethodHandle handler = LOOKUP.findStatic(TestCatchException.class, "handler", MethodType.methodType(int.class, Exception.class));

        MethodHandle h = MethodHandles.catchException(target, Exception.class, handler);

        int x = (int)h.invokeExact(new Object(), new Object(), 1, 2, 3, 4, 5, 6, 7);
        assertEquals(x, 17);
    }

    public static void main(String[] args) throws Throwable {
        TestCatchException test = new TestCatchException();
        test.testNoThrowPath();
        test.testThrowPath();
        System.out.println("TEST PASSED");
    }
}
