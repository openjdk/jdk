/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import static java.util.stream.Collectors.toList;
import static jdk.internal.jrtfs.AbstractJrtFileSystem.getString;

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
class JrtExplodedFileSystem extends AbstractJrtFileSystem {

    private static final String MODULES = "/modules/";
    private static final String PACKAGES = "/packages/";
    private static final int PACKAGES_LEN = PACKAGES.length();

    // root path
    private final JrtExplodedPath rootPath;
    private volatile boolean isOpen;
    private final FileSystem defaultFS;
    private final String separator;
    private final Map<String, Node> nodes = Collections.synchronizedMap(new HashMap<>());
    private final BasicFileAttributes modulesDirAttrs;

    JrtExplodedFileSystem(JrtFileSystemProvider provider,
            Map<String, ?> env)
            throws IOException {

        super(provider, env);
        checkExists(SystemImages.explodedModulesDir());
        byte[] root = new byte[]{'/'};
        rootPath = new JrtExplodedPath(this, root);
        isOpen = true;
        defaultFS = FileSystems.getDefault();
        String str = defaultFS.getSeparator();
        separator = str.equals(getSeparator()) ? null : str;
        modulesDirAttrs = Files.readAttributes(SystemImages.explodedModulesDir(), BasicFileAttributes.class);
        initNodes();
    }

    @Override
    public void close() throws IOException {
        cleanup();
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    protected void finalize() throws Throwable {
        cleanup();
        super.finalize();
    }

    private synchronized void cleanup() {
        isOpen = false;
        nodes.clear();
    }

    @Override
    JrtExplodedPath getRootPath() {
        return rootPath;
    }

    // Base class for Nodes of this file system
    abstract class Node {

        private final String name;

        Node(String name) {
            this.name = name;
        }

        final String getName() {
            return name;
        }

        final String getExtension() {
            if (isFile()) {
                final int index = name.lastIndexOf(".");
                if (index != -1) {
                    return name.substring(index + 1);
                }
            }

            return null;
        }

        BasicFileAttributes getBasicAttrs() throws IOException {
            return modulesDirAttrs;
        }

        boolean isLink() {
            return false;
        }

        boolean isDirectory() {
            return false;
        }

        boolean isFile() {
            return false;
        }

        byte[] getContent() throws IOException {
            if (!isFile()) {
                throw new FileSystemException(name + " is not file");
            }

            throw new AssertionError("ShouldNotReachHere");
        }

        List<Node> getChildren() throws IOException {
            if (!isDirectory()) {
                throw new NotDirectoryException(name);
            }

            throw new AssertionError("ShouldNotReachHere");
        }

        final Node resolveLink() {
            return resolveLink(false);
        }

        Node resolveLink(boolean recursive) {
            return this;
        }
    }

    // A Node that is backed by actual default file system Path
    private final class PathNode extends Node {

        // Path in underlying default file system
        private final Path path;
        private final boolean file;
        // lazily initialized, don't read attributes unless required!
        private BasicFileAttributes attrs;

        PathNode(String name, Path path) {
            super(name);
            this.path = path;
            this.file = Files.isRegularFile(path);
        }

        @Override
        synchronized BasicFileAttributes getBasicAttrs() throws IOException {
            if (attrs == null) {
                attrs = Files.readAttributes(path, BasicFileAttributes.class);
            }
            return attrs;
        }

        @Override
        boolean isDirectory() {
            return !file;
        }

        @Override
        boolean isFile() {
            return file;
        }

        @Override
        byte[] getContent() throws IOException {
            if (!isFile()) {
                throw new FileSystemException(getName() + " is not file");
            }

            return Files.readAllBytes(path);
        }

        @Override
        List<Node> getChildren() throws IOException {
            if (!isDirectory()) {
                throw new NotDirectoryException(getName());
            }

            List<Node> children = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path cp : stream) {
                    cp = SystemImages.explodedModulesDir().relativize(cp);
                    String cpName = MODULES + nativeSlashToFrontSlash(cp.toString());
                    try {
                        children.add(findNode(cpName));
                    } catch (NoSuchFileException nsfe) {
                        // findNode may choose to hide certain files!
                    }
                }
            }

            return children;
        }
    }

    // A Node that links to another Node
    private final class LinkNode extends Node {

        // underlying linked Node
        private final Node link;

        LinkNode(String name, Node link) {
            super(name);
            this.link = link;
        }

        @Override
        BasicFileAttributes getBasicAttrs() throws IOException {
            return link.getBasicAttrs();
        }

        @Override
        public boolean isLink() {
            return true;
        }

        @Override
        Node resolveLink(boolean recursive) {
            return recursive && (link instanceof LinkNode) ? ((LinkNode) link).resolveLink(true) : link;
        }
    }

    // A directory Node with it's children Nodes
    private final class DirNode extends Node {

        // children Nodes of this Node.
        private final List<Node> children;

        DirNode(String name, List<Node> children) {
            super(name);
            this.children = children;
        }

        @Override
        boolean isDirectory() {
            return true;
        }

        @Override
        List<Node> getChildren() throws IOException {
            return children;
        }
    }

    private JrtExplodedPath toJrtExplodedPath(String path) {
        return toJrtExplodedPath(getBytes(path));
    }

    private JrtExplodedPath toJrtExplodedPath(byte[] path) {
        return new JrtExplodedPath(this, path);
    }

    @Override
    boolean isSameFile(AbstractJrtPath jrtPath1, AbstractJrtPath jrtPath2) throws IOException {
        Node n1 = checkNode(jrtPath1);
        Node n2 = checkNode(jrtPath2);
        return n1 == n2;
    }

    @Override
    boolean isLink(AbstractJrtPath jrtPath) throws IOException {
        return checkNode(jrtPath).isLink();
    }

    @Override
    AbstractJrtPath resolveLink(AbstractJrtPath jrtPath) throws IOException {
        String name = checkNode(jrtPath).resolveLink().getName();
        return toJrtExplodedPath(name);
    }

    @Override
    AbstractJrtFileAttributes getFileAttributes(AbstractJrtPath jrtPath, LinkOption... options) throws IOException {
        Node node = checkNode(jrtPath);
        if (node.isLink() && followLinks(options)) {
            node = node.resolveLink(true);
        }
        return new JrtExplodedFileAttributes(node);
    }

    @Override
    boolean exists(AbstractJrtPath jrtPath) throws IOException {
        try {
            checkNode(jrtPath);
            return true;
        } catch (NoSuchFileException nsfe) {
            return false;
        }
    }

    @Override
    boolean isDirectory(AbstractJrtPath jrtPath, boolean resolveLinks) throws IOException {
        Node node = checkNode(jrtPath);
        return resolveLinks && node.isLink()
                ? node.resolveLink(true).isDirectory()
                : node.isDirectory();
    }

    @Override
    Iterator<Path> iteratorOf(AbstractJrtPath dir) throws IOException {
        Node node = checkNode(dir).resolveLink(true);
        if (!node.isDirectory()) {
            throw new NotDirectoryException(getString(dir.getName()));
        }

        Function<Node, Path> nodeToPath =
            child -> dir.resolve(
                toJrtExplodedPath(child.getName()).
                getFileName());

        return node.getChildren().stream().
                   map(nodeToPath).collect(toList()).
                   iterator();
    }

    @Override
    byte[] getFileContent(AbstractJrtPath jrtPath) throws IOException {
        return checkNode(jrtPath).getContent();
    }

    private Node checkNode(AbstractJrtPath jrtPath) throws IOException {
        return checkNode(jrtPath.getResolvedPath());
    }

    private Node checkNode(byte[] path) throws IOException {
        ensureOpen();
        return findNode(path);
    }

    synchronized Node findNode(byte[] path) throws IOException {
        return findNode(getString(path));
    }

    // find Node for the given Path
    synchronized Node findNode(String str) throws IOException {
        Node node = findModulesNode(str);
        if (node != null) {
            return node;
        }

        // lazily created for paths like /packages/<package>/<module>/xyz
        // For example /packages/java.lang/java.base/java/lang/
        if (str.startsWith(PACKAGES)) {
            // pkgEndIdx marks end of <package> part
            int pkgEndIdx = str.indexOf('/', PACKAGES_LEN);
            if (pkgEndIdx != -1) {
                // modEndIdx marks end of <module> part
                int modEndIdx = str.indexOf('/', pkgEndIdx + 1);
                if (modEndIdx != -1) {
                    // make sure we have such module link!
                    // ie., /packages/<package>/<module> is valid
                    Node linkNode = nodes.get(str.substring(0, modEndIdx));
                    if (linkNode == null || !linkNode.isLink()) {
                        throw new NoSuchFileException(str);
                    }

                    // map to "/modules/zyz" path and return that node
                    // For example, "/modules/java.base/java/lang" for
                    // "/packages/java.lang/java.base/java/lang".
                    String mod = MODULES + str.substring(pkgEndIdx + 1);
                    return findNode(mod);
                }
            }
        }

        throw new NoSuchFileException(str);
    }

    // find a Node for a path that starts like "/modules/..."
    synchronized Node findModulesNode(String str) throws IOException {
        Node node = nodes.get(str);
        if (node != null) {
            return node;
        }

        // lazily created "/modules/xyz/abc/" Node
        // This is mapped to default file system path "<JDK_MODULES_DIR>/xyz/abc"
        Path p = underlyingPath(str);
        if (p != null) {
            if (Files.isRegularFile(p)) {
                Path file = p.getFileName();
                if (file.toString().startsWith("_the.")) {
                    return null;
                }
            }
            node = new PathNode(str, p);
            nodes.put(str, node);
            return node;
        }

        return null;
    }

    Path underlyingPath(String str) {
        if (str.startsWith(MODULES)) {
            str = frontSlashToNativeSlash(str.substring("/modules".length()));
            return defaultFS.getPath(SystemImages.explodedModulesDir().toString(), str);
        }
        return null;
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
        // same package prefix may exist in mutliple modules. This Map
        // is filled by walking "jdk modules" directory recursively!
        Map<String, List<String>> packageToModules = new HashMap<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(SystemImages.explodedModulesDir())) {
            for (Path module : stream) {
                if (Files.isDirectory(module)) {
                    String moduleName = module.getFileName().toString();
                    // make sure "/modules/<moduleName>" is created
                    findModulesNode(MODULES + moduleName);

                    Files.walk(module).filter(Files::isDirectory).forEach((p) -> {
                        p = module.relativize(p);
                        String pkgName = slashesToDots(p.toString());
                        // skip META-INFO and empty strings
                        if (!pkgName.isEmpty() && !pkgName.startsWith("META-INF")) {
                            List<String> moduleNames = packageToModules.get(pkgName);
                            if (moduleNames == null) {
                                moduleNames = new ArrayList<>();
                                packageToModules.put(pkgName, moduleNames);
                            }
                            moduleNames.add(moduleName);
                        }
                    });
                }
            }
        }

        // create "/modules" directory
        // "nodes" map contains only /modules/<foo> nodes only so far and so add all as children of /modules
        DirNode modulesDir = new DirNode("/modules", new ArrayList<>(nodes.values()));
        nodes.put(modulesDir.getName(), modulesDir);

        // create children under "/packages"
        List<Node> packagesChildren = new ArrayList<>(packageToModules.size());
        for (Map.Entry<String, List<String>> entry : packageToModules.entrySet()) {
            String pkgName = entry.getKey();
            List<String> moduleNameList = entry.getValue();
            List<Node> moduleLinkNodes = new ArrayList<>(moduleNameList.size());
            for (String moduleName : moduleNameList) {
                Node moduleNode = findModulesNode(MODULES + moduleName);
                LinkNode linkNode = new LinkNode(PACKAGES + pkgName + "/" + moduleName, moduleNode);
                nodes.put(linkNode.getName(), linkNode);
                moduleLinkNodes.add(linkNode);
            }

            DirNode pkgDir = new DirNode(PACKAGES + pkgName, moduleLinkNodes);
            nodes.put(pkgDir.getName(), pkgDir);
            packagesChildren.add(pkgDir);
        }

        // "/packages" dir
        DirNode packagesDir = new DirNode("/packages", packagesChildren);
        nodes.put(packagesDir.getName(), packagesDir);

        // finally "/" dir!
        List<Node> rootChildren = new ArrayList<>();
        rootChildren.add(modulesDir);
        rootChildren.add(packagesDir);
        DirNode root = new DirNode("/", rootChildren);
        nodes.put(root.getName(), root);
    }
}
