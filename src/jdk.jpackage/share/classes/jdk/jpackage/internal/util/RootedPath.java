/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A relative path (branch) rooted at the root.
 */
public sealed interface RootedPath {

    Path root();
    Path branch();

    default Path fullPath() {
        return root().resolve(branch());
    }

    public static Function<Path, RootedPath> toRootedPath(Path root) {
        return path -> {
            if (!path.startsWith(root)) {
                throw new IllegalArgumentException(String.format("Expected path [%s] to start with [%s] root", path, root));
            }
            return new Details.DefaultRootedPath(root, root.relativize(path), Files.isDirectory(path));
        };
    }

    public static void copy(Stream<RootedPath> rootedPaths, Path dest, CopyOption...options) throws IOException {
        Objects.requireNonNull(rootedPaths);
        Objects.requireNonNull(dest);

        var marks = new HashMap<Path, Details.PathMark>();

        // Preset marks for the preexisting paths.
        Files.walkFileTree(dest, new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                marks.put(dir, new Details.PathMark(true, true));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                marks.put(file, new Details.PathMark(false, true));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

        });

        var replacePreexisting = Set.of(options).contains(StandardCopyOption.REPLACE_EXISTING);

        Predicate<Details.PathMark> canReplace = v -> {
            return v.isPreexisting() && replacePreexisting;
        };

        try {
            rootedPaths.sequential().map(Details.DefaultRootedPath.class::cast).forEach(rootedPath -> {

                final var dstPath = dest.resolve(rootedPath.branch());

                var dstPathMark = marks.get(dstPath);

                if (!Optional.ofNullable(dstPathMark).map(canReplace::test).orElse(true)) {
                    // Destination path can not be replaced, bail out.
                    return;
                }

                // Check the ancestors of the destination path.
                for (var ancestor : Details.ancestorPaths(rootedPath.branch())) {
                    var mark = Optional.ofNullable(marks.get(dest.resolve(ancestor)));

                    if (!mark.map(Details.PathMark::isDirectory).orElse(true)) {
                        // `ancestor` is a file, don't overwrite it.
                        return;
                    }
                }

                dstPathMark = rootedPath.createPathMark();
                marks.put(dstPath, dstPathMark);

                try {
                    if (replacePreexisting && (rootedPath.isDirectory() != dstPathMark.isDirectory())) {
                        FileUtils.deleteRecursive(dstPath);
                    }

                    if (rootedPath.isDirectory()) {
                        Files.createDirectories(dstPath);
                    } else {
                        Files.createDirectories(dstPath.getParent());
                        Files.copy(rootedPath.fullPath(), dstPath, options);
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    static final class Details {

        private Details() {
        }

        private record DefaultRootedPath(Path root, Path branch, boolean isDirectory) implements RootedPath {

            DefaultRootedPath {
                Objects.requireNonNull(root);
                Objects.requireNonNull(branch);
                if (branch.isAbsolute()) {
                    throw new IllegalArgumentException();
                }
            }

            PathMark createPathMark() {
                return new PathMark(isDirectory);
            }
        }

        private record PathMark(boolean isDirectory, boolean isPreexisting) {
            PathMark(boolean isDirectory) {
                this(isDirectory, false);
            }
        }

        private static List<Path> ancestorPaths(Path path) {
            var ancestors = new ArrayList<Path>();
            while ((path = path.getParent()) != null) {
                ancestors.add(path);
            }
            return ancestors;
        }
    }
}
