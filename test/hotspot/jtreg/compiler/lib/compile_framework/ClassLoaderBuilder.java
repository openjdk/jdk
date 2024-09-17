/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.compile_framework;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Build a ClassLoader that loads from classpath and {@code classesDir}.
 * Helper class that generates a ClassLoader which allows loading classes
 * from the classpath (see {@link Utils#getClassPaths()}) and {@code classesDir}.
 * <p>
 * The CompileFramework compiles all its classes to a specific {@code classesDir},
 * and this generated ClassLoader thus can be used to load those classes.
 */
class ClassLoaderBuilder {

    /**
     * Build a ClassLoader that loads from classpath and {@code classesDir}.
     */
    public static ClassLoader build(Path classesDir) {
        ClassLoader sysLoader = ClassLoader.getSystemClassLoader();

        try {
            // Classpath for all included classes (e.g. IR Framework).
            // Get all class paths, convert to URLs.
            List<URL> urls = new ArrayList<>();
            for (String path : Utils.getClassPaths()) {
                urls.add(new File(path).toURI().toURL());
            }
            // And add in the compiled classes from this instance of CompileFramework.
            urls.add(new File(classesDir.toString()).toURI().toURL());
            return URLClassLoader.newInstance(urls.toArray(URL[]::new), sysLoader);
        } catch (IOException e) {
            throw new CompileFrameworkException("IOException while creating ClassLoader", e);
        }
    }
}
