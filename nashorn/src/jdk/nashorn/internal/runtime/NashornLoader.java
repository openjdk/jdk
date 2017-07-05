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

package jdk.nashorn.internal.runtime;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.SecureClassLoader;
import jdk.nashorn.tools.Shell;

/**
 * Superclass for Nashorn class loader classes.
 */
abstract class NashornLoader extends SecureClassLoader {
    private static final String OBJECTS_PKG        = "jdk.nashorn.internal.objects";
    private static final String RUNTIME_PKG        = "jdk.nashorn.internal.runtime";
    private static final String RUNTIME_ARRAYS_PKG = "jdk.nashorn.internal.runtime.arrays";
    private static final String RUNTIME_LINKER_PKG = "jdk.nashorn.internal.runtime.linker";
    private static final String SCRIPTS_PKG        = "jdk.nashorn.internal.scripts";

    private static final Permission[] SCRIPT_PERMISSIONS;

    static {
        /*
         * Generated classes get access to runtime, runtime.linker, objects, scripts packages.
         * Note that the actual scripts can not access these because Java.type, Packages
         * prevent these restricted packages. And Java reflection and JSR292 access is prevented
         * for scripts. In other words, nashorn generated portions of script classes can access
         * classes in these implementation packages.
         */
        SCRIPT_PERMISSIONS = new Permission[] {
                new RuntimePermission("accessClassInPackage." + RUNTIME_PKG),
                new RuntimePermission("accessClassInPackage." + RUNTIME_LINKER_PKG),
                new RuntimePermission("accessClassInPackage." + OBJECTS_PKG),
                new RuntimePermission("accessClassInPackage." + SCRIPTS_PKG),
                new RuntimePermission("accessClassInPackage." + RUNTIME_ARRAYS_PKG)
        };
    }

    NashornLoader(final ClassLoader parent) {
        super(parent);
    }

    protected static void checkPackageAccess(final String name) {
        final int i = name.lastIndexOf('.');
        if (i != -1) {
            final SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                final String pkgName = name.substring(0, i);
                switch (pkgName) {
                    case RUNTIME_PKG:
                    case RUNTIME_ARRAYS_PKG:
                    case RUNTIME_LINKER_PKG:
                    case OBJECTS_PKG:
                    case SCRIPTS_PKG:
                        // allow it.
                        break;
                    default:
                        sm.checkPackageAccess(pkgName);
                }
            }
        }
    }

    @Override
    protected PermissionCollection getPermissions(final CodeSource codesource) {
        final Permissions permCollection = new Permissions();
        for (final Permission perm : SCRIPT_PERMISSIONS) {
            permCollection.add(perm);
        }
        return permCollection;
    }

    /**
     * Create a secure URL class loader for the given classpath
     * @param classPath classpath for the loader to search from
     * @return the class loader
     */
    static ClassLoader createClassLoader(final String classPath) {
        final ClassLoader parent = Shell.class.getClassLoader();
        final URL[] urls = pathToURLs(classPath);
        return URLClassLoader.newInstance(urls, parent);
    }

    /*
     * Utility method for converting a search path string to an array
     * of directory and JAR file URLs.
     *
     * @param path the search path string
     * @return the resulting array of directory and JAR file URLs
     */
    private static URL[] pathToURLs(final String path) {
        final String[] components = path.split(File.pathSeparator);
        URL[] urls = new URL[components.length];
        int count = 0;
        while(count < components.length) {
            final URL url = fileToURL(new File(components[count]));
            if (url != null) {
                urls[count++] = url;
            }
        }
        if (urls.length != count) {
            final URL[] tmp = new URL[count];
            System.arraycopy(urls, 0, tmp, 0, count);
            urls = tmp;
        }
        return urls;
    }

    /*
     * Returns the directory or JAR file URL corresponding to the specified
     * local file name.
     *
     * @param file the File object
     * @return the resulting directory or JAR file URL, or null if unknown
     */
    private static URL fileToURL(final File file) {
        String name;
        try {
            name = file.getCanonicalPath();
        } catch (final IOException e) {
            name = file.getAbsolutePath();
        }
        name = name.replace(File.separatorChar, '/');
        if (!name.startsWith("/")) {
            name = "/" + name;
        }
        // If the file does not exist, then assume that it's a directory
        if (!file.isFile()) {
            name = name + "/";
        }
        try {
            return new URL("file", "", name);
        } catch (final MalformedURLException e) {
            throw new IllegalArgumentException("file");
        }
    }
}

