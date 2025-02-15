/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.FileUtils;

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
        // Sort by the number of path components in ascending order.
        List<Map.Entry<Path, Path>> sorted = normalizedPaths().stream()
                .sorted((a, b) -> a.getKey().getNameCount() - b.getKey().getNameCount()).toList();

        // Returns `true` if `a` is a parent of `b`
        BiFunction<Map.Entry<Path, Path>, Map.Entry<Path, Path>, Boolean> isParentOrSelf = (a, b) -> {
            return a == b || b.getKey().startsWith(a.getKey());
        };

        return sorted.stream().filter(
                v -> v == sorted.stream().sequential().filter(v2 -> isParentOrSelf.apply(v2, v)).findFirst().get())
                .map(v -> v.getValue()).toList();
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
        for (Path dir : roots().stream().filter(f -> Files.isDirectory(f)).collect(Collectors.toList())) {
            try (Stream<Path> stream = Files.walk(dir)) {
                reply += stream.filter(p -> Files.isRegularFile(p)).mapToLong(f -> f.toFile().length()).sum();
            }
        }
        return reply;
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
                .collect(Collectors.toMap(e -> e.getKey(), e -> root.resolve(e.getValue()))));
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
    public void copy(PathGroup dst) throws IOException {
        copy(this, dst, null, false);
    }

    /**
     * Similar to {@link #copy(PathGroup)} but moves files/directories instead of
     * copying.
     *
     * @param dst the destination path group
     * @throws IOException If an I/O error occurs
     */
    public void move(PathGroup dst) throws IOException {
        copy(this, dst, null, true);
    }

    /**
     * Similar to {@link #copy(PathGroup)} but uses the given handler to transform
     * paths instead of coping.
     *
     * @param dst the destination path group
     * @param handler the path transformation handler
     * @throws IOException If an I/O error occurs
     */
    public void transform(PathGroup dst, TransformHandler handler) throws IOException {
        Objects.requireNonNull(handler);
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
    }

    private static void copy(PathGroup src, PathGroup dst, TransformHandler handler, boolean move) throws IOException {
        List<Map.Entry<Path, Path>> copyItems = new ArrayList<>();
        List<Path> excludeItems = new ArrayList<>();

        for (var id : src.entries.keySet()) {
            Path srcPath = src.entries.get(id);
            if (dst.entries.containsKey(id)) {
                copyItems.add(Map.entry(srcPath, dst.entries.get(id)));
            } else {
                excludeItems.add(srcPath);
            }
        }

        copy(move, copyItems, excludeItems, handler);
    }

    private static void copy(boolean move, List<Map.Entry<Path, Path>> entries, List<Path> excludePaths,
            TransformHandler handler) throws IOException {

        if (handler == null) {
            handler = new TransformHandler() {
                @Override
                public void copyFile(Path src, Path dst) throws IOException {
                    Files.createDirectories(dst.getParent());
                    if (move) {
                        Files.move(src, dst);
                    } else {
                        Files.copy(src, dst);
                    }
                }

                @Override
                public void createDirectory(Path dir) throws IOException {
                    Files.createDirectories(dir);
                }
            };
        }

        // destination -> source file mapping
        Map<Path, Path> actions = new HashMap<>();
        for (var action : entries) {
            Path src = action.getKey();
            Path dst = action.getValue();
            if (Files.isDirectory(src)) {
                try (Stream<Path> stream = Files.walk(src)) {
                    stream.sequential()
                            .forEach(path -> actions.put(dst.resolve(src.relativize(path)).normalize(), path));
                }
            } else {
                actions.put(dst.normalize(), src);
            }
        }

        for (var action : actions.entrySet()) {
            Path dst = action.getKey();
            Path src = action.getValue();

            if (excludePaths.stream().anyMatch(src::startsWith)) {
                continue;
            }

            if (src.equals(dst) || !src.toFile().exists()) {
                continue;
            }

            if (Files.isDirectory(src)) {
                handler.createDirectory(dst);
            } else {
                handler.copyFile(src, dst);
            }
        }

        if (move) {
            // Delete source dirs.
            for (var entry : entries) {
                Path srcFile = entry.getKey();
                if (Files.isDirectory(srcFile)) {
                    FileUtils.deleteRecursive(srcFile);
                }
            }
        }
    }

    private static Map.Entry<Path, Path> normalizedPath(Path v) {
        final Path normalized;
        if (!v.isAbsolute()) {
            normalized = Path.of("./").resolve(v.normalize());
        } else {
            normalized = v.normalize();
        }

        return Map.entry(normalized, v);
    }

    private List<Map.Entry<Path, Path>> normalizedPaths() {
        return entries.values().stream().map(PathGroup::normalizedPath).collect(Collectors.toList());
    }

    private final Map<Object, Path> entries;
}
