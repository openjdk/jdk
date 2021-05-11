/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.reflect;

import jdk.internal.vm.annotation.Hidden;

import java.lang.invoke.MethodHandle;

/**
 * Delegate the invocation directly to the target method handle.
 */
final class MHMethodAccessorDelegate implements MHMethodAccessor {
    private final MethodHandle target;
    MHMethodAccessorDelegate(MethodHandle target) {
        this.target = target;
    }
    // non-specialized non-static and static methods
    @Hidden @Override public Object invoke(Object obj, Object[] args) throws Throwable {
        return target.invokeExact(obj, args);
    }
    @Hidden @Override public Object invoke(Object[] args) throws Throwable {
        return target.invokeExact(args);
    }
    @Hidden @Override public Object invoke(Object obj, Object[] args, Class<?> caller) throws Throwable {
        return target.invokeExact(obj, args, caller);
    }
    @Hidden @Override public Object invoke(Object[] args, Class<?> caller) throws Throwable {
        return target.invokeExact(args, caller);
    }

    // specialized version for instance method
    @Hidden @Override public Object invoke(Object obj) throws Throwable {
        return target.invokeExact(obj);
    }
    @Hidden @Override public Object invoke(Object obj, Object arg1) throws Throwable {
        return target.invokeExact(obj, arg1);
    }
    @Hidden @Override public Object invoke(Object obj, Object arg1, Object arg2) throws Throwable {
        return target.invokeExact(obj, arg1, arg2);
    }
    @Hidden @Override public Object invoke(Object obj, Object arg1, Object arg2, Object arg3) throws Throwable {
        return target.invokeExact(obj, arg1, arg2, arg3);
    }
    @Hidden @Override public Object invoke(Object obj, Class<?> caller) throws Throwable {
        return target.invokeExact(obj, caller);
    }
    @Hidden @Override public Object invoke(Object obj, Object arg1, Class<?> caller) throws Throwable {
        return target.invokeExact(obj, arg1, caller);
    }
    @Hidden @Override public Object invoke(Object obj, Object arg1, Object arg2, Class<?> caller) throws Throwable {
        return target.invokeExact(obj, arg1, arg2, caller);
    }
    @Hidden @Override public Object invoke(Object obj, Object arg1, Object arg2, Object arg3, Class<?> caller) throws Throwable {
        return target.invokeExact(obj, arg1, arg2, arg3, caller);
    }

    // specialized version for static method
    @Hidden @Override public Object invoke() throws Throwable {
        return target.invokeExact();
    }
    @Hidden @Override public Object invoke(Class<?> caller) throws Throwable {
        return target.invokeExact(caller);
    }

    // the method of the same signature already defined
//    @Hidden @Override public Object invoke(Object arg1) throws Throwable {
//        return target.invokeExact();
//    }
//    @Hidden @Override public Object invoke(Object arg1, Object arg2) throws Throwable {
//        return target.invokeExact();
//    }
//    @Hidden @Override public Object invoke(Object arg1, Class<?> caller) throws Throwable {
//        return target.invokeExact(arg1, caller);
//    }
//    @Hidden @Override public Object invoke(Object arg1, Object arg2, Class<?> caller) throws Throwable {
//        return target.invokeExact(arg1, arg2, caller);
//    }
//    @Hidden @Override public Object invoke(Object arg1, Object arg2, Object arg3, Class<?> caller) throws Throwable {
//        return target.invokeExact(arg1, arg2, arg3, caller);
//    }
}
