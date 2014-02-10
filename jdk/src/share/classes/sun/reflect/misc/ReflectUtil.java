/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import sun.reflect.Reflection;
import sun.security.util.SecurityConstants;

public final class ReflectUtil {

    private ReflectUtil() {
    }

    public static Class<?> forName(String name)
        throws ClassNotFoundException {
        checkPackageAccess(name);
        return Class.forName(name);
    }

    public static Object newInstance(Class<?> cls)
        throws InstantiationException, IllegalAccessException {
        checkPackageAccess(cls);
        return cls.newInstance();
    }

    /*
     * Reflection.ensureMemberAccess is overly-restrictive
     * due to a bug. We awkwardly work around it for now.
     */
    public static void ensureMemberAccess(Class<?> currentClass,
                                          Class<?> memberClass,
                                          Object target,
                                          int modifiers)
        throws IllegalAccessException
    {
        if (target == null && Modifier.isProtected(modifiers)) {
            int mods = modifiers;
            mods = mods & (~Modifier.PROTECTED);
            mods = mods | Modifier.PUBLIC;

            /*
             * See if we fail because of class modifiers
             */
            Reflection.ensureMemberAccess(currentClass,
                                          memberClass,
                                          target,
                                          mods);
            try {
                /*
                 * We're still here so class access was ok.
                 * Now try with default field access.
                 */
                mods = mods & (~Modifier.PUBLIC);
                Reflection.ensureMemberAccess(currentClass,
                                              memberClass,
                                              target,
                                              mods);
                /*
                 * We're still here so access is ok without
                 * checking for protected.
                 */
                return;
            } catch (IllegalAccessException e) {
                /*
                 * Access failed but we're 'protected' so
                 * if the test below succeeds then we're ok.
                 */
                if (isSubclassOf(currentClass, memberClass)) {
                    return;
                } else {
                    throw e;
                }
            }
        } else {
            Reflection.ensureMemberAccess(currentClass,
                                          memberClass,
                                          target,
                                          modifiers);
        }
    }

    private static boolean isSubclassOf(Class<?> queryClass,
                                        Class<?> ofClass)
    {
        while (queryClass != null) {
            if (queryClass == ofClass) {
                return true;
            }
            queryClass = queryClass.getSuperclass();
        }
        return false;
    }

    /**
     * Does a conservative approximation of member access check. Use this if
     * you don't have an actual 'userland' caller Class/ClassLoader available.
     * This might be more restrictive than a precise member access check where
     * you have a caller, but should never allow a member access that is
     * forbidden.
     *
     * @param m the {@code Member} about to be accessed
     */
    public static void conservativeCheckMemberAccess(Member m) throws SecurityException{
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null)
            return;

        // Check for package access on the declaring class.
        //
        // In addition, unless the member and the declaring class are both
        // public check for access declared member permissions.
        //
        // This is done regardless of ClassLoader relations between the {@code
        // Member m} and any potential caller.

        final Class<?> declaringClass = m.getDeclaringClass();

        checkPackageAccess(declaringClass);

        if (Modifier.isPublic(m.getModifiers()) &&
                Modifier.isPublic(declaringClass.getModifiers()))
            return;

        // Check for declared member access.
        sm.checkPermission(SecurityConstants.CHECK_MEMBER_ACCESS_PERMISSION);
    }

    /**
     * Checks package access on the given class.
     *
     * If it is a {@link Proxy#isProxyClass(java.lang.Class)} that implements
     * a non-public interface (i.e. may be in a non-restricted package),
     * also check the package access on the proxy interfaces.
     */
    public static void checkPackageAccess(Class<?> clazz) {
        checkPackageAccess(clazz.getName());
        if (isNonPublicProxyClass(clazz)) {
            checkProxyPackageAccess(clazz);
        }
    }

    /**
     * Checks package access on the given classname.
     * This method is typically called when the Class instance is not
     * available and the caller attempts to load a class on behalf
     * the true caller (application).
     */
    public static void checkPackageAccess(String name) {
        SecurityManager s = System.getSecurityManager();
        if (s != null) {
            String cname = name.replace('/', '.');
            if (cname.startsWith("[")) {
                int b = cname.lastIndexOf('[') + 2;
                if (b > 1 && b < cname.length()) {
                    cname = cname.substring(b);
                }
            }
            int i = cname.lastIndexOf('.');
            if (i != -1) {
                s.checkPackageAccess(cname.substring(0, i));
            }
        }
    }

    public static boolean isPackageAccessible(Class<?> clazz) {
        try {
            checkPackageAccess(clazz);
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }

    // Returns true if p is an ancestor of cl i.e. class loader 'p' can
    // be found in the cl's delegation chain
    private static boolean isAncestor(ClassLoader p, ClassLoader cl) {
        ClassLoader acl = cl;
        do {
            acl = acl.getParent();
            if (p == acl) {
                return true;
            }
        } while (acl != null);
        return false;
    }

    /**
     * Returns true if package access check is needed for reflective
     * access from a class loader 'from' to classes or members in
     * a class defined by class loader 'to'.  This method returns true
     * if 'from' is not the same as or an ancestor of 'to'.  All code
     * in a system domain are granted with all permission and so this
     * method returns false if 'from' class loader is a class loader
     * loading system classes.  On the other hand, if a class loader
     * attempts to access system domain classes, it requires package
     * access check and this method will return true.
     */
    public static boolean needsPackageAccessCheck(ClassLoader from, ClassLoader to) {
        if (from == null || from == to)
            return false;

        if (to == null)
            return true;

        return !isAncestor(from, to);
    }

    /**
     * Check package access on the proxy interfaces that the given proxy class
     * implements.
     *
     * @param clazz Proxy class object
     */
    public static void checkProxyPackageAccess(Class<?> clazz) {
        SecurityManager s = System.getSecurityManager();
        if (s != null) {
            // check proxy interfaces if the given class is a proxy class
            if (Proxy.isProxyClass(clazz)) {
                for (Class<?> intf : clazz.getInterfaces()) {
                    checkPackageAccess(intf);
                }
            }
        }
    }

    /**
     * Access check on the interfaces that a proxy class implements and throw
     * {@code SecurityException} if it accesses a restricted package from
     * the caller's class loader.
     *
     * @param ccl the caller's class loader
     * @param interfaces the list of interfaces that a proxy class implements
     */
    public static void checkProxyPackageAccess(ClassLoader ccl,
                                               Class<?>... interfaces)
    {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            for (Class<?> intf : interfaces) {
                ClassLoader cl = intf.getClassLoader();
                if (needsPackageAccessCheck(ccl, cl)) {
                    checkPackageAccess(intf);
                }
            }
        }
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
        String name = cls.getName();
        int i = name.lastIndexOf('.');
        String pkg = (i != -1) ? name.substring(0, i) : "";
        return Proxy.isProxyClass(cls) && !pkg.equals(PROXY_PACKAGE);
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

        // disallow any method not declared in one of the proxy intefaces
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

    /**
     * Checks if {@code Class cls} is a VM-anonymous class
     * as defined by {@link sun.misc.Unsafe#defineAnonymousClass}
     * (not to be confused with a Java Language anonymous inner class).
     */
    public static boolean isVMAnonymousClass(Class<?> cls) {
        return cls.getName().indexOf("/") > -1;
    }
}
