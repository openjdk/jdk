/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Test MethodHandle::invokeExact and MethodHandle::invoke throws
 *          UnsupportedOperationException when called via Method::invoke
 * @run testng test.java.lang.invoke.MethodHandleInvokeUOE
 */

package test.java.lang.invoke;

import org.testng.*;
import org.testng.annotations.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.lang.invoke.MethodType.*;

public class MethodHandleInvokeUOE {
    @Test
    public void testInvokeExact() throws Throwable {
        Class<?> clz = MethodHandle.class;
        String mname = "invokeExact";
        Method m = clz.getDeclaredMethod(mname, Object[].class);
        MethodHandle mh = MethodHandles.lookup().findVirtual(clz, mname, methodType(Object.class, Object[].class));
        try {
            m.invoke(mh, new Object[1]);
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof UnsupportedOperationException)) {
                throw new RuntimeException("expected UnsupportedOperationException but got: "
                        + e.getCause().getClass().getName(), e);
            }
        }
    }

    @Test
    public void testInvoke() throws Throwable {
        Class<?> clz = MethodHandle.class;
        String mname = "invoke";
        Method m = clz.getDeclaredMethod(mname, Object[].class);
        MethodHandle mh = MethodHandles.lookup().findVirtual(clz, mname, methodType(Object.class, Object[].class));
        try {
            m.invoke(mh, new Object[1]);
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof UnsupportedOperationException)) {
                throw new RuntimeException("expected UnsupportedOperationException but got: "
                        + e.getCause().getClass().getName(), e);
            }
        }
    }
}
