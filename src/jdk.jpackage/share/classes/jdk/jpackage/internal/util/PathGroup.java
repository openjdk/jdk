/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Group of paths. Each path in the group is assigned a unique id.
 */
public final class PathGroup {

    /**
     * Creates path group with the initial paths.
     *
     * @param paths the initial paths
     */
    public PathGroup(Map<Object, Path> paths) {
        paths.keySet().forEach(Objects::requireNonNull);
        paths.values().forEach(Objects::requireNonNull);
        entries = new HashMap<>(paths);
    }

    /**
     * Returns a path associated with the given identifier in this path group.
     *
     * @param id the identifier
     * @return the path corresponding to the given identifier in this path group or
     *         <code>null</code> if there is no such path
     */
    public Path getPath(Object id) {
        Objects.requireNonNull(id);
        return entries.get(id);
    }

    /**
     * Assigns the specified path value to the given identifier in this path group.
     * If the given identifier doesn't exist in this path group, it is added,
     * otherwise, the current value associated with the identifier is replaced with
     * the given path value. If the path value is <code>null</code> the given
     * identifier is removed from this path group if it existed; otherwise, no
     * action is taken.
     *
     * @param id   the identifier
     * @param path the path to associate with the identifier or <code>null</code>
     */
    public void setPath(Object id, Path path) {
        Objects.requireNonNull(id);
        if (path != null) {
            entries.put(id, path);
        } else {
            entries.remove(id);
        }
    }

    /**
     * Adds a path associated with the new unique identifier to this path group.
     *
     * @param path the path to associate the new unique identifier in this path
     *             group
     */
    public void ghostPath(Path path) {
        Objects.requireNonNull(path);
        setPath(new Object(), path);
    }

    /**
     * Gets all identifiers of this path group.
     * <p>
     * The order of identifiers in the returned list is undefined.
     *
     * @return all identifiers of this path group
     */
    public Set<Object> keys() {
        return entries.keySet();
    }

    /**
     * Gets paths associated with all identifiers in this path group.
     * <p>
     * The order of paths in the returned list is undefined.
     *
     * @return paths associated with all identifiers in this path group
     */
    public List<Path> paths() {
        return entries.values().stream().toList();
    }

    /**
     * Gets root paths in this path group.
     * <p>
     * If multiple identifiers are associated with the same path value in the group,
     * the path value is added to the returned list only once. Paths that are
     * descendants of other paths in the group are not added to the returned list.
     * <p>
     * The order of paths in the returned list is undefined.
     *
     * @return unique root paths in this path group
     */
    public List<Path> roots() {
        if (entries.isEmpty()) {
            return List.of();
        }

        // Sort by the number of path components in descending order.
        final var sorted = entries.entrySet().stream().map(e -> {
            return Map.entry(e.getValue().normalize(), e.getValue());
        }).sorted(Comparator.comparingInt(e -> e.getValue().getNameCount() * -1)).distinct().toList();

        final var shortestNormalizedPath = sorted.getLast().getKey();
        if (shortestNormalizedPath.getNameCount() == 1 && shortestNormalizedPath.getFileName().toString().isEmpty()) {
            return List.of(sorted.getLast().getValue());
        }

        final List<Path> roots = new ArrayList<>();

        for (int i = 0; i < sorted.size(); ++i) {
            final var path = sorted.get(i).getKey();
            boolean pathIsRoot = true;
            for (int j = i + 1; j < sorted.size(); ++j) {
                final var maybeParent = sorted.get(j).getKey();
                if (path.getNameCount() > maybeParent.getNameCount() && path.startsWith(maybeParent)) {
                    pathIsRoot = false;
                    break;
                }
            }

            if (pathIsRoot) {
                roots.add(sorted.get(i).getValue());
            }
        }

        return roots;
    }

    /**
     * Gets the number of bytes in root paths of this path group. The method sums
     * the size of all root path entries in the group. If the path entry is a
     * directory it calculates the total size of the files in the directory. If the
     * path entry is a file, it takes its size.
     *
     * @return the total number of bytes in root paths of this path group
     * @throws IOException If an I/O error occurs
     */
    public long sizeInBytes() throws IOException {
        long reply = 0;
        final var roots = roots();
        try {
            for (Path dir : roots.stream().filter(Files::isDirectory).toList()) {
                try (Stream<Path> stream = Files.walk(dir)) {
                    reply += stream.mapToLong(PathGroup::sizeInBytes).sum();
                }
            }
            reply += roots.stream().mapToLong(PathGroup::sizeInBytes).sum();
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
        return reply;
    }

    private static long sizeInBytes(Path path) throws UncheckedIOException {
        if (Files.isRegularFile(path)) {
            try {
                return Files.size(path);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        } else {
            return 0;
        }
    }

    /**
     * Creates a copy of this path group with all paths resolved against the given
     * root. Taken action is equivalent to creating a copy of this path group and
     * calling <code>root.resolve()</code> on every path in the copy.
     *
     * @param root the root against which to resolve paths
     *
     * @return a new path group resolved against the given root path
     */
    public PathGroup resolveAt(Path root) {
        Objects.requireNonNull(root);
        return new PathGroup(entries.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> root.resolve(e.getValue()))));
    }

    /**
     * Copies files/directories from the locations in the path group into the
     * locations of the given path group. For every identifier found in this and the
     * given group, copy the associated file or directory from the location
     * specified by the path value associated with the identifier in this group into
     * the location associated with the identifier in the given group.
     *
     * @param dst the destination path group
     * @throws IOException If an I/O error occurs
     */
    public void copy(PathGroup dst, CopyOption ...options) throws IOException {
        final var handler = new Copy(false, options);
        copy(this, dst, handler, handler.followSymlinks());
    }

    /**
     * Similar to {@link #copy(PathGroup, CopyOption...)} but moves files/directories instead of
     * copying.
     *
     * @param dst the destination path group
     * @throws IOException If an I/O error occurs
     */
    public void move(PathGroup dst, CopyOption ...options) throws IOException {
        final var handler = new Copy(true, options);
        copy(this, dst, handler, handler.followSymlinks());
        deleteEntries();
    }

    /**
     * Similar to {@link #copy(PathGroup, CopyOption...)} but uses the given handler to transform
     * paths instead of coping.
     *
     * @param dst the destination path group
     * @param handler the path transformation handler
     * @throws IOException If an I/O error occurs
     */
    public void transform(PathGroup dst, TransformHandler handler) throws IOException {
        copy(this, dst, handler, false);
    }

    /**
     * Handler of file copying and directory creating.
     *
     * @see #transform
     */
    public static interface TransformHandler {

        /**
         * Request to copy a file from the given source location into the given
         * destination location.
         *
         * @implNote Default implementation takes no action
         *
         * @param src the source file location
         * @param dst the destination file location
         * @throws IOException If an I/O error occurs
         */
        default void copyFile(Path src, Path dst) throws IOException {

        }

        /**
         * Request to create a directory at the given location.
         *
         * @implNote Default implementation takes no action
         *
         * @param dir the path where the directory is requested to be created
         * @throws IOException
         */
        default void createDirectory(Path dir) throws IOException {

        }

        /**
         * Request to copy a symbolic link from the given source location into the given
         * destination location.
         *
         * @implNote Default implementation calls {@link #copyFile}.
         *
         * @param src the source symbolic link location
         * @param dst the destination symbolic link location
         * @throws IOException If an I/O error occurs
         */
        default void copySymbolicLink(Path src, Path dst) throws IOException {
            copyFile(src, dst);
        }
    }

    private void deleteEntries() throws IOException {
        for (final var file : entries.values()) {
            if (Files.isDirectory(file)) {
                FileUtils.deleteRecursive(file);
            } else {
                Files.deleteIfExists(file);
            }
        }
    }

    private record CopySpec(Path basepath, Path from, Path to) {
        CopySpec {
            Objects.requireNonNull(basepath);
            Objects.requireNonNull(to);
            if (!from.startsWith(basepath)) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if ((obj == null) || (getClass() != obj.getClass())) {
                return false;
            }
            CopySpec other = (CopySpec) obj;
            return Objects.equals(from, other.from) && Objects.equals(to, other.to);
        }

        Path fromNormalized() {
            return from().normalize();
        }

        Path toNormalized() {
            return to().normalize();
        }

        CopySpec(Path from, Path to) {
            this(from, from, to);
        }
    }

    private static void copy(PathGroup src, PathGroup dst, TransformHandler handler, boolean followSymlinks) throws IOException {
        List<CopySpec> copySpecs = new ArrayList<>();
        List<Path> excludePaths = new ArrayList<>();

        for (final var e : src.entries.entrySet()) {
            final var srcPath = e.getValue();
            final var dstPath = dst.entries.get(e.getKey());
            if (dstPath != null) {
                copySpecs.add(new CopySpec(srcPath, dstPath));
            } else {
                excludePaths.add(srcPath.normalize());
            }
        }

        copy(copySpecs, excludePaths, handler, followSymlinks);
    }

    private record Copy(boolean move, boolean followSymlinks, CopyOption ... options) implements TransformHandler {

        Copy(boolean move, CopyOption ... options) {
            this(move, !Set.of(options).contains(LinkOption.NOFOLLOW_LINKS), options);
        }

        @Override
        public void copyFile(Path src, Path dst) throws IOException {
            Files.createDirectories(dst.getParent());
            if (move) {
                Files.move(src, dst, options);
            } else {
                Files.copy(src, dst, options);
            }
        }

        @Override
        public void createDirectory(Path dir) throws IOException {
            Files.createDirectories(dir);
        }
    }

    private static boolean match(Path what, List<Path> paths) {
        return paths.stream().anyMatch(what::startsWith);
    }

    private static void copy(List<CopySpec> copySpecs, List<Path> excludePaths,
            TransformHandler handler, boolean followSymlinks) throws IOException {
        Objects.requireNonNull(excludePaths);
        Objects.requireNonNull(handler);


        final var filteredCopySpecs = copySpecs.stream().<CopySpec>mapMulti((copySpec, consumer) -> {
            final var src = copySpec.from();

            if (!Files.exists(src) || match(src, excludePaths)) {
                return;
            }

            if (Files.isDirectory(copySpec.from())) {
                final var dst = copySpec.to;
                final var walkMode = followSymlinks ? new FileVisitOption[] { FileVisitOption.FOLLOW_LINKS } : new FileVisitOption[0];
                try (final var files = Files.walk(src, walkMode)) {
                    files.filter(file -> {
                        return !match(file, excludePaths);
                    }).map(file -> {
                        return new CopySpec(src, file, dst.resolve(src.relativize(file)));
                    }).toList().forEach(consumer::accept);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            } else {
                consumer.accept(copySpec);
            }
        }).collect(groupingBy(CopySpec::fromNormalized, collectingAndThen(toSet(), copySpecGroup -> {
            return copySpecGroup.stream().filter(copySpec -> {
                for (final var otherCopySpec : copySpecGroup) {
                    if (otherCopySpec != copySpec && !otherCopySpec.basepath().equals(copySpec.basepath())
                            && otherCopySpec.basepath().startsWith(copySpec.basepath())) {
                        return false;
                    }
                }
                return true;
            }).toList();
        }))).values().stream().flatMap(Collection::stream).toList();

        filteredCopySpecs.stream().collect(toMap(CopySpec::toNormalized, x -> x, (x, y) -> {
            throw new IllegalStateException(String.format(
                    "Duplicate source files [%s] and [%s] for [%s] destination file", x.from(), y.from(), x.to()));
        }));

        try {
            filteredCopySpecs.stream().forEach(copySpec -> {
                try {
                    if (Files.isSymbolicLink(copySpec.from())) {
                        handler.copySymbolicLink(copySpec.from(), copySpec.to());
                    } else if (Files.isDirectory(copySpec.from())) {
                        handler.createDirectory(copySpec.to());
                    } else {
                        handler.copyFile(copySpec.from(), copySpec.to());
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private final Map<Object, Path> entries;
}
