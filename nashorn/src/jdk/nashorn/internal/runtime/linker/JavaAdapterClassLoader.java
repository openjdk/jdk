/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.linker;

import java.security.AccessController;
import java.security.AllPermission;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;

import jdk.internal.dynalink.beans.StaticClass;

/**
 * This class encapsulates the bytecode of the adapter class and can be used to load it into the JVM as an actual Class.
 * It can be invoked repeatedly to create multiple adapter classes from the same bytecode; adapter classes that have
 * class-level overrides must be re-created for every set of such overrides. Note that while this class is named
 * "class loader", it does not, in fact, extend {@code ClassLoader}, but rather uses them internally. Instances of this
 * class are normally created by {@link JavaAdapterBytecodeGenerator}.
 */
@SuppressWarnings("javadoc")
final class JavaAdapterClassLoader {
    private static final ProtectionDomain GENERATED_PROTECTION_DOMAIN = createGeneratedProtectionDomain();

    private final String className;
    private final byte[] classBytes;

    JavaAdapterClassLoader(String className, byte[] classBytes) {
        this.className = className.replace('/', '.');
        this.classBytes = classBytes;
    }

    /**
     * Loads the generated adapter class into the JVM.
     * @param parentLoader the parent class loader for the generated class loader
     * @return the generated adapter class
     */
    StaticClass generateClass(final ClassLoader parentLoader) {
        return AccessController.doPrivileged(new PrivilegedAction<StaticClass>() {
            @Override
            public StaticClass run() {
                try {
                    return StaticClass.forClass(Class.forName(className, true, createClassLoader(parentLoader)));
                } catch (final ClassNotFoundException e) {
                    throw new AssertionError(e); // cannot happen
                }
            }
        });
    }

    private static class AdapterLoader extends SecureClassLoader {
        AdapterLoader(ClassLoader parent) {
            super(parent);
        }
    }

    static boolean isAdapterClass(Class<?> clazz) {
        return clazz.getClassLoader() instanceof AdapterLoader;
    }

    // Note that the adapter class is created in the protection domain of the class/interface being
    // extended/implemented, and only the privileged global setter action class is generated in the protection domain
    // of Nashorn itself. Also note that the creation and loading of the global setter is deferred until it is
    // required by JVM linker, which will only happen on first invocation of any of the adapted method. We could defer
    // it even more by separating its invocation into a separate static method on the adapter class, but then someone
    // with ability to introspect on the class and use setAccessible(true) on it could invoke the method. It's a
    // security tradeoff...
    private ClassLoader createClassLoader(final ClassLoader parentLoader) {
        return new AdapterLoader(parentLoader) {
            private final ClassLoader myLoader = getClass().getClassLoader();

            @Override
            public Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                try {
                    return super.loadClass(name, resolve);
                } catch (final SecurityException se) {
                    // we may be implementing an interface or extending a class that was
                    // loaded by a loader that prevents package.access. If so, it'd throw
                    // SecurityException for nashorn's classes!. For adapter's to work, we
                    // should be able to refer to nashorn classes.
                    if (name.startsWith("jdk.nashorn.internal.")) {
                        return myLoader.loadClass(name);
                    }
                    throw se;
                }
            }

            @Override
            protected Class<?> findClass(final String name) throws ClassNotFoundException {
                if(name.equals(className)) {
                    return defineClass(name, classBytes, 0, classBytes.length, GENERATED_PROTECTION_DOMAIN);
                } else {
                    throw new ClassNotFoundException(name);
                }
            }
        };
    }

    private static ProtectionDomain createGeneratedProtectionDomain() {
        // Generated classes need to have AllPermission. Since we require the "createClassLoader" RuntimePermission, we
        // can create a class loader that'll load new classes with any permissions. Our generated classes are just
        // delegating adapters, so having AllPermission can't cause anything wrong; the effective set of permissions for
        // the executing script functions will still be limited by the permissions of the caller and the permissions of
        // the script.
        final Permissions permissions = new Permissions();
        permissions.add(new AllPermission());
        return new ProtectionDomain(new CodeSource(null, (CodeSigner[])null), permissions);
    }
}
