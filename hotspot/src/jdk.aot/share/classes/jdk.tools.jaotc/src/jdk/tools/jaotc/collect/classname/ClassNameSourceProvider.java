/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jaotc.collect.classname;

import jdk.tools.jaotc.collect.ClassSource;
import jdk.tools.jaotc.collect.FileSupport;
import jdk.tools.jaotc.collect.SearchPath;
import jdk.tools.jaotc.collect.SourceProvider;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ClassNameSourceProvider implements SourceProvider {
    public final static String TYPE = "class";
    private final ClassLoader classLoader;

    public ClassNameSourceProvider(FileSupport fileSupport) {
        String classPath = System.getProperty("java.class.path");
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (classPath != null && !classPath.isEmpty()) {
            classLoader = systemClassLoader;
        } else {
            Path path = Paths.get(".").toAbsolutePath();
            classLoader = fileSupport.createClassLoader(path, systemClassLoader);
        }
    }

    @Override
    public ClassSource findSource(String name, SearchPath searchPath) {
        Path path = Paths.get(name);
        if (ClassSource.pathIsClassFile(path)) {
            name = ClassSource.makeClassName(path);
        }
        try {
            classLoader.loadClass(name);
            return new ClassNameSource(name, classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public boolean supports(String type) {
        return TYPE.equals(type);
    }
}
