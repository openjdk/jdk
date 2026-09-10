/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.stream.Collectors.toUnmodifiableList;

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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Exploded directory.
 */
public record ExplodedPath(Path root, Collection<Node> children) {

    public ExplodedPath {
        Objects.requireNonNull(root);
        children.forEach(Objects::requireNonNull);
    }


    public record Node(Path path, boolean isDirectory) {

        public Node {
            if (path.isAbsolute()) {
                throw new IllegalArgumentException();
            }
        }

        public Node copyWithName(Path path) {
            return new Node(path, isDirectory());
        }
    }


    public record CopySpec(ExplodedPath source, Path dest) {

        public CopySpec {
            Objects.requireNonNull(source);
            Objects.requireNonNull(dest);
        }

        Stream<CopyPathSpec> copyPathSpecs() {
            return source.copyPathSpecs(dest);
        }
    }


    public ExplodedPath getParent() {
        var basename = root.getFileName();
        return new ExplodedPath(root.getParent(), children.stream().map(n -> {
            return n.copyWithName(basename.resolve(n.path()));
        }).collect(toUnmodifiableList()));
    }

    private Stream<CopyPathSpec> copyPathSpecs(Path dest) {
        Objects.requireNonNull(dest);
        return children.stream().sorted(Comparator.comparing(Node::path)).map(child -> {
            return new CopyPathSpec(root, child, dest);
        });
    }

    public static ExplodedPath of(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            return new ExplodedPath(root, walk.map(path -> {
                return new Node(root.relativize(path), Files.isDirectory(path));
            }).collect(toUnmodifiableList()));
        }
    }

    public static CopySpec copySpec(ExplodedPath source, Path dest) {
        return new CopySpec(source, dest);
    }

    public static void copy(List<CopySpec> specs, CopyOption...options) throws IOException {
        Objects.requireNonNull(specs);

        var marks = new HashMap<Path, PathMark>();

        // Preset marks for the preexisting paths.
        for (var spec : specs) {
            Files.walkFileTree(spec.dest(), new FileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    marks.put(dir, new PathMark(true, true));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    marks.put(file, new PathMark(false, true));
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
        }

        var replacePreexisting = Set.of(options).contains(StandardCopyOption.REPLACE_EXISTING);

        Predicate<PathMark> canReplace = v -> {
            return v.isPreexisting() && replacePreexisting;
        };

        try {
            specs.stream().flatMap(CopySpec::copyPathSpecs).forEach(spec -> {

                final var dstPathMark = marks.get(spec.resolvedDstPath());

                if (!Optional.ofNullable(dstPathMark).map(canReplace::test).orElse(true)) {
                    // Destination path can not be replaced, bail out.
                    return;
                }

                // Check the ancestors of the destination path.
                for (var ancestor : ancestorPaths(spec.child().path())) {
                    var mark = Optional.ofNullable(marks.get(spec.destRoot().resolve(ancestor)));

                    if (!mark.map(PathMark::isDirectory).orElse(true)) {
                        // `ancestor` is a file, don't overwrite it.
                        return;
                    }
                }

                marks.put(spec.resolvedDstPath(), spec.createPathMark());

                try {
                    if (dstPathMark != null && replacePreexisting && (spec.child().isDirectory() != dstPathMark.isDirectory())) {
                        FileUtils.deleteRecursive(spec.resolvedDstPath());
                    }

                    if (spec.child().isDirectory()) {
                        Files.createDirectories(spec.resolvedDstPath());
                    } else {
                        Files.createDirectories(spec.resolvedDstPath().getParent());
                        Files.copy(spec.resolvedSrcPath(), spec.resolvedDstPath(), options);
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private static Collection<Path> ancestorPaths(Path path) {
        var ancestors = new ArrayList<Path>();

        if (!EMPTY_PATH.equals(path)) {
            ancestors.add(EMPTY_PATH);
        }

        while ((path = path.getParent()) != null) {
            ancestors.add(path);
        }

        return ancestors;
    }


    private record PathMark(boolean isDirectory, boolean isPreexisting) {
        PathMark(boolean isDirectory) {
            this(isDirectory, false);
        }
    }


    private record CopyPathSpec(Path srcRoot, Node child, Path destRoot) {

        CopyPathSpec {
            Objects.requireNonNull(srcRoot);
            Objects.requireNonNull(child);
            Objects.requireNonNull(destRoot);
        }

        Path resolvedSrcPath() {
            return srcRoot.resolve(child.path());
        }

        Path resolvedDstPath() {
            return destRoot.resolve(child.path());
        }

        PathMark createPathMark() {
            return new PathMark(child.isDirectory());
        }
    }

    private static final Path EMPTY_PATH = Path.of("");
}
