/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.hotspot.tools.ctw;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Handler for jimage-files containing classes to compile.
 */
public class ClassPathJimageEntry extends PathHandler.PathEntry {

    @Override
    protected Stream<String> classes() {
        Path modulesRoot = jrtFileSystem.getPath("/modules");
        List<String> classNames = new ArrayList<>();
        FileVisitor<Path> collectClassNames = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                classNames.add(Utils.fileNameToClassName(modulesRoot.relativize(path).toString()));
                return FileVisitResult.CONTINUE;
            }
        };
        try {
            Files.walkFileTree(modulesRoot, collectClassNames);
        } catch (IOException e) {
            throw new Error(e);
        }
        return classNames.stream();
    }

    @Override
    protected String description() {
        return "# jimage: " + root;
    }

    @Override
    public void close() {
        try {
            jrtFileSystem.close();
        } catch (IOException e) {
            throw new Error("error on closing reader for " + root + " : "
                    + e.getMessage(), e);
        } finally {
            super.close();
        }
    }

    private final FileSystem jrtFileSystem;

    public ClassPathJimageEntry(Path root) {
        super(root);
        if (!Files.exists(root)) {
            throw new Error(root + " image file not found");
        }
        try {
            jrtFileSystem = FileSystemProvider.installedProviders().stream()
                    .filter(p -> "jrt".equals(p.getScheme()))
                    .findFirst()
                    .orElseThrow(() -> new Error("cannot find JRT filesystem for " + root))
                    .newFileSystem(root, Map.of());
        } catch (IOException e) {
            throw new Error("can not open " + root + " : " + e.getMessage(), e);
        }
    }

    @Override
    protected byte[] findByteCode(String name) {
        // Relative path to search for inside each /modules/<module> directory.
        Path resourcePath = jrtFileSystem.getPath(Utils.classNameToFileName(name));
        Path modulesRoot = jrtFileSystem.getPath("/modules");
        try (DirectoryStream<Path> modules = Files.newDirectoryStream(modulesRoot)) {
            for (Path module : modules) {
                Path p = module.resolve(resourcePath);
                if (Files.isRegularFile(p)) {
                    return Files.readAllBytes(p);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
