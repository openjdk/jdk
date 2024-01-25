/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.PrivilegedExceptionAction;
import java.security.SecureClassLoader;


class Trampoline {
    static {
        if (Trampoline.class.getClassLoader() == null) {
            throw new Error(
                "Trampoline must not be defined by the bootstrap classloader");
        }
    }

    @SuppressWarnings("removal")
    private static void ensureInvocableMethod(Method m)
        throws InvocationTargetException
    {
        Class<?> clazz = m.getDeclaringClass();
        if (clazz.equals(AccessController.class) ||
            clazz.equals(Method.class) ||
            clazz.getName().startsWith("java.lang.invoke."))
            throw new InvocationTargetException(
                new UnsupportedOperationException("invocation not supported"));
    }

    private static Object invoke(Method m, Object obj, Object[] params)
        throws InvocationTargetException, IllegalAccessException
    {
        ensureInvocableMethod(m);
        return m.invoke(obj, params);
    }
}

/*
 * Create a trampoline class.
 */
public final class MethodUtil extends SecureClassLoader {
    private static final String MISC_PKG = "sun.reflect.misc.";
    private static final String TRAMPOLINE = MISC_PKG + "Trampoline";
    private static final Method bounce = getTrampoline();

    private MethodUtil() {
        super();
    }

    public static Method getMethod(Class<?> cls, String name, Class<?>[] args)
        throws NoSuchMethodException {
        ReflectUtil.checkPackageAccess(cls);
        return cls.getMethod(name, args);
    }

    public static Method[] getMethods(Class<?> cls) {
        ReflectUtil.checkPackageAccess(cls);
        return cls.getMethods();
    }

    /*
     * Bounce through the trampoline.
     */
    public static Object invoke(Method m, Object obj, Object[] params)
        throws InvocationTargetException, IllegalAccessException {
        try {
            return bounce.invoke(null, new Object[] {m, obj, params});
        } catch (InvocationTargetException ie) {
            Throwable t = ie.getCause();

            if (t instanceof InvocationTargetException ite) {
                throw ite;
            } else if (t instanceof IllegalAccessException iae) {
                throw iae;
            } else if (t instanceof RuntimeException re) {
                throw re;
            } else if (t instanceof Error error) {
                throw error;
            } else {
                throw new Error("Unexpected invocation error", t);
            }
        } catch (IllegalAccessException iae) {
            // this can't happen
            throw new Error("Unexpected invocation error", iae);
        }
    }

    @SuppressWarnings("removal")
    private static Method getTrampoline() {
        try {
            return AccessController.doPrivileged(
                new PrivilegedExceptionAction<Method>() {
                    public Method run() throws Exception {
                        Class<?> t = getTrampolineClass();
                        Class<?>[] types = {
                            Method.class, Object.class, Object[].class
                        };
                        Method b = t.getDeclaredMethod("invoke", types);
                        b.setAccessible(true);
                        return b;
                    }
                });
        } catch (Exception e) {
            throw new InternalError("bouncer cannot be found", e);
        }
    }


    protected synchronized Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException
    {
        // First, check if the class has already been loaded
        ReflectUtil.checkPackageAccess(name);
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
        throws ClassNotFoundException
    {
        if (!name.startsWith(MISC_PKG)) {
            throw new ClassNotFoundException(name);
        }
        String path = name.replace('.', '/').concat(".class");
        try {
            InputStream in = Object.class.getModule().getResourceAsStream(path);
            if (in != null) {
                try (in) {
                    byte[] b = in.readAllBytes();
                    return defineClass(name, b);
                }
            }
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }

        throw new ClassNotFoundException(name);
    }


    /*
     * Define the proxy classes
     */
    private Class<?> defineClass(String name, byte[] b) throws IOException {
        CodeSource cs = new CodeSource(null, (java.security.cert.Certificate[])null);
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

}
