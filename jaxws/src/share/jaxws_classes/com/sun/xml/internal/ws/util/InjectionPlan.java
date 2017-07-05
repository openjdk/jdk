/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import javax.annotation.Resource;
import javax.xml.ws.WebServiceException;

/**
 * Encapsulates which field/method the injection is done, and performs the
 * injection.
 */
public abstract class InjectionPlan<T, R> {
    /**
     * Perform injection
     *
     * @param instance
     *            Instance
     * @param resource
     *            Resource
     */
    public abstract void inject(T instance, R resource);

    /**
     * Perform injection, but resource is only generated if injection is
     * necessary.
     *
     * @param instance
     * @param resource
     */
    public void inject(T instance, Callable<R> resource) {
        try {
            inject(instance, resource.call());
        } catch(Exception e) {
            throw new WebServiceException(e);
        }
    }

    /*
     * Injects to a field.
     */
    public static class FieldInjectionPlan<T, R> extends
            InjectionPlan<T, R> {
        private final Field field;

        public FieldInjectionPlan(Field field) {
            this.field = field;
        }

        public void inject(final T instance, final R resource) {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    try {
                        if (!field.isAccessible()) {
                            field.setAccessible(true);
                        }
                        field.set(instance, resource);
                        return null;
                    } catch (IllegalAccessException e) {
                        throw new WebServiceException(e);
                    }
                }
            });
        }
    }

    /*
     * Injects to a method.
     */
    public static class MethodInjectionPlan<T, R> extends
            InjectionPlan<T, R> {
        private final Method method;

        public MethodInjectionPlan(Method method) {
            this.method = method;
        }

        public void inject(T instance, R resource) {
            invokeMethod(method, instance, resource);
        }
    }

    /*
     * Helper for invoking a method with elevated privilege.
     */
    private static void invokeMethod(final Method method, final Object instance, final Object... args) {
        if(method==null)    return;
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                try {
                    if (!method.isAccessible()) {
                        method.setAccessible(true);
                    }
                    method.invoke(instance,args);
                } catch (IllegalAccessException e) {
                    throw new WebServiceException(e);
                } catch (InvocationTargetException e) {
                    throw new WebServiceException(e);
                }
                return null;
            }
        });
    }

    /*
     * Combines multiple {@link InjectionPlan}s into one.
     */
    private static class Compositor<T, R> extends InjectionPlan<T, R> {
        private final Collection<InjectionPlan<T, R>> children;

        public Compositor(Collection<InjectionPlan<T, R>> children) {
            this.children = children;
        }

        public void inject(T instance, R res) {
            for (InjectionPlan<T, R> plan : children)
                plan.inject(instance, res);
        }

        public void inject(T instance, Callable<R> resource) {
            if (!children.isEmpty()) {
                super.inject(instance, resource);
            }
        }
    }

    /*
     * Creates an {@link InjectionPlan} that injects the given resource type to the given class.
     *
     * @param isStatic
     *      Only look for static field/method
     *
     */
    public static <T,R>
    InjectionPlan<T,R> buildInjectionPlan(Class<? extends T> clazz, Class<R> resourceType, boolean isStatic) {
        List<InjectionPlan<T,R>> plan = new ArrayList<InjectionPlan<T,R>>();

        Class<?> cl = clazz;
        while(cl != Object.class) {
            for(Field field: cl.getDeclaredFields()) {
                Resource resource = field.getAnnotation(Resource.class);
                if (resource != null) {
                    if(isInjectionPoint(resource, field.getType(),
                        "Incorrect type for field"+field.getName(),
                        resourceType)) {

                        if(isStatic && !Modifier.isStatic(field.getModifiers()))
                            throw new WebServiceException("Static resource "+resourceType+" cannot be injected to non-static "+field);

                        plan.add(new FieldInjectionPlan<T,R>(field));
                    }
                }
            }
            cl = cl.getSuperclass();
        }

        cl = clazz;
        while(cl != Object.class) {
            for(Method method : cl.getDeclaredMethods()) {
                Resource resource = method.getAnnotation(Resource.class);
                if (resource != null) {
                    Class[] paramTypes = method.getParameterTypes();
                    if (paramTypes.length != 1)
                        throw new WebServiceException("Incorrect no of arguments for method "+method);
                    if(isInjectionPoint(resource,paramTypes[0],
                        "Incorrect argument types for method"+method.getName(),
                        resourceType)) {

                        if(isStatic && !Modifier.isStatic(method.getModifiers()))
                            throw new WebServiceException("Static resource "+resourceType+" cannot be injected to non-static "+method);

                        plan.add(new MethodInjectionPlan<T,R>(method));
                    }
                }
            }
            cl = cl.getSuperclass();
        }

        return new Compositor<T,R>(plan);
    }

    /*
     * Returns true if the combination of {@link Resource} and the field/method type
     * are consistent for {@link WebServiceContext} injection.
     */
    private static boolean isInjectionPoint(Resource resource, Class fieldType, String errorMessage, Class resourceType ) {
        Class t = resource.type();
        if (t.equals(Object.class)) {
            return fieldType.equals(resourceType);
        } else if (t.equals(resourceType)) {
            if (fieldType.isAssignableFrom(resourceType)) {
                return true;
            } else {
                // type compatibility error
                throw new WebServiceException(errorMessage);
            }
        }
        return false;
    }
}
