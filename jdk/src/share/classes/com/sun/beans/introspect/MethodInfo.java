/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.beans.TypeResolver;
import com.sun.beans.finder.MethodFinder;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MethodInfo {
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
                        if (list == null) {
                            list = new ArrayList<>();
                        }
                        list.add(method);
                    }
                }
            }
        }
        return (list != null)
                ? Collections.unmodifiableList(list)
                : Collections.emptyList();
    }
}
