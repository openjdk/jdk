/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * A helper class to compute properties of a Java package object. Properties of
 * package object are (simple) top level class names in that java package and
 * immediate subpackages of that package.
 */
final class PackagesHelper {
    // JavaCompiler may be null on certain platforms (eg. JRE)
    private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    /**
     * Is Java package properties helper available?
     *
     * @return true if package properties support is available
     */
    static boolean isAvailable() {
        return compiler != null;
    }

    private final StandardJavaFileManager fm;
    private final Set<JavaFileObject.Kind> fileKinds;

    /**
     * Construct a new PackagesHelper.
     *
     * @param classPath Class path to compute properties of java package objects
     */
    PackagesHelper(final String classPath) throws IOException {
        assert isAvailable() : "no java compiler found!";

        fm = compiler.getStandardFileManager(null, null, null);
        fileKinds = EnumSet.of(JavaFileObject.Kind.CLASS);

        if (classPath != null && !classPath.isEmpty()) {
            fm.setLocation(StandardLocation.CLASS_PATH, getFiles(classPath));
        } else {
            // no classpath set. Make sure that it is empty and not any default like "."
            fm.setLocation(StandardLocation.CLASS_PATH, Collections.<File>emptyList());
        }
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
    List<String> getPackageProperties(final String pkg) {
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

    public void close() throws IOException {
        fm.close();
    }

    private Set<String> listPackage(final String pkg) throws IOException {
        final Set<String> props = new HashSet<>();
        listPackage(StandardLocation.PLATFORM_CLASS_PATH, pkg, props);
        listPackage(StandardLocation.CLASS_PATH, pkg, props);
        return props;
    }

    private void listPackage(final Location loc, final String pkg, final Set<String> props)
            throws IOException {
        for (JavaFileObject file : fm.list(loc, pkg, fileKinds, true)) {
            final String binaryName = fm.inferBinaryName(loc, file);
            // does not start with the given package prefix
            if (!binaryName.startsWith(pkg + ".")) {
                continue;
            }

            final int nextDot = binaryName.indexOf('.', pkg.length() + 1);
            final int start = pkg.length() + 1;

            if (nextDot != -1) {
                // subpackage - eg. "regex" for "java.util"
                props.add(binaryName.substring(start, nextDot));
            } else {
                // class - filter out nested, inner, anonymous, local classes.
                // Dynalink supported public nested classes as properties of
                // StaticClass object anyway. We don't want to expose those
                // "$" internal names as properties of package object.

                final String clsName = binaryName.substring(start);
                if (clsName.indexOf('$') == -1) {
                    props.add(clsName);
                }
            }
        }
    }

    // return list of File objects for the given class path
    private static List<File> getFiles(final String classPath) {
        return Stream.of(classPath.split(File.pathSeparator))
                    .map(File::new)
                    .collect(Collectors.toList());
    }
}
