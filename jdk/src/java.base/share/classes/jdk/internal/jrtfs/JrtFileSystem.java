/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.LinkOption;
import java.nio.file.FileSystemException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import static java.util.stream.Collectors.toList;
import jdk.internal.jimage.ImageReader;
import jdk.internal.jimage.ImageReader.Node;


/**
 * jrt file system implementation built on System jimage files.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
class JrtFileSystem extends AbstractJrtFileSystem {

    // System image reader
    private ImageReader bootImage;
    // root path
    private final JrtPath rootPath;
    private volatile boolean isOpen;

    // open a .jimage and build directory structure
    private static ImageReader openImage(Path path) throws IOException {
        ImageReader image = ImageReader.open(path);
        image.getRootDirectory();
        return image;
    }

    JrtFileSystem(JrtFileSystemProvider provider,
            Map<String, ?> env)
            throws IOException {
        super(provider, env);
        checkExists(SystemImages.moduleImageFile());

        // open image file
        this.bootImage = openImage(SystemImages.moduleImageFile());

        byte[] root = new byte[]{'/'};
        rootPath = new JrtPath(this, root);
        isOpen = true;
    }

    // FileSystem method implementations
    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public void close() throws IOException {
        cleanup();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            cleanup();
        } catch (IOException ignored) {
        }
        super.finalize();
    }

    // AbstractJrtFileSystem method implementations
    @Override
    JrtPath getRootPath() {
        return rootPath;
    }

    @Override
    boolean isSameFile(AbstractJrtPath p1, AbstractJrtPath p2) throws IOException {
        ensureOpen();
        Node node1 = findNode(p1);
        Node node2 = findNode(p2);
        return node1.equals(node2);
    }

    @Override
    boolean isLink(AbstractJrtPath jrtPath) throws IOException {
        return checkNode(jrtPath).isLink();
    }

    @Override
    AbstractJrtPath resolveLink(AbstractJrtPath jrtPath) throws IOException {
        Node node = checkNode(jrtPath);
        if (node.isLink()) {
            node = node.resolveLink();
            return toJrtPath(getBytes(node.getName()));
        }

        return jrtPath;
    }

    @Override
    JrtFileAttributes getFileAttributes(AbstractJrtPath jrtPath, LinkOption... options)
            throws IOException {
        Node node = checkNode(jrtPath);
        if (node.isLink() && followLinks(options)) {
            return new JrtFileAttributes(node.resolveLink(true));
        }
        return new JrtFileAttributes(node);
    }

    @Override
    boolean exists(AbstractJrtPath jrtPath) throws IOException {
        try {
            checkNode(jrtPath);
        } catch (NoSuchFileException exp) {
            return false;
        }
        return true;
    }

    @Override
    boolean isDirectory(AbstractJrtPath jrtPath, boolean resolveLinks)
            throws IOException {
        Node node = checkNode(jrtPath);
        return resolveLinks && node.isLink()
                ? node.resolveLink(true).isDirectory()
                : node.isDirectory();
    }

    @Override
    Iterator<Path> iteratorOf(AbstractJrtPath jrtPath) throws IOException {
        Node node = checkNode(jrtPath).resolveLink(true);
        if (!node.isDirectory()) {
            throw new NotDirectoryException(getString(jrtPath.getName()));
        }

        if (node.isRootDir()) {
            return rootDirIterator(jrtPath);
        } else if (node.isModulesDir()) {
            return modulesDirIterator(jrtPath);
        } else if (node.isPackagesDir()) {
            return packagesDirIterator(jrtPath);
        }

        return nodesToIterator(jrtPath, node.getChildren());
    }

    @Override
    byte[] getFileContent(AbstractJrtPath jrtPath) throws IOException {
        final Node node = checkResource(jrtPath);
        return bootImage.getResource(node);
    }

    // Implementation details below this point
    // clean up this file system - called from finalize and close
    private void cleanup() throws IOException {
        if (!isOpen) {
            return;
        }

        synchronized (this) {
            isOpen = false;

            // close all image reader and null out
            bootImage.close();
            bootImage = null;
        }
    }

    private Node lookup(byte[] path) {
        Node node = null;
        try {
            node = bootImage.findNode(getString(path));
        } catch (RuntimeException re) {
            throw new InvalidPathException(getString(path), re.toString());
        }
        return node;
    }

    private Node lookupSymbolic(byte[] path) {
        for (int i = 1; i < path.length; i++) {
            if (path[i] == (byte) '/') {
                byte[] prefix = Arrays.copyOfRange(path, 0, i);
                Node node = lookup(prefix);
                if (node == null) {
                    break;
                }

                if (node.isLink()) {
                    Node link = node.resolveLink(true);
                    // resolved symbolic path concatenated to the rest of the path
                    String resPath = link.getName() + getString(path).substring(i);
                    byte[] resPathBytes = getBytes(resPath);
                    node = lookup(resPathBytes);
                    return node != null ? node : lookupSymbolic(resPathBytes);
                }
            }
        }

        return null;
    }

    private Node findNode(AbstractJrtPath jrtPath) throws IOException {
        return findNode(jrtPath.getResolvedPath());
    }

    private Node findNode(byte[] path) throws IOException {
        Node node = lookup(path);
        if (node == null) {
            node = lookupSymbolic(path);
            if (node == null) {
                throw new NoSuchFileException(getString(path));
            }
        }
        return node;
    }

    private Node checkNode(AbstractJrtPath jrtPath) throws IOException {
        return checkNode(jrtPath.getResolvedPath());
    }

    private Node checkNode(byte[] path) throws IOException {
        ensureOpen();
        return findNode(path);
    }

    private Node checkResource(AbstractJrtPath jrtPath) throws IOException {
        return checkResource(jrtPath.getResolvedPath());
    }

    private Node checkResource(byte[] path) throws IOException {
        Node node = checkNode(path);
        if (node.isDirectory()) {
            throw new FileSystemException(getString(path) + " is a directory");
        }

        assert node.isResource() : "resource node expected here";
        return node;
    }

    private JrtPath toJrtPath(String path) {
        return toJrtPath(getBytes(path));
    }

    private JrtPath toJrtPath(byte[] path) {
        return new JrtPath(this, path);
    }

    private Iterator<Path> nodesToIterator(AbstractJrtPath dir, List<Node> childNodes) {
        Function<Node, Path> nodeToPath =
            child -> dir.resolve(
                toJrtPath(child.getNameString()).getFileName());
        return childNodes.stream().
                map(nodeToPath).collect(toList()).
                iterator();
    }

    private List<Node> rootChildren;

    private synchronized void initRootChildren(AbstractJrtPath jrtPath) throws IOException {
        if (rootChildren == null) {
            rootChildren = new ArrayList<>();
            rootChildren.addAll(findNode(jrtPath).getChildren());
        }
    }

    private Iterator<Path> rootDirIterator(AbstractJrtPath jrtPath) throws IOException {
        initRootChildren(jrtPath);
        return nodesToIterator(jrtPath, rootChildren);
    }

    private List<Node> modulesChildren;

    private synchronized void initModulesChildren(AbstractJrtPath jrtPath) throws IOException {
        if (modulesChildren == null) {
            modulesChildren = new ArrayList<>();
            modulesChildren.addAll(findNode(jrtPath).getChildren());
        }
    }

    private Iterator<Path> modulesDirIterator(AbstractJrtPath jrtPath) throws IOException {
        initModulesChildren(jrtPath);
        return nodesToIterator(jrtPath, modulesChildren);
    }

    private List<Node> packagesChildren;

    private synchronized void initPackagesChildren(AbstractJrtPath jrtPath) throws IOException {
        if (packagesChildren == null) {
            packagesChildren = new ArrayList<>();
            packagesChildren.addAll(findNode(jrtPath).getChildren());
        }
    }

    private Iterator<Path> packagesDirIterator(AbstractJrtPath jrtPath) throws IOException {
        initPackagesChildren(jrtPath);
        return nodesToIterator(jrtPath, packagesChildren);
    }
}
