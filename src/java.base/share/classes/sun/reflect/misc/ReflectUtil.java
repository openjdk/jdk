/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.reflect.misc;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import jdk.internal.reflect.Reflection;

public final class ReflectUtil {

    private ReflectUtil() {
    }

    public static Class<?> forName(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    /**
     * Ensures that access to a method or field is granted and throws
     * IllegalAccessException if not. This method is not suitable for checking
     * access to constructors.
     *
     * @param currentClass the class performing the access
     * @param memberClass the declaring class of the member being accessed
     * @param target the target object if accessing instance field or method;
     *               or null if accessing static field or method or if target
     *               object access rights will be checked later
     * @param modifiers the member's access modifiers
     * @throws IllegalAccessException if access to member is denied
     * @implNote Delegates directly to
     *           {@link Reflection#ensureMemberAccess(Class, Class, Class, int)}
     *           which should be used instead.
     */
    public static void ensureMemberAccess(Class<?> currentClass,
                                          Class<?> memberClass,
                                          Object target,
                                          int modifiers)
        throws IllegalAccessException
    {
        Reflection.ensureMemberAccess(currentClass,
                                      memberClass,
                                      target == null ? null : target.getClass(),
                                      modifiers);
    }

    /**
     * Does nothing.
     */
    public static void conservativeCheckMemberAccess(Member m) {
    }

    /**
     * Does nothing.
     */
    public static void checkPackageAccess(Class<?> clazz) {
    }

    /**
     * Does nothing
     */
    public static void checkPackageAccess(String name) {
    }

    /**
     * Returns true.
     */
    public static boolean isPackageAccessible(Class<?> clazz) {
        return true;
    }

    /**
     * Returns false.
     */
    public static boolean needsPackageAccessCheck(ClassLoader from, ClassLoader to) {
        return false;
    }

    /**
     * Does nothing
     */
    public static void checkProxyPackageAccess(Class<?> clazz) {
    }

    /**
     * Does nothing.
     */
    public static void checkProxyPackageAccess(ClassLoader ccl,
                                               Class<?>... interfaces) {
    }

    // Note that bytecode instrumentation tools may exclude 'sun.*'
    // classes but not generated proxy classes and so keep it in com.sun.*
    public static final String PROXY_PACKAGE = "com.sun.proxy";

    /**
     * Test if the given class is a proxy class that implements
     * non-public interface.  Such proxy class may be in a non-restricted
     * package that bypasses checkPackageAccess.
     */
    public static boolean isNonPublicProxyClass(Class<?> cls) {
        if (!Proxy.isProxyClass(cls)) {
            return false;
        }
        return !Modifier.isPublic(cls.getModifiers());
    }

    /**
     * Check if the given method is a method declared in the proxy interface
     * implemented by the given proxy instance.
     *
     * @param proxy a proxy instance
     * @param method an interface method dispatched to a InvocationHandler
     *
     * @throws IllegalArgumentException if the given proxy or method is invalid.
     */
    public static void checkProxyMethod(Object proxy, Method method) {
        // check if it is a valid proxy instance
        if (proxy == null || !Proxy.isProxyClass(proxy.getClass())) {
            throw new IllegalArgumentException("Not a Proxy instance");
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Can't handle static method");
        }

        Class<?> c = method.getDeclaringClass();
        if (c == Object.class) {
            String name = method.getName();
            if (name.equals("hashCode") || name.equals("equals") || name.equals("toString")) {
                return;
            }
        }

        if (isSuperInterface(proxy.getClass(), c)) {
            return;
        }

        // disallow any method not declared in one of the proxy interfaces
        throw new IllegalArgumentException("Can't handle: " + method);
    }

    private static boolean isSuperInterface(Class<?> c, Class<?> intf) {
        for (Class<?> i : c.getInterfaces()) {
            if (i == intf) {
                return true;
            }
            if (isSuperInterface(i, intf)) {
                return true;
            }
        }
        return false;
    }

}
