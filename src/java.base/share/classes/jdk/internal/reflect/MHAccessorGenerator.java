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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;

import static java.lang.invoke.MethodType.methodType;

class MHAccessorGenerator {

    private static final MethodType METHOD_MH_TYPE =
            methodType(Object.class, Object.class, Object[].class);
    private static final MethodType CONSTRUCTOR_MH_TYPE =
            methodType(Object.class, Object[].class);
    private static final MethodHandle WRAP_AND_RETHROW;


    private static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();

    static {
        try {
            Lookup l = MethodHandles.publicLookup();
            MethodHandle wrap = l.findConstructor(InvocationTargetException.class,
                            methodType(void.class, Throwable.class));
            MethodHandle thrower = MethodHandles.throwException(Object.class, InvocationTargetException.class);
            WRAP_AND_RETHROW = MethodHandles.filterArguments(thrower, 0, wrap);
        } catch (ReflectiveOperationException roe) {
            throw new InternalError(roe);
        }
    }

    public static MethodAccessorImpl generateMethod(Method m) {
        try {
            MethodHandle target = JLIA.unreflectMethod(m)
                    .asFixedArity();
            if (Modifier.isStatic(m.getModifiers())) {
                target = MethodHandles.dropArguments(target, 0, Object.class);
            }
            target = target.asType(target.type().changeReturnType(Object.class));
            target = MethodHandles.catchException(target, Throwable.class, WRAP_AND_RETHROW)
                .asSpreader(1, Object[].class, target.type().parameterCount() - 1)
                .asType(METHOD_MH_TYPE);
            return new MHMethodAccessor(m.getDeclaringClass(), target, m.getModifiers());
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    public static ConstructorAccessor generateConstructor(Constructor<?> c) {
        try {
            return commonConstructorAccessor(c.getDeclaringClass(), JLIA.unreflectConstructor(c));
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    public static ConstructorAccessor generateSerializationConstructor(Constructor<?> c, Class<?> instantiatedClass) {
        try {
            return commonConstructorAccessor(instantiatedClass, JLIA.unreflectConstructorForSerialization(c, instantiatedClass));
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    public static ConstructorAccessor commonConstructorAccessor(Class<?> cl, MethodHandle target) {
        target = target.asFixedArity();
        target = target.asType(target.type().changeReturnType(Object.class));
        target = MethodHandles.catchException(target, Throwable.class, WRAP_AND_RETHROW)
                .asSpreader(Object[].class, target.type().parameterCount())
                .asType(CONSTRUCTOR_MH_TYPE);
        return new MHConstructorAccessor(target, cl);
    }

}
