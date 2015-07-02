/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package java.security;

import java.util.Map;
import java.util.ArrayList;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import sun.security.util.Debug;

/**
 * This class extends ClassLoader with additional support for defining
 * classes with an associated code source and permissions which are
 * retrieved by the system policy by default.
 *
 * @author  Li Gong
 * @author  Roland Schemers
 */
public class SecureClassLoader extends ClassLoader {
    /*
     * If initialization succeed this is set to true and security checks will
     * succeed. Otherwise the object is not initialized and the object is
     * useless.
     */
    private final boolean initialized;

    /*
     * Map that maps the CodeSource URL (as a String) to ProtectionDomain.
     * We use a String instead of a CodeSource/URL as the key to avoid
     * potential expensive name service lookups. This does mean that URLs that
     * are equivalent after nameservice lookup will be placed in separate
     * ProtectionDomains; however during policy enforcement these URLs will be
     * canonicalized and resolved resulting in a consistent set of granted
     * permissions.
     */
    private final Map<String, ProtectionDomain> pdcache
            = new ConcurrentHashMap<>(11);

    private static final Debug debug = Debug.getInstance("scl");

    static {
        ClassLoader.registerAsParallelCapable();
    }

    /**
     * Creates a new SecureClassLoader using the specified parent
     * class loader for delegation.
     *
     * <p>If there is a security manager, this method first
     * calls the security manager's {@code checkCreateClassLoader}
     * method  to ensure creation of a class loader is allowed.
     *
     * @param parent the parent ClassLoader
     * @exception  SecurityException  if a security manager exists and its
     *             {@code checkCreateClassLoader} method doesn't allow
     *             creation of a class loader.
     * @see SecurityManager#checkCreateClassLoader
     */
    protected SecureClassLoader(ClassLoader parent) {
        super(parent);
        // this is to make the stack depth consistent with 1.1
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        initialized = true;
    }

    /**
     * Creates a new SecureClassLoader using the default parent class
     * loader for delegation.
     *
     * <p>If there is a security manager, this method first
     * calls the security manager's {@code checkCreateClassLoader}
     * method  to ensure creation of a class loader is allowed.
     *
     * @exception  SecurityException  if a security manager exists and its
     *             {@code checkCreateClassLoader} method doesn't allow
     *             creation of a class loader.
     * @see SecurityManager#checkCreateClassLoader
     */
    protected SecureClassLoader() {
        super();
        // this is to make the stack depth consistent with 1.1
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        initialized = true;
    }

    /**
     * Converts an array of bytes into an instance of class Class,
     * with an optional CodeSource. Before the
     * class can be used it must be resolved.
     * <p>
     * If a non-null CodeSource is supplied a ProtectionDomain is
     * constructed and associated with the class being defined.
     *
     * @param      name the expected name of the class, or {@code null}
     *                  if not known, using '.' and not '/' as the separator
     *                  and without a trailing ".class" suffix.
     * @param      b    the bytes that make up the class data. The bytes in
     *             positions {@code off} through {@code off+len-1}
     *             should have the format of a valid class file as defined by
     *             <cite>The Java&trade; Virtual Machine Specification</cite>.
     * @param      off  the start offset in {@code b} of the class data
     * @param      len  the length of the class data
     * @param      cs   the associated CodeSource, or {@code null} if none
     * @return the {@code Class} object created from the data,
     *         and optional CodeSource.
     * @exception  ClassFormatError if the data did not contain a valid class
     * @exception  IndexOutOfBoundsException if either {@code off} or
     *             {@code len} is negative, or if
     *             {@code off+len} is greater than {@code b.length}.
     *
     * @exception  SecurityException if an attempt is made to add this class
     *             to a package that contains classes that were signed by
     *             a different set of certificates than this class, or if
     *             the class name begins with "java.".
     */
    protected final Class<?> defineClass(String name,
                                         byte[] b, int off, int len,
                                         CodeSource cs)
    {
        return defineClass(name, b, off, len, getProtectionDomain(cs));
    }

    /**
     * Converts a {@link java.nio.ByteBuffer ByteBuffer}
     * into an instance of class {@code Class}, with an optional CodeSource.
     * Before the class can be used it must be resolved.
     * <p>
     * If a non-null CodeSource is supplied a ProtectionDomain is
     * constructed and associated with the class being defined.
     *
     * @param      name the expected name of the class, or {@code null}
     *                  if not known, using '.' and not '/' as the separator
     *                  and without a trailing ".class" suffix.
     * @param      b    the bytes that make up the class data.  The bytes from positions
     *                  {@code b.position()} through {@code b.position() + b.limit() -1}
     *                  should have the format of a valid class file as defined by
     *                  <cite>The Java&trade; Virtual Machine Specification</cite>.
     * @param      cs   the associated CodeSource, or {@code null} if none
     * @return the {@code Class} object created from the data,
     *         and optional CodeSource.
     * @exception  ClassFormatError if the data did not contain a valid class
     * @exception  SecurityException if an attempt is made to add this class
     *             to a package that contains classes that were signed by
     *             a different set of certificates than this class, or if
     *             the class name begins with "java.".
     *
     * @since  1.5
     */
    protected final Class<?> defineClass(String name, java.nio.ByteBuffer b,
                                         CodeSource cs)
    {
        return defineClass(name, b, getProtectionDomain(cs));
    }

    /**
     * Returns the permissions for the given CodeSource object.
     * <p>
     * This method is invoked by the defineClass method which takes
     * a CodeSource as an argument when it is constructing the
     * ProtectionDomain for the class being defined.
     *
     * @param codesource the codesource.
     *
     * @return the permissions granted to the codesource.
     *
     */
    protected PermissionCollection getPermissions(CodeSource codesource)
    {
        check();
        return new Permissions(); // ProtectionDomain defers the binding
    }

    /*
     * Returned cached ProtectionDomain for the specified CodeSource.
     */
    private ProtectionDomain getProtectionDomain(CodeSource cs) {
        if (cs == null) {
            return null;
        }

        // Use a String form of the URL as the key. It should behave in the
        // same manner as the URL when compared for equality except that no
        // nameservice lookup is done on the hostname (String comparison
        // only), and the fragment is not considered.
        String key = cs.getLocationNoFragString();
        if (key == null) {
            key = "<null>";
        }
        return pdcache.computeIfAbsent(key, new Function<>() {
            @Override
            public ProtectionDomain apply(String key /* not used */) {
                PermissionCollection perms
                        = SecureClassLoader.this.getPermissions(cs);
                ProtectionDomain pd = new ProtectionDomain(
                        cs, perms, SecureClassLoader.this, null);
                if (debug != null) {
                    debug.println(" getPermissions " + pd);
                    debug.println("");
                }
                return pd;
            }
        });
    }

    /*
     * Check to make sure the class loader has been initialized.
     */
    private void check() {
        if (!initialized) {
            throw new SecurityException("ClassLoader object not initialized");
        }
    }

}
