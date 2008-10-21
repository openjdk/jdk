/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.mbeanserver;

import java.lang.ref.WeakReference;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Resource;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import static com.sun.jmx.mbeanserver.Util.newMap;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.management.SendNotification;

public class MBeanInjector {
    private static Class<?>[] injectedClasses = {
        MBeanServer.class, ObjectName.class, SendNotification.class,
    };

    public static void inject(Object mbean, MBeanServer mbs, ObjectName name)
    throws Exception {
        ClassInjector injector = injectorForClass(mbean.getClass());
        injector.inject(mbean, MBeanServer.class, mbs);
        injector.inject(mbean, ObjectName.class, name);
    }

    public static boolean injectsSendNotification(Object mbean)
    throws NotCompliantMBeanException {
        ClassInjector injector = injectorForClass(mbean.getClass());
        return injector.injects(SendNotification.class);
    }

    public static void injectSendNotification(Object mbean, SendNotification sn)
    throws Exception {
        ClassInjector injector = injectorForClass(mbean.getClass());
        injector.inject(mbean, SendNotification.class, sn);
    }

    public static void validate(Class<?> c) throws NotCompliantMBeanException {
        injectorForClass(c);
    }

    private static class ClassInjector {
        private Map<Class<?>, List<Field>> fields;
        private Map<Class<?>, List<Method>> methods;

        ClassInjector(Class<?> c) throws NotCompliantMBeanException {
            fields = newMap();
            methods = newMap();

            Class<?> sup = c.getSuperclass();
            ClassInjector supInjector;
            if (sup == null) {
                supInjector = null;
            } else {
                supInjector = injectorForClass(sup);
                fields.putAll(supInjector.fields);
                methods.putAll(supInjector.methods);
            }

            addMembers(c);
            eliminateOverriddenMethods();

            // If we haven't added any new fields or methods to what we
            // inherited, then we can share the parent's maps.
            if (supInjector != null) {
                if (fields.equals(supInjector.fields))
                    fields = supInjector.fields;
                if (methods.equals(supInjector.methods))
                    methods = supInjector.methods;
            }
        }

        boolean injects(Class<?> c) {
            return (fields.get(c) != null || methods.get(c) != null);
        }

        <T> void inject(Object instance, Class<T> type, T resource)
        throws Exception {
            List<Field> fs = fields.get(type);
            if (fs != null) {
                for (Field f : fs)
                    f.set(instance, resource);
            }
            List<Method> ms = methods.get(type);
            if (ms != null) {
                for (Method m : ms) {
                    try {
                        m.invoke(instance, resource);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof Error)
                            throw (Error) cause;
                        else
                            throw (Exception) cause;
                    }
                }
            }
        }

        private void eliminateOverriddenMethods() {
            /* Covariant overriding is unlikely, but it is possible that the
             * parent has a @Resource method that we override with another
             * @Resource method.  We don't want to invoke both methods,
             * because polymorphism means we would actually invoke the same
             * method twice.
             */
            for (Map.Entry<Class<?>, List<Method>> entry : methods.entrySet()) {
                List<Method> list = entry.getValue();
                list = MBeanAnalyzer.eliminateCovariantMethods(list);
                entry.setValue(list);
            }
        }

        /*
         * Find Fields or Methods within the given Class that we can inject
         * resource references into.  Suppose we want to know if a Field can get
         * a reference to an ObjectName.  We'll accept fields like this:
         *
         * @Resource
         * private transient ObjectName name;
         *
         * or like this:
         *
         * @Resource(type = ObjectName.class)
         * private transient Object name;
         *
         * but not like this:
         *
         * @Resource
         * private transient Object name;
         *
         * (Plain @Resource is equivalent to @Resource(type = Object.class).)
         *
         * We don't want to inject into everything that might possibly accept
         * an ObjectName reference, because examples like the last one above
         * could also accept an MBeanServer reference or any other sort of
         * reference.
         *
         * So we accept a Field if it has a @Resource annotation and either
         * (a) its type is exactly ObjectName and its @Resource type is
         * compatible with ObjectName (e.g. it is Object); or
         * (b) its type is compatible with ObjectName and its @Resource type
         * is exactly ObjectName.  Fields that meet these criteria will not
         * meet the same criteria with respect to other types such as MBeanServer.
         *
         * The same logic applies mutatis mutandis to Methods such as this:
         *
         * @Resource
         * private void setObjectName1(ObjectName name)
         * @Resource(type = Object.class)
         * private void setObjectName2(Object name)
         */
        private void addMembers(final Class<?> c)
        throws NotCompliantMBeanException {
            AccessibleObject[][] memberArrays =
                AccessController.doPrivileged(
                    new PrivilegedAction<AccessibleObject[][]>() {
                        public AccessibleObject[][] run() {
                            return new AccessibleObject[][] {
                                c.getDeclaredFields(), c.getDeclaredMethods()
                            };
                        }
                    });
            for (AccessibleObject[] members : memberArrays) {
                for (final AccessibleObject member : members) {
                    Resource res = member.getAnnotation(Resource.class);
                    if (res == null)
                        continue;

                    final Field field;
                    final Method method;
                    final Class<?> memberType;
                    final int modifiers;
                    if (member instanceof Field) {
                        field = (Field) member;
                        memberType = field.getType();
                        modifiers = field.getModifiers();
                        method = null;
                    } else {
                        field = null;
                        method = (Method) member;
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length != 1) {
                            throw new NotCompliantMBeanException(
                                    "@Resource method must have exactly 1 " +
                                    "parameter: " + method);
                        }
                        if (method.getReturnType() != void.class) {
                            throw new NotCompliantMBeanException(
                                    "@Resource method must return void: " +
                                    method);
                        }
                        memberType = paramTypes[0];
                        modifiers = method.getModifiers();
                    }

                    if (Modifier.isStatic(modifiers)) {
                        throw new NotCompliantMBeanException(
                                "@Resource method or field cannot be static: " +
                                member);
                    }

                    for (Class<?> injectedClass : injectedClasses) {
                        Class<?>[] types = {memberType, res.type()};
                        boolean accept = false;
                        for (int i = 0; i < 2; i++) {
                            if (types[i] == injectedClass &&
                                    types[1 - i].isAssignableFrom(injectedClass)) {
                                accept = true;
                                break;
                            }
                        }
                        if (accept) {
                            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                                public Void run() {
                                    member.setAccessible(true);
                                    return null;
                                }
                            });
                            addToMap(fields, injectedClass, field);
                            addToMap(methods, injectedClass, method);
                        }
                    }
                }
            }
        }

        private static <K, V> void addToMap(Map<K, List<V>> map, K key, V value) {
            if (value == null)
                return;
            List<V> list = map.get(key);
            if (list == null)
                list = Collections.singletonList(value);
            else {
                if (list.size() == 1)
                    list = new ArrayList<V>(list);
                list.add(value);
            }
            map.put(key, list);
        }
    }

    private static synchronized ClassInjector injectorForClass(Class<?> c)
    throws NotCompliantMBeanException {
        WeakReference<ClassInjector> wr = injectorMap.get(c);
        ClassInjector ci = (wr == null) ? null : wr.get();
        if (ci == null) {
            ci = new ClassInjector(c);
            injectorMap.put(c, new WeakReference<ClassInjector>(ci));
        }
        return ci;
    }

    private static Map<Class<?>, WeakReference<ClassInjector>> injectorMap =
            new WeakHashMap<Class<?>, WeakReference<ClassInjector>>();
}
