/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

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

    private final Path modulesDir;
    private final String separator;
    private final Map<String, PathNode> nodes = new HashMap<>();
    private final BasicFileAttributes modulesDirAttrs;

    ExplodedImage(Path modulesDir) throws IOException {
        this.modulesDir = modulesDir;
        String str = modulesDir.getFileSystem().getSeparator();
        separator = str.equals("/") ? null : str;
        modulesDirAttrs = Files.readAttributes(modulesDir, BasicFileAttributes.class);
        initNodes();
    }

    // A Node that is backed by actual default file system Path
    private final class PathNode extends Node {

        // Path in underlying default file system
        private Path path;
        private PathNode link;
        private List<Node> children;

        private PathNode(String name, Path path, BasicFileAttributes attrs) {  // path
            super(name, attrs);
            this.path = path;
        }

        private PathNode(String name, Node link) {              // link
            super(name, link.getFileAttributes());
            this.link = (PathNode)link;
        }

        private PathNode(String name, List<Node> children) {    // dir
            super(name, modulesDirAttrs);
            this.children = children;
        }

        @Override
        public boolean isResource() {
            return link == null && !getFileAttributes().isDirectory();
        }

        @Override
        public boolean isDirectory() {
            return children != null ||
                   (link == null && getFileAttributes().isDirectory());
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
            if (!getFileAttributes().isRegularFile())
                throw new FileSystemException(getName() + " is not file");
            return Files.readAllBytes(path);
        }

        @Override
        public Stream<String> getChildNames() {
            if (!isDirectory())
                throw new IllegalArgumentException("not a directory: " + getName());
            if (children == null) {
                List<Node> list = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    for (Path p : stream) {
                        p = modulesDir.relativize(p);
                        String pName = MODULES + nativeSlashToFrontSlash(p.toString());
                        Node node = findNode(pName);
                        if (node != null) {  // findNode may choose to hide certain files!
                            list.add(node);
                        }
                    }
                } catch (IOException x) {
                    return null;
                }
                children = list;
            }
            return children.stream().map(Node::getName);
        }

        @Override
        public long size() {
            try {
                return isDirectory() ? 0 : Files.size(path);
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
        // If null, this was not the name of "/modules/..." node, and since all
        // "/packages/..." nodes were created and cached in advance, the name
        // cannot reference a valid node.
        Path path = underlyingModulesPath(name);
        if (path == null) {
            return null;
        }
        // This can still return null for hidden files.
        return createModulesNode(name, path);
    }

    /**
     * Lazily creates and caches a {@code Node} for the given "/modules/..." name
     * and corresponding path to a file or directory.
     *
     * @param name a resource or directory node name, of the form "/modules/...".
     * @param path the path of a file for a resource or directory.
     * @return the newly created and cached node, or {@code null} if the given
     *     path references a file which must be hidden in the node hierarchy.
     */
    private Node createModulesNode(String name, Path path) {
        assert !nodes.containsKey(name) : "Node must not already exist: " + name;
        assert isNonEmptyModulesPath(name) : "Invalid modules name: " + name;

        try {
            // We only know if we're creating a resource of directory when we
            // look up file attributes, and we only do that once. Thus, we can
            // only reject "marker files" here, rather than by inspecting the
            // given name string, since it doesn't apply to directories.
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            if (attrs.isRegularFile()) {
                Path f = path.getFileName();
                if (f.toString().startsWith("_the.")) {
                    return null;
                }
            } else if (!attrs.isDirectory()) {
                return null;
            }
            PathNode node = new PathNode(name, path, attrs);
            nodes.put(name, node);
            return node;
        } catch (IOException x) {
            // Since the path reference a file, any errors should not be ignored.
            throw new UncheckedIOException(x);
        }
    }

    /**
     * Returns the expected file path for name in the "/modules/..." namespace,
     * or {@code null} if the name is not in the "/modules/..." namespace or the
     * path does not reference a file.
     */
    private Path underlyingModulesPath(String name) {
        if (isNonEmptyModulesPath(name)) {
            Path path = modulesDir.resolve(frontSlashToNativeSlash(name.substring(MODULES.length())));
            return Files.exists(path) ? path : null;
        }
        return null;
    }

    private static boolean isNonEmptyModulesPath(String name) {
        // Don't just check the prefix, there must be something after it too
        // (otherwise you end up with an empty string after trimming).
        return name.startsWith(MODULES) && name.length() > MODULES.length();
    }

    // convert "/" to platform path separator
    private String frontSlashToNativeSlash(String str) {
        return separator == null ? str : str.replace("/", separator);
    }

    // convert platform path separator to "/"
    private String nativeSlashToFrontSlash(String str) {
        return separator == null ? str : str.replace(separator, "/");
    }

    // convert "/"s to "."s
    private String slashesToDots(String str) {
        return str.replace(separator != null ? separator : "/", ".");
    }

    // initialize file system Nodes
    private void initNodes() throws IOException {
        // same package prefix may exist in multiple modules. This Map
        // is filled by walking "jdk modules" directory recursively!
        Map<String, List<String>> packageToModules = new HashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modulesDir)) {
            for (Path module : stream) {
                if (Files.isDirectory(module)) {
                    String moduleName = module.getFileName().toString();
                    // make sure "/modules/<moduleName>" is created
                    Objects.requireNonNull(createModulesNode(MODULES + moduleName, module));
                    try (Stream<Path> contentsStream = Files.walk(module)) {
                        contentsStream.filter(Files::isDirectory).forEach((p) -> {
                            p = module.relativize(p);
                            String pkgName = slashesToDots(p.toString());
                            // skip META-INF and empty strings
                            if (!pkgName.isEmpty() && !pkgName.startsWith("META-INF")) {
                                packageToModules
                                        .computeIfAbsent(pkgName, k -> new ArrayList<>())
                                        .add(moduleName);
                            }
                        });
                    }
                }
            }
        }
        // create "/modules" directory
        // "nodes" map contains only /modules/<foo> nodes only so far and so add all as children of /modules
        PathNode modulesRootNode = new PathNode("/modules", new ArrayList<>(nodes.values()));
        nodes.put(modulesRootNode.getName(), modulesRootNode);

        // create children under "/packages"
        List<Node> packagesChildren = new ArrayList<>(packageToModules.size());
        for (Map.Entry<String, List<String>> entry : packageToModules.entrySet()) {
            String pkgName = entry.getKey();
            List<String> moduleNameList = entry.getValue();
            List<Node> moduleLinkNodes = new ArrayList<>(moduleNameList.size());
            for (String moduleName : moduleNameList) {
                Node moduleNode = Objects.requireNonNull(nodes.get(MODULES + moduleName));
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
        List<Node> rootChildren = new ArrayList<>();
        rootChildren.add(packagesRootNode);
        rootChildren.add(modulesRootNode);
        PathNode root = new PathNode("/", rootChildren);
        nodes.put(root.getName(), root);
    }
}
