/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.jrtfs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import jdk.internal.jimage.ImageReader.Node;

/**
 * A jrt file system built on $JAVA_HOME/modules directory ('exploded modules
 * build')
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
class ExplodedImage extends SystemImage {

    private static final String MODULES = "/modules/";
    private static final String PACKAGES = "/packages/";
    // This directory cannot be preview overridden.
    private static final Path META_INF_DIR = Paths.get("META-INF");
    // Root of the preview override of a module relative to root of that module.
    // This directory never appears in either non-preview or preview images.
    private static final Path PREVIEW_DIR = META_INF_DIR.resolve("preview");

    private final Path modulesDir;
    private final boolean isPreviewMode;
    private final Map<String, PathNode> nodes = new HashMap<>();
    private final BasicFileAttributes modulesDirAttrs;

    ExplodedImage(Path modulesDir, boolean isPreviewMode) throws IOException {
        this.modulesDir = modulesDir;
        this.isPreviewMode = isPreviewMode;
        modulesDirAttrs = Files.readAttributes(modulesDir, BasicFileAttributes.class);
        initNodes();
    }

    // A Node that is backed by absolute Paths on the default FS
    // This is thread-safe, guaranteed by synchronized findNode
    private final class PathNode extends Node {
        // Regular file
        private final Path file;
        // Symbolic link
        private final PathNode link;
        // Directories
        // `directories` is written before and read after `childNames`
        private List<Path> directories;
        private volatile List<String> childNames; // Has no duplicates

        /**
         * Creates a file based node with the given file attributes.
         * Used for all /modules/... files.
         */
        private PathNode(String name, Path file, BasicFileAttributes attrs) {
            super(name, attrs);
            this.file = Objects.requireNonNull(file);
            this.link = null;
            this.directories = null;
            this.childNames = null;
        }

        /**
         * Creates a directory based node with the given file attributes.
         * Used for all /modules/... directories.  It is created in an
         * "incomplete" state, and its child names are determined lazily.
         */
        private PathNode(String name, List<Path> directories, BasicFileAttributes attrs) {
            super(name, attrs);
            this.file = null;
            this.link = null;
            this.directories = Objects.requireNonNull(directories);
            this.childNames = null;
        }

        /**
         * Creates a symbolic link node to the specified target.
         * Used for each module-named directory that are leafs of /packages/...
         */
        private PathNode(String name, PathNode link) {
            super(name, link.getFileAttributes());
            this.file = null;
            this.link = Objects.requireNonNull(link);
            this.directories = null;
            this.childNames = null;
        }

        /**
         * Creates a completed directory node based a list of child nodes.
         * Used for the root, /modules, /packages, and /packages/... non-leaf
         * directories, all created in initNodes().
         */
        private PathNode(String name, List<PathNode> children) {
            super(name, modulesDirAttrs);
            this.file = null;
            this.link = null;
            this.directories = null;
            this.childNames = children.stream().map(Node::getName).collect(Collectors.toList());
        }

        @Override
        public boolean isResource() {
            return file != null;
        }

        @Override
        public boolean isDirectory() {
            return childNames != null || directories != null;
        }

        @Override
        public boolean isLink() {
            return link != null;
        }

        @Override
        public PathNode resolveLink(boolean recursive) {
            if (link == null)
                return this;
            return recursive && link.isLink() ? link.resolveLink(true) : link;
        }

        private byte[] getContent() throws IOException {
            if (!isResource())
                throw new FileSystemException(getName() + " is not file");
            return Files.readAllBytes(file);
        }

        @Override
        public Stream<String> getChildNames() {
            if (!isDirectory())
                throw new IllegalStateException("not a directory: " + getName());
            List<String> names = childNames;
            if (names == null) {
                names = completeDirectory();
            }
            return names.stream();
        }

        private synchronized List<String> completeDirectory() {
            if (childNames != null) {
                return childNames;
            }

            Set<String> childNameSet = new LinkedHashSet<>();
            for (Path path : directories) {
                collectChildNodeNames(path, childNameSet);
            }
            directories = null;
            return childNames = new ArrayList<>(childNameSet);
        }

        private void collectChildNodeNames(Path absPath, Set<String> childNameSet) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(absPath)) {
                for (Path p : stream) {
                    PathNode node = (PathNode) findNode(getName() + "/" + p.getFileName().toString());
                    if (node != null) {  // findNode may choose to hide certain files!
                        childNameSet.add(node.getName());
                    }
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        @Override
        public long size() {
            try {
                return !isResource() ? 0 : Files.size(file);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        nodes.clear();
    }

    @Override
    public byte[] getResource(Node node) throws IOException {
        return ((PathNode)node).getContent();
    }

    @Override
    public synchronized Node findNode(String name) {
        PathNode node = nodes.get(name);
        if (node != null) {
            return node;
        }

        return createPathInModulesNodeIfValid(name);
    }

    // `rest` nullable means name points to the root of a module
    private Path candidatePath(Path module, Path rest, boolean preview) {
        if (preview && rest != null && rest.startsWith(META_INF_DIR)) {
            // Nothing in META-INF has a preview override
            return null;
        }
        Path now = modulesDir.resolve(module);
        if (preview) {
            now = now.resolve(PREVIEW_DIR);
        }
        if (rest != null) {
            now = now.resolve(rest);
        }
        return Files.exists(now) ? now : null;
    }

    /**
     * Lazily creates and caches a {@code Node} for the given "/modules/..." name
     * and corresponding path to a file or directory.
     *
     * @param name a resource or directory node name, of the form "/modules/...".
     * @return the newly created and cached node, or {@code null} if the given
     *     path references a file which must be hidden in the node hierarchy.
     */
    private PathNode createPathInModulesNodeIfValid(String name) {
        // We anticipate the name of a "/modules/..." node for lazy creation.
        // All "/packages/..." nodes are created by initNodes() instead.
        if (!isPathInModulesName(name)) {
            return null;
        }

        assert !nodes.containsKey(name) : "Node must not already exist: " + name;

        // Extract the module name and the remaining parts of the path
        Path moduleName; // Exactly a single name element
        Path remainderPath; // May be null
        {
            String relativeName = name.substring(MODULES.length());
            Path relativePath = Paths.get("", relativeName.split("/"));

            moduleName = relativePath.getName(0);
            int nameCount = relativePath.getNameCount();
            remainderPath = nameCount > 1 ? relativePath.subpath(1, nameCount) : null;
        }

        // Filter any path to in META-INF/preview consistently
        if (remainderPath != null && remainderPath.startsWith(PREVIEW_DIR)) {
            return null;
        }

        // Find valid regular and preview paths
        Path regularPath = candidatePath(moduleName, remainderPath, false);
        Path previewPath = isPreviewMode ? candidatePath(moduleName, remainderPath, true) : null;
        if (regularPath == null && previewPath == null) {
            return null;
        }

        // Select a path for source of attributes
        Path selected;
        if (regularPath != null && Files.isDirectory(regularPath)) {
            // Non-preview directories take precedence.
            selected = regularPath;
        } else {
            // Otherwise prefer preview resources over non-preview ones.
            selected = previewPath == null ? regularPath : previewPath;
        }

        // Read the file attributes
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(selected, BasicFileAttributes.class);
        } catch (IOException x) {
            // Since the path references a file, errors should not be ignored.
            throw new UncheckedIOException(x);
        }

        // Create the right PathNode
        PathNode node;
        if (attrs.isRegularFile()) {
            Path f = selected.getFileName();
            // Only reject "marker files", doesn't apply to directories
            if (f.toString().startsWith("_the.")) {
                return null;
            }
            node = new PathNode(name, selected, attrs);
        } else if (attrs.isDirectory()) {
            List<Path> directories = Stream.of(regularPath, previewPath)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            node = new PathNode(name, directories, attrs);
        } else {
            return null;
        }
        nodes.put(name, node);
        return node;
    }

    // Ensures this is a name taking form /modules/... with no trailing slash.
    private static boolean isPathInModulesName(String name) {
        // Don't just check the prefix, there must be something after it too
        // (otherwise you end up with an empty string after trimming).
        // Also make sure we can't be tricked by "/modules//absolute/path" or
        // "/modules/../../escaped/path".
        // Don't use regex as 'name' is untrusted (avoids stack overflow risk)
        // and performance isn't an issue here.
        return name.startsWith("/modules/")
                && !name.contains("//")
                && !name.contains("/./")
                && !name.contains("/../")
                && !name.endsWith("/")
                && !name.endsWith("/.")
                && !name.endsWith("/..");
    }

    // initialize the root /modules, /packages, and the symbolic link Nodes
    private void initNodes() throws IOException {
        // same package prefix may exist in multiple modules. This Map
        // is filled by walking "jdk modules" directory recursively!
        Map<String, List<String>> packageToModules = new HashMap<>();
        List<PathNode> modules = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modulesDir, Files::isDirectory)) {
            for (Path moduleDir : stream) {
                modules.add(findPackagesAndCreateModuleNode(moduleDir, packageToModules));
            }
        }
        // create "/modules" directory
        PathNode modulesRootNode = new PathNode("/modules", modules);
        nodes.put(modulesRootNode.getName(), modulesRootNode);

        // create children under "/packages"
        List<PathNode> packagesChildren = new ArrayList<>(packageToModules.size());
        for (Map.Entry<String, List<String>> entry : packageToModules.entrySet()) {
            String pkgName = entry.getKey();
            List<String> moduleNameList = entry.getValue();
            List<PathNode> moduleLinkNodes = new ArrayList<>(moduleNameList.size());
            for (String moduleName : moduleNameList) {
                PathNode moduleNode = Objects.requireNonNull(nodes.get(MODULES + moduleName));
                PathNode linkNode = new PathNode(PACKAGES + pkgName + "/" + moduleName, moduleNode);
                nodes.put(linkNode.getName(), linkNode);
                moduleLinkNodes.add(linkNode);
            }
            PathNode pkgDir = new PathNode(PACKAGES + pkgName, moduleLinkNodes);
            nodes.put(pkgDir.getName(), pkgDir);
            packagesChildren.add(pkgDir);
        }
        // "/packages" dir
        PathNode packagesRootNode = new PathNode("/packages", packagesChildren);
        nodes.put(packagesRootNode.getName(), packagesRootNode);

        // finally "/" dir!
        List<PathNode> rootChildren = new ArrayList<>();
        rootChildren.add(packagesRootNode);
        rootChildren.add(modulesRootNode);
        PathNode root = new PathNode("/", rootChildren);
        nodes.put(root.getName(), root);
    }

    private PathNode findPackagesAndCreateModuleNode(Path moduleDir, Map<String, List<String>> packageToModules)
            throws IOException {
        String moduleName = moduleDir.getFileName().toString();
        UnaryOperator<Path> previewExtractor = isPreviewMode
                ? (p -> p.startsWith(PREVIEW_DIR) ? PREVIEW_DIR.relativize(p) : p)
                : UnaryOperator.identity();
        try (Stream<Path> contentsStream = Files.find(moduleDir, Integer.MAX_VALUE, (path, attr) -> attr.isDirectory())) {
            contentsStream
                    .map(moduleDir::relativize)
                    // When in preview mode, map paths inside preview directory
                    // to non-preview versions.
                    .map(previewExtractor)
                    // Ignore the special META-INF directory (including
                    // unextracted preview).
                    .filter(p -> !p.startsWith(META_INF_DIR))
                    // Extract unique package names.
                    .map(str -> StreamSupport.stream(str.spliterator(), false)
                            .map(Path::toString)
                            .collect(Collectors.joining(".")))
                    // Ignore the root directories, regular or preview
                    .filter(st -> !st.isEmpty())
                    .distinct()
                    .forEach(pkgName ->
                            packageToModules
                                    .computeIfAbsent(pkgName, k -> new ArrayList<>())
                                    .add(moduleName));
        }
        return Objects.requireNonNull(createPathInModulesNodeIfValid(MODULES + moduleName));
    }
}
