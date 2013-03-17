/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.beans.finder;

import com.sun.beans.TypeResolver;
import com.sun.beans.WeakCache;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

import static sun.reflect.misc.ReflectUtil.isPackageAccessible;

/**
 * This utility class provides {@code static} methods
 * to find a public method with specified name and parameter types
 * in specified class.
 *
 * @since 1.7
 *
 * @author Sergey A. Malenkov
 */
public final class MethodFinder extends AbstractFinder<Method> {
    private static final WeakCache<Signature, Method> CACHE = new WeakCache<Signature, Method>();

    /**
     * Finds public method (static or non-static)
     * that is accessible from public class.
     *
     * @param type  the class that can have method
     * @param name  the name of method to find
     * @param args  parameter types that is used to find method
     * @return object that represents found method
     * @throws NoSuchMethodException if method could not be found
     *                               or some methods are found
     */
    public static Method findMethod(Class<?> type, String name, Class<?>...args) throws NoSuchMethodException {
        if (name == null) {
            throw new IllegalArgumentException("Method name is not set");
        }
        PrimitiveWrapperMap.replacePrimitivesWithWrappers(args);
        Signature signature = new Signature(type, name, args);

        Method method = CACHE.get(signature);
        boolean cached = method != null;
        if (cached && isPackageAccessible(method.getDeclaringClass())) {
            return method;
        }
        method = findAccessibleMethod(new MethodFinder(name, args).find(type.getMethods()));
        if (!cached) {
            CACHE.put(signature, method);
        }
        return method;
    }

    /**
     * Finds public non-static method
     * that is accessible from public class.
     *
     * @param type  the class that can have method
     * @param name  the name of method to find
     * @param args  parameter types that is used to find method
     * @return object that represents found method
     * @throws NoSuchMethodException if method could not be found
     *                               or some methods are found
     */
    public static Method findInstanceMethod(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        Method method = findMethod(type, name, args);
        if (Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException("Method '" + name + "' is static");
        }
        return method;
    }

    /**
     * Finds public static method
     * that is accessible from public class.
     *
     * @param type  the class that can have method
     * @param name  the name of method to find
     * @param args  parameter types that is used to find method
     * @return object that represents found method
     * @throws NoSuchMethodException if method could not be found
     *                               or some methods are found
     */
    public static Method findStaticMethod(Class<?> type, String name, Class<?>...args) throws NoSuchMethodException {
        Method method = findMethod(type, name, args);
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException("Method '" + name + "' is not static");
        }
        return method;
    }

    /**
     * Finds method that is accessible from public class or interface through class hierarchy.
     *
     * @param method  object that represents found method
     * @return object that represents accessible method
     * @throws NoSuchMethodException if method is not accessible or is not found
     *                               in specified superclass or interface
     */
    public static Method findAccessibleMethod(Method method) throws NoSuchMethodException {
        Class<?> type = method.getDeclaringClass();
        if (Modifier.isPublic(type.getModifiers()) && isPackageAccessible(type)) {
            return method;
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException("Method '" + method.getName() + "' is not accessible");
        }
        for (Type generic : type.getGenericInterfaces()) {
            try {
                return findAccessibleMethod(method, generic);
            }
            catch (NoSuchMethodException exception) {
                // try to find in superclass or another interface
            }
        }
        return findAccessibleMethod(method, type.getGenericSuperclass());
    }

    /**
     * Finds method that accessible from specified class.
     *
     * @param method  object that represents found method
     * @param generic generic type that is used to find accessible method
     * @return object that represents accessible method
     * @throws NoSuchMethodException if method is not accessible or is not found
     *                               in specified superclass or interface
     */
    private static Method findAccessibleMethod(Method method, Type generic) throws NoSuchMethodException {
        String name = method.getName();
        Class<?>[] params = method.getParameterTypes();
        if (generic instanceof Class) {
            Class<?> type = (Class<?>) generic;
            return findAccessibleMethod(type.getMethod(name, params));
        }
        if (generic instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) generic;
            Class<?> type = (Class<?>) pt.getRawType();
            for (Method m : type.getMethods()) {
                if (m.getName().equals(name)) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == params.length) {
                        if (Arrays.equals(params, pts)) {
                            return findAccessibleMethod(m);
                        }
                        Type[] gpts = m.getGenericParameterTypes();
                        if (params.length == gpts.length) {
                            if (Arrays.equals(params, TypeResolver.erase(TypeResolver.resolve(pt, gpts)))) {
                                return findAccessibleMethod(m);
                            }
                        }
                    }
                }
            }
        }
        throw new NoSuchMethodException("Method '" + name + "' is not accessible");
    }


    private final String name;

    /**
     * Creates method finder with specified array of parameter types.
     *
     * @param name  the name of method to find
     * @param args  the array of parameter types
     */
    private MethodFinder(String name, Class<?>[] args) {
        super(args);
        this.name = name;
    }

    /**
     * Returns an array of {@code Class} objects
     * that represent the formal parameter types of the method.
     * Returns an empty array if the method takes no parameters.
     *
     * @param method  the object that represents method
     * @return the parameter types of the method
     */
    @Override
    protected Class<?>[] getParameters(Method method) {
        return method.getParameterTypes();
    }

    /**
     * Returns {@code true} if and only if the method
     * was declared to take a variable number of arguments.
     *
     * @param method  the object that represents method
     * @return {@code true} if the method was declared
     *         to take a variable number of arguments;
     *         {@code false} otherwise
     */
    @Override
    protected boolean isVarArgs(Method method) {
        return method.isVarArgs();
    }

    /**
     * Checks validness of the method.
     * The valid method should be public and
     * should have the specified name.
     *
     * @param method  the object that represents method
     * @return {@code true} if the method is valid,
     *         {@code false} otherwise
     */
    @Override
    protected boolean isValid(Method method) {
        return !method.isBridge() && Modifier.isPublic(method.getModifiers()) && method.getName().equals(this.name);
    }
}
