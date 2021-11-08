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

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Hidden;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * MethodAccessor adapter for caller-sensitive methods which have
 * an alternate non-CSM method with the same method name but an additional
 * caller class argument.
 *
 * When a caller-sensitive method is called,
 * Method::invoke(Object target, Object[] args, Class<?> caller) will
 * be invoked with the caller class.  If an adapter is present,
 * the adapter method with the caller class parameter will be called
 * instead.
 */
class CsMethodAccessorAdapter extends MethodAccessorImpl {
    private final Method csmAdapter;
    private final MethodAccessor accessor;

    CsMethodAccessorAdapter(Method method, Method csmAdapter, MethodAccessor accessor) {
        assert Reflection.isCallerSensitive(method) && !Reflection.isCallerSensitive(csmAdapter);
        this.csmAdapter = csmAdapter;
        this.accessor = accessor;
    }

    @Override
    public Object invoke(Object obj, Object[] args)
            throws IllegalArgumentException, InvocationTargetException {
        throw new InternalError("caller-sensitive method invoked without explicit caller: " + csmAdapter);
    }

    @Override
    @ForceInline
    @Hidden
    public Object invoke(Object obj, Object[] args, Class<?> caller)
            throws IllegalArgumentException, InvocationTargetException {
        Object[] newArgs = new Object[args == null ? 1 : args.length + 1];
        newArgs[0] = caller;
        if (args != null) {
            System.arraycopy(args, 0, newArgs, 1, args.length);
        }
        return accessor.invoke(obj, newArgs);
    }
}
