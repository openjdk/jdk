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
package jdk.tools.jaotc.collect.module;

import jdk.tools.jaotc.collect.ClassSource;
import jdk.tools.jaotc.collect.FileSupport;
import jdk.tools.jaotc.collect.SearchPath;
import jdk.tools.jaotc.collect.SourceProvider;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class ModuleSourceProvider implements SourceProvider {
    private final FileSystem fileSystem;
    private final ClassLoader classLoader;
    private final FileSupport fileSupport;
    public final static String TYPE = "module";

    public ModuleSourceProvider() {
        this(FileSystems.getFileSystem(URI.create("jrt:/")), ClassLoader.getSystemClassLoader(), new FileSupport());
    }

    public ModuleSourceProvider(FileSystem fileSystem, ClassLoader classLoader, FileSupport fileSupport) {
        this.fileSystem = fileSystem;
        this.classLoader = classLoader;
        this.fileSupport = fileSupport;
    }

    @Override
    public ClassSource findSource(String name, SearchPath searchPath) {
        Path path = fileSystem.getPath(name);
        Path dir = fileSystem.getPath("modules");

        if (dir == null || !fileSupport.isDirectory(dir)) {
            return null;
        }

        Path found = findModuleDirectory(dir, path);

        if (found == null) {
            return null;
        }

        return new ModuleSource(found, classLoader);
    }

    private Path findModuleDirectory(Path root, Path path) {
        try {
            return fileSupport.getSubDirectory(fileSystem, root, path);
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    @Override
    public boolean supports(String type) {
        return TYPE.equals(type);
    }
}
