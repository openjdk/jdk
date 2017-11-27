/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.tools.jjs;

import java.lang.reflect.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.nashorn.internal.runtime.Context;

/**
 * Abstract helper class to compute properties of a Java package object. Properties of
 * package object are (simple) top level class names in that java package and
 * immediate subpackages of that package.
 */
abstract class PackagesHelper {
    private final Context context;

    /**
     * Construct a new PackagesHelper.
     *
     * @param context the current Nashorn Context
     */
    PackagesHelper(final Context context) throws IOException {
        this.context = context;
    }

    static PackagesHelper create(final Context context) throws IOException {
        return isJavacHelperAvailable()? new JavacPackagesHelper(context) : new JrtPackagesHelper(context);
    }

    // LRU cache for java package properties lists
    private final LinkedHashMap<String, List<String>> propsCache =
        new LinkedHashMap<String, List<String>>(32, 0.75f, true) {
            private static final int CACHE_SIZE = 100;
            private static final long serialVersionUID = 1;

            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, List<String>> eldest) {
                return size() > CACHE_SIZE;
            }
        };

    /**
     * Return the list of properties of the given Java package or package prefix
     *
     * @param pkg Java package name or package prefix name
     * @return the list of properties of the given Java package or package prefix
     */
    final List<String> getPackageProperties(final String pkg) {
        // check the cache first
        if (propsCache.containsKey(pkg)) {
            return propsCache.get(pkg);
        }

        try {
            // make sorted list of properties
            final List<String> props = new ArrayList<>(listPackage(pkg));
            Collections.sort(props);
            propsCache.put(pkg, props);
            return props;
        } catch (final IOException exp) {
            if (Main.DEBUG) {
                exp.printStackTrace();
            }
            return Collections.<String>emptyList();
        }
    }

    /**
     * Close resources (like file system) used, if any.
     */
    abstract void close() throws IOException;

    /**
     * Return the set of properties of a given package object.
     *
     * @param pkg package start string
     * @return set of properties of the given Java package
     */
    abstract Set<String> listPackage(final String pkg) throws IOException;

    final boolean isClassAccessible(final String className) {
        try {
            final Class<?> clz = context.findClass(className);
            return Modifier.isPublic(clz.getModifiers());
        } catch (final ClassNotFoundException cnfe) {
        }
        return false;
    }

    final boolean isPackageAccessible(final String pkgName) {
        try {
            Context.checkPackageAccess(pkgName);
            return true;
        } catch (final SecurityException se) {
            return false;
        }
    }

    private static boolean isJavacHelperAvailable() {
        try {
            boolean result = JavacPackagesHelper.isAvailable();
            if (Main.DEBUG && !result) {
                System.err.println("javac packages helper is not available");
            }
            return result;
        } catch (final LinkageError err) {
            if (Main.DEBUG) {
                System.err.println("javac packages helper is not available");
                err.printStackTrace();
            }
            return false;
        }
    }
}
