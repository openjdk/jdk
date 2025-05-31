/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.beans.introspect;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import com.sun.beans.TypeResolver;
import com.sun.beans.finder.MethodFinder;

final class MethodInfo {

    // These are some common interfaces that we know a priori
    // will not contain any bean property getters or setters.
    static final Set<Class<?>> IGNORABLE_INTERFACES = Set.of(
        AutoCloseable.class,
        Cloneable.class,
        Closeable.class,
        Comparable.class,
        Externalizable.class,
        Serializable.class
    );

    final Method method;
    final Class<?> type;

    MethodInfo(Method method, Class<?> type) {
        this.method = method;
        this.type = type;
    }

    MethodInfo(Method method, Type type) {
        this.method = method;
        this.type = resolve(method, type);
    }

    boolean isThrow(Class<?> exception) {
        for (Class<?> type : this.method.getExceptionTypes()) {
            if (type == exception) {
                return true;
            }
        }
        return false;
    }

    static Class<?> resolve(Method method, Type type) {
        return TypeResolver.erase(TypeResolver.resolveInClass(method.getDeclaringClass(), type));
    }

    static List<Method> get(Class<?> type) {
        List<Method> list = null;
        if (type != null) {

            // Add declared methods
            boolean inaccessible = !Modifier.isPublic(type.getModifiers());
            for (Method method : type.getMethods()) {
                if (method.getDeclaringClass().equals(type)) {
                    if (inaccessible) {
                        try {
                            method = MethodFinder.findAccessibleMethod(method);
                            if (!method.getDeclaringClass().isInterface()) {
                                method = null; // ignore methods from superclasses
                            }
                        } catch (NoSuchMethodException exception) {
                            // commented out because of 6976577
                            // method = null; // ignore inaccessible methods
                        }
                    }
                    if (method != null) {
                        (list = createIfNeeded(list)).add(method);
                    }
                }
            }

            // Add methods inherited from interfaces
            Deque<Class<?>> ifaceDeque = new ArrayDeque<>(List.of(type.getInterfaces()));
            while (!ifaceDeque.isEmpty()) {
                Class<?> iface = ifaceDeque.removeLast();
                if (IGNORABLE_INTERFACES.contains(iface)) {
                    continue;
                }
                ifaceDeque.addAll(List.of(iface.getInterfaces()));
                for (Method method : iface.getMethods()) {
                    if (!Modifier.isAbstract(method.getModifiers()) && !method.isBridge()) {
                        (list = createIfNeeded(list)).add(method);
                    }
                }
            }
        }
        if (list != null) {
            list.sort(MethodOrder.instance);
            return Collections.unmodifiableList(list);
        }
        return Collections.emptyList();
    }

    private static List<Method> createIfNeeded(List<Method> list) {
        return list != null ? list : new ArrayList<>();
    }

    /**
     * A comparator that defines a total order so that methods have the same
     * name and identical signatures appear next to each others.
     **/
    private static final class MethodOrder implements Comparator<Method> {

        /*
         * Code particularly was copied from com.sun.jmx.mbeanserver.MethodOrder
         */
        @Override
        public int compare(final Method a, final Method b) {
            int cmp = a.getName().compareTo(b.getName());
            if (cmp != 0) {
                return cmp;
            }
            final Class<?>[] aparams = a.getParameterTypes();
            final Class<?>[] bparams = b.getParameterTypes();
            if (aparams.length != bparams.length) {
                return aparams.length - bparams.length;
            }
            for (int i = 0; i < aparams.length; ++i) {
                final Class<?> aparam = aparams[i];
                final Class<?> bparam = bparams[i];
                if (aparam == bparam) {
                    continue;
                }
                cmp = aparam.getName().compareTo(bparam.getName());
                if (cmp != 0) {
                    return cmp;
                }
            }
            final Class<?> aret = a.getReturnType();
            final Class<?> bret = b.getReturnType();
            return aret == bret ? 0 : aret.getName().compareTo(bret.getName());
        }

        static final MethodOrder instance = new MethodOrder();
    }
}
