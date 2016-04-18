/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

package sun.reflect;

import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;

public class ReflectionFactory {

    private static final ReflectionFactory soleInstance = new ReflectionFactory();
    private final jdk.internal.reflect.ReflectionFactory delegate;

    private ReflectionFactory() {
        delegate = AccessController.doPrivileged(
            new PrivilegedAction<jdk.internal.reflect.ReflectionFactory>() {
                public jdk.internal.reflect.ReflectionFactory run() {
                    return jdk.internal.reflect.ReflectionFactory.getReflectionFactory();
                }
        });
    }

    private static final Permission REFLECTION_FACTORY_ACCESS_PERM
            = new RuntimePermission("reflectionFactoryAccess");

    /**
     * Provides the caller with the capability to instantiate reflective
     * objects.
     *
     * <p> First, if there is a security manager, its {@code checkPermission}
     * method is called with a {@link java.lang.RuntimePermission} with target
     * {@code "reflectionFactoryAccess"}.  This may result in a securit
     * exception.
     *
     * <p> The returned {@code ReflectionFactory} object should be carefully
     * guarded by the caller, since it can be used to read and write private
     * data and invoke private methods, as well as to load unverified bytecodes.
     * It must never be passed to untrusted code.
     *
     * @throws SecurityException if a security manager exists and its
     *         {@code checkPermission} method doesn't allow access to
     *         the RuntimePermission "reflectionFactoryAccess".
     */
    public static ReflectionFactory getReflectionFactory() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(REFLECTION_FACTORY_ACCESS_PERM);
        }
        return soleInstance;
    }

    public Constructor<?> newConstructorForSerialization(Class<?> classToInstantiate,
                                                         Constructor<?> constructorToCall)
    {
        return delegate.newConstructorForSerialization(classToInstantiate,
                                                       constructorToCall);
    }
}

