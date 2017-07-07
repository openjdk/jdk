/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.PrivilegedExceptionAction;
import java.security.SecureClassLoader;
import java.util.Arrays;

/*
 * This copies from sun.reflect.misc.MethodUtil to implement the trampoline
 * code such that when a Method is invoked, it will be called through
 * the trampoline that is defined by this MethodUtil class loader.
 */
class Trampoline {
    static {
        if (Trampoline.class.getClassLoader() == null) {
            throw new Error(
                    "Trampoline must not be defined by the bootstrap classloader");
        }
    }

    private static void ensureInvocableMethod(Method m)
            throws InvocationTargetException {
        Class<?> clazz = m.getDeclaringClass();
        if (clazz.equals(AccessController.class) ||
                clazz.equals(Method.class) ||
                clazz.getName().startsWith("java.lang.invoke."))
            throw new InvocationTargetException(
                    new UnsupportedOperationException("invocation not supported"));
    }

    private static Object invoke(Method m, Object obj, Object[] params)
            throws InvocationTargetException, IllegalAccessException {
        ensureInvocableMethod(m);
        return m.invoke(obj, params);
    }
}

/*
 * Create a trampoline class.
 */
public final class MethodUtil extends SecureClassLoader {
    private static final String WS_UTIL_PKG = "com.sun.xml.internal.ws.util.";
    private static final String TRAMPOLINE = WS_UTIL_PKG + "Trampoline";
    private static final Method bounce = getTrampoline();
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;


    private MethodUtil() {
        super();
    }

    /*
     * Bounce through the trampoline.
     */
    public static Object invoke(Method m, Object obj, Object[] params)
            throws InvocationTargetException, IllegalAccessException {
        try {
            return bounce.invoke(null, m, obj, params);
        } catch (InvocationTargetException ie) {
            Throwable t = ie.getCause();

            if (t instanceof InvocationTargetException) {
                throw (InvocationTargetException) t;
            } else if (t instanceof IllegalAccessException) {
                throw (IllegalAccessException) t;
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                throw new Error("Unexpected invocation error", t);
            }
        } catch (IllegalAccessException iae) {
            // this can't happen
            throw new Error("Unexpected invocation error", iae);
        }
    }

    private static Method getTrampoline() {
        try {
            return AccessController.doPrivileged(
                    new PrivilegedExceptionAction<Method>() {
                        public Method run() throws Exception {
                            Class<?> t = getTrampolineClass();
                            Method b = t.getDeclaredMethod("invoke",
                                    Method.class, Object.class, Object[].class);
                            b.setAccessible(true);
                            return b;
                        }
                    });
        } catch (Exception e) {
            throw new InternalError("bouncer cannot be found", e);
        }
    }


    protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        // First, check if the class has already been loaded
        checkPackageAccess(name);
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            try {
                c = findClass(name);
            } catch (ClassNotFoundException e) {
                // Fall through ...
            }
            if (c == null) {
                c = getParent().loadClass(name);
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }


    protected Class<?> findClass(final String name)
            throws ClassNotFoundException {
        if (!name.startsWith(WS_UTIL_PKG)) {
            throw new ClassNotFoundException(name);
        }
        String path = "/".concat(name.replace('.', '/').concat(".class"));
        try (InputStream in = MethodUtil.class.getResourceAsStream(path)) {
            byte[] b = readAllBytes(in);
            return defineClass(name, b);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    /**
     * JDK9 {@link InputStream#readAllBytes()} substitution.
     */
    private byte[] readAllBytes(InputStream in) throws IOException {
        byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
        int capacity = buf.length;
        int nread = 0;
        int n;
        for (; ; ) {
            // read to EOF which may read more or less than initial buffer size
            while ((n = in.read(buf, nread, capacity - nread)) > 0)
                nread += n;

            // if the last call to read returned -1, then we're done
            if (n < 0)
                break;

            // need to allocate a larger buffer
            if (capacity <= MAX_BUFFER_SIZE - capacity) {
                capacity = capacity << 1;
            } else {
                if (capacity == MAX_BUFFER_SIZE)
                    throw new OutOfMemoryError("Required array size too large");
                capacity = MAX_BUFFER_SIZE;
            }
            buf = Arrays.copyOf(buf, capacity);
        }
        return (capacity == nread) ? buf : Arrays.copyOf(buf, nread);
    }


    /*
     * Define the proxy classes
     */
    private Class<?> defineClass(String name, byte[] b) throws IOException {
        CodeSource cs = new CodeSource(null, (java.security.cert.Certificate[]) null);
        if (!name.equals(TRAMPOLINE)) {
            throw new IOException("MethodUtil: bad name " + name);
        }
        return defineClass(name, b, 0, b.length, cs);
    }

    protected PermissionCollection getPermissions(CodeSource codesource) {
        PermissionCollection perms = super.getPermissions(codesource);
        perms.add(new AllPermission());
        return perms;
    }

    private static Class<?> getTrampolineClass() {
        try {
            return Class.forName(TRAMPOLINE, true, new MethodUtil());
        } catch (ClassNotFoundException e) {
        }
        return null;
    }

    /**
     * Checks package access on the given classname.
     * This method is typically called when the Class instance is not
     * available and the caller attempts to load a class on behalf
     * the true caller (application).
     */
    private static void checkPackageAccess(String name) {
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

    /**
     * Checks package access on the given class.
     * <p>
     * If it is a {@link Proxy#isProxyClass(Class)} that implements
     * a non-public interface (i.e. may be in a non-restricted package),
     * also check the package access on the proxy interfaces.
     */
    private static void checkPackageAccess(Class<?> clazz) {
        checkPackageAccess(clazz.getName());
        if (isNonPublicProxyClass(clazz)) {
            checkProxyPackageAccess(clazz);
        }
    }

    // Note that bytecode instrumentation tools may exclude 'sun.*'
    // classes but not generated proxy classes and so keep it in com.sun.*
    private static final String PROXY_PACKAGE = "com.sun.proxy";

    /**
     * Test if the given class is a proxy class that implements
     * non-public interface.  Such proxy class may be in a non-restricted
     * package that bypasses checkPackageAccess.
     */
    private static boolean isNonPublicProxyClass(Class<?> cls) {
        String name = cls.getName();
        int i = name.lastIndexOf('.');
        String pkg = (i != -1) ? name.substring(0, i) : "";
        return Proxy.isProxyClass(cls) && !pkg.startsWith(PROXY_PACKAGE);
    }

    /**
     * Check package access on the proxy interfaces that the given proxy class
     * implements.
     *
     * @param clazz Proxy class object
     */
    private static void checkProxyPackageAccess(Class<?> clazz) {
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
}
