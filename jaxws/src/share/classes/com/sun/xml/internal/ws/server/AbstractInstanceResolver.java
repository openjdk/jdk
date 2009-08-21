/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.xml.internal.ws.server;

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.server.InstanceResolver;
import com.sun.xml.internal.ws.api.server.ResourceInjector;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.util.localization.Localizable;

import javax.annotation.Resource;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Partial implementation of {@link InstanceResolver} with
 * convenience methods to do the resource injection.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class AbstractInstanceResolver<T> extends InstanceResolver<T> {

    /**
     * Encapsulates which field/method the injection is done,
     * and performs the injection.
     */
    protected static interface InjectionPlan<T,R> {
        void inject(T instance,R resource);
        /**
         * Gets the number of injections to be performed.
         */
        int count();
    }

    /**
     * Injects to a field.
     */
    protected static class FieldInjectionPlan<T,R> implements InjectionPlan<T,R> {
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
                        field.set(instance,resource);
                        return null;
                    } catch (IllegalAccessException e) {
                        throw new ServerRtException("server.rt.err",e);
                    }
                }
            });
        }

        public int count() {
            return 1;
        }
    }

    /**
     * Injects to a method.
     */
    protected static class MethodInjectionPlan<T,R> implements InjectionPlan<T,R> {
        private final Method method;

        public MethodInjectionPlan(Method method) {
            this.method = method;
        }

        public void inject(T instance, R resource) {
            invokeMethod(method, instance, resource);
        }

        public int count() {
            return 1;
        }
    }

    /**
     * Combines multiple {@link InjectionPlan}s into one.
     */
    private static class Compositor<T,R> implements InjectionPlan<T,R> {
        private final InjectionPlan<T,R>[] children;

        public Compositor(Collection<InjectionPlan<T,R>> children) {
            this.children = children.toArray(new InjectionPlan[children.size()]);
        }

        public void inject(T instance, R res) {
            for (InjectionPlan<T,R> plan : children)
                plan.inject(instance,res);
        }

        public int count() {
            int r = 0;
            for (InjectionPlan<T, R> plan : children)
                r += plan.count();
            return r;
        }
    }

    protected static ResourceInjector getResourceInjector(WSEndpoint endpoint) {
        ResourceInjector ri = endpoint.getContainer().getSPI(ResourceInjector.class);
        if(ri==null)
            ri = ResourceInjector.STANDALONE;
        return ri;
    }

    /**
     * Helper for invoking a method with elevated privilege.
     */
    protected static void invokeMethod(final @Nullable Method method, final Object instance, final Object... args) {
        if(method==null)    return;
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                try {
                    if (!method.isAccessible()) {
                        method.setAccessible(true);
                    }
                    method.invoke(instance,args);
                } catch (IllegalAccessException e) {
                    throw new ServerRtException("server.rt.err",e);
                } catch (InvocationTargetException e) {
                    throw new ServerRtException("server.rt.err",e);
                }
                return null;
            }
        });
    }

    /**
     * Finds the method that has the given annotation, while making sure that
     * there's only at most one such method.
     */
    protected final @Nullable Method findAnnotatedMethod(Class clazz, Class<? extends Annotation> annType) {
        boolean once = false;
        Method r = null;
        for(Method method : clazz.getDeclaredMethods()) {
            if (method.getAnnotation(annType) != null) {
                if (once)
                    throw new ServerRtException(ServerMessages.ANNOTATION_ONLY_ONCE(annType));
                if (method.getParameterTypes().length != 0)
                    throw new ServerRtException(ServerMessages.NOT_ZERO_PARAMETERS(method));
                r = method;
                once = true;
            }
        }
        return r;
    }

    /**
     * Creates an {@link InjectionPlan} that injects the given resource type to the given class.
     *
     * @param isStatic
     *      Only look for static field/method
     *
     */
    protected static <T,R>
    InjectionPlan<T,R> buildInjectionPlan(Class<? extends T> clazz, Class<R> resourceType, boolean isStatic) {
        List<InjectionPlan<T,R>> plan = new ArrayList<InjectionPlan<T,R>>();

        Class<?> cl = clazz;
        while(cl != Object.class) {
            for(Field field: cl.getDeclaredFields()) {
                Resource resource = field.getAnnotation(Resource.class);
                if (resource != null) {
                    if(isInjectionPoint(resource, field.getType(),
                        ServerMessages.localizableWRONG_FIELD_TYPE(field.getName()),resourceType)) {

                        if(isStatic && !Modifier.isStatic(field.getModifiers()))
                            throw new WebServiceException(ServerMessages.STATIC_RESOURCE_INJECTION_ONLY(resourceType,field));

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
                        throw new ServerRtException(ServerMessages.WRONG_NO_PARAMETERS(method));
                    if(isInjectionPoint(resource,paramTypes[0],
                        ServerMessages.localizableWRONG_PARAMETER_TYPE(method.getName()),resourceType)) {

                        if(isStatic && !Modifier.isStatic(method.getModifiers()))
                            throw new WebServiceException(ServerMessages.STATIC_RESOURCE_INJECTION_ONLY(resourceType,method));

                        plan.add(new MethodInjectionPlan<T,R>(method));
                    }
                }
            }
            cl = cl.getSuperclass();
        }

        return new Compositor<T,R>(plan);
    }

    /**
     * Returns true if the combination of {@link Resource} and the field/method type
     * are consistent for {@link WebServiceContext} injection.
     */
    private static boolean isInjectionPoint(Resource resource, Class fieldType, Localizable errorMessage, Class resourceType ) {
        Class t = resource.type();
        if (t.equals(Object.class)) {
            return fieldType.equals(resourceType);
        } else if (t.equals(resourceType)) {
            if (fieldType.isAssignableFrom(resourceType)) {
                return true;
            } else {
                // type compatibility error
                throw new ServerRtException(errorMessage);
            }
        }
        return false;
    }
}
