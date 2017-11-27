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

package jdk.nashorn.tools.jjs;

import java.io.IOException;
import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import jdk.nashorn.internal.runtime.Context;

/**
 * A javac package helper that uses javac to complete package names.
 */
final class JavacPackagesHelper extends PackagesHelper {
    // JavaCompiler may be null on certain platforms (eg. JRE)
    private static final JavaCompiler compiler;
    static {
        // Use javac only if security manager is not around!
        compiler = System.getSecurityManager() == null? ToolProvider.getSystemJavaCompiler() : null;
    }

    /**
     * Is this class available?
     *
     * @return true if javac is available
     */
    static boolean isAvailable() {
        return compiler != null;
    }

    private final boolean modulePathSet;
    private final StandardJavaFileManager fm;
    private final Set<JavaFileObject.Kind> fileKinds;

    /**
     * Construct a new JavacPackagesHelper.
     *
     * @param context the current Nashorn Context
     */
    JavacPackagesHelper(final Context context) throws IOException {
        super(context);
        final String modulePath = context.getEnv()._module_path;
        this.modulePathSet = modulePath != null && !modulePath.isEmpty();
        if (isAvailable()) {
            final String classPath = context.getEnv()._classpath;
            fm = compiler.getStandardFileManager(null, null, null);
            fileKinds = EnumSet.of(JavaFileObject.Kind.CLASS);

            if (this.modulePathSet) {
                fm.setLocation(StandardLocation.MODULE_PATH, getFiles(modulePath));
            }

            if (classPath != null && !classPath.isEmpty()) {
                fm.setLocation(StandardLocation.CLASS_PATH, getFiles(classPath));
            } else {
                // no classpath set. Make sure that it is empty and not any default like "."
                fm.setLocation(StandardLocation.CLASS_PATH, Collections.<File>emptyList());
            }
        } else {
            // javac is not available - caller should have checked!
            throw new IllegalStateException("JavacPackagesHelper is not available!");
        }
    }


    @Override
    void close() throws IOException {
        if (fm != null) {
            fm.close();
        }
    }

    @Override
    Set<String> listPackage(final String pkg) throws IOException {
        final Set<String> props = new HashSet<>();
        listPackage(StandardLocation.PLATFORM_CLASS_PATH, pkg, props);
        if (this.modulePathSet) {
            for (Set<Location> locs : fm.listLocationsForModules(StandardLocation.MODULE_PATH)) {
                for (Location loc : locs) {
                    listPackage(loc, pkg, props);
                }
            }
        }
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
                final String pkgName = binaryName.substring(start, nextDot);
                if (isPackageAccessible(binaryName.substring(0, nextDot))) {
                    props.add(binaryName.substring(start, nextDot));
                }
            } else {
                // class - filter out nested, inner, anonymous, local classes.
                // Dynalink supported public nested classes as properties of
                // StaticClass object anyway. We don't want to expose those
                // "$" internal names as properties of package object.

                final String clsName = binaryName.substring(start);
                if (clsName.indexOf('$') == -1 && isClassAccessible(binaryName)) {
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
