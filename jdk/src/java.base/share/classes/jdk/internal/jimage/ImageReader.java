/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.jimage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public class ImageReader extends BasicImageReader {

    private static final int SIZE_OF_OFFSET = 4;

    // Map of files opened as LITTLE_ENDIAN
    private static final HashMap<Path, ImageReader> OPEN_LE_FILES
            = new HashMap<>();

    // Map of files opened as BIG_ENDIAN
    private static final HashMap<Path, ImageReader> OPEN_BE_FILES
            = new HashMap<>();

    private int openCount;

    // attributes of the .jimage file. jimage file does not contain
    // attributes for the individual resources (yet). We use attributes
    // of the jimage file itself (creation, modification, access times).
    // Iniitalized lazily, see {@link #imageFileAttributes()}.
    private BasicFileAttributes imageFileAttributes;

    // directory management implementation
    private final HashMap<String, Node> nodes;
    private volatile Directory rootDir;

    private Directory packagesDir;
    private Directory modulesDir;

    private ImageReader(Path imagePath, ByteOrder byteOrder) throws IOException {
        super(imagePath, byteOrder);
        this.nodes = new HashMap<>();
    }

    public static ImageReader open(Path imagePath, ByteOrder byteOrder) throws IOException {
        HashMap<Path, ImageReader> openFiles = getOpenFilesMap(byteOrder);
        ImageReader reader;
        synchronized (openFiles) {
            reader = openFiles.get(imagePath);
            if (reader == null) {
                reader = new ImageReader(imagePath, byteOrder);
                ImageReader existingReader = openFiles.putIfAbsent(imagePath, reader);
                assert (existingReader == null);
            }
            reader.openCount++;
        }
        return reader;
    }

    private static HashMap<Path, ImageReader> getOpenFilesMap(ByteOrder byteOrder) {
        return (byteOrder == ByteOrder.BIG_ENDIAN) ? OPEN_BE_FILES : OPEN_LE_FILES;
    }

    /**
     * Opens the given file path as an image file, returning an {@code ImageReader}.
     */
    public static ImageReader open(Path imagePath) throws IOException {
        return open(imagePath, ByteOrder.nativeOrder());
    }

    private boolean canClose() {
        HashMap<Path, ImageReader> openFiles = getOpenFilesMap(this.getByteOrder());
        synchronized (openFiles) {
            if (--this.openCount == 0) {
                return openFiles.remove(this.getName(), this);
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        if (canClose()) {
            super.close();
            clearNodes();
       }
    }

    // jimage file does not store directory structure. We build nodes
    // using the "path" strings found in the jimage file.
    // Node can be a directory or a resource
    public abstract static class Node {
        private static final int ROOT_DIR = 0b0000_0000_0000_0001;
        private static final int PACKAGES_DIR = 0b0000_0000_0000_0010;
        private static final int MODULES_DIR = 0b0000_0000_0000_0100;

        private int flags;
        private final String name;
        private final BasicFileAttributes fileAttrs;
        private boolean completed;

        Node(String name, BasicFileAttributes fileAttrs) {
            this.name = Objects.requireNonNull(name);
            this.fileAttrs = Objects.requireNonNull(fileAttrs);
        }

        /**
         * A node is completed when all its direct children have been built.
         *
         * @return
         */
        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public final void setIsRootDir() {
            flags |= ROOT_DIR;
        }

        public final boolean isRootDir() {
            return (flags & ROOT_DIR) != 0;
        }

        public final void setIsPackagesDir() {
            flags |= PACKAGES_DIR;
        }

        public final boolean isPackagesDir() {
            return (flags & PACKAGES_DIR) != 0;
        }

        public final void setIsModulesDir() {
            flags |= MODULES_DIR;
        }

        public final boolean isModulesDir() {
            return (flags & MODULES_DIR) != 0;
        }

        public final String getName() {
            return name;
        }

        public final BasicFileAttributes getFileAttributes() {
            return fileAttrs;
        }

        // resolve this Node (if this is a soft link, get underlying Node)
        public final Node resolveLink() {
            return resolveLink(false);
        }

        public Node resolveLink(boolean recursive) {
            return this;
        }

        // is this a soft link Node?
        public boolean isLink() {
            return false;
        }

        public boolean isDirectory() {
            return false;
        }

        public List<Node> getChildren() {
            throw new IllegalArgumentException("not a directory: " + getNameString());
        }

        public boolean isResource() {
            return false;
        }

        public ImageLocation getLocation() {
            throw new IllegalArgumentException("not a resource: " + getNameString());
        }

        public long size() {
            return 0L;
        }

        public long compressedSize() {
            return 0L;
        }

        public String extension() {
            return null;
        }

        public long contentOffset() {
            return 0L;
        }

        public final FileTime creationTime() {
            return fileAttrs.creationTime();
        }

        public final FileTime lastAccessTime() {
            return fileAttrs.lastAccessTime();
        }

        public final FileTime lastModifiedTime() {
            return fileAttrs.lastModifiedTime();
        }

        public final String getNameString() {
            return name;
        }

        @Override
        public final String toString() {
            return getNameString();
        }

        @Override
        public final int hashCode() {
            return name.hashCode();
        }

        @Override
        public final boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (other instanceof Node) {
                return name.equals(((Node) other).name);
            }

            return false;
        }
    }

    // directory node - directory has full path name without '/' at end.
    static final class Directory extends Node {
        private final List<Node> children;

        private Directory(String name, BasicFileAttributes fileAttrs) {
            super(name, fileAttrs);
            children = new ArrayList<>();
        }

        static Directory create(Directory parent, String name, BasicFileAttributes fileAttrs) {
            Directory d = new Directory(name, fileAttrs);
            if (parent != null) {
                parent.addChild(d);
            }
            return d;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public List<Node> getChildren() {
            return Collections.unmodifiableList(children);
        }

        void addChild(Node node) {
            children.add(node);
        }

        public void walk(Consumer<? super Node> consumer) {
            consumer.accept(this);
            for ( Node child : children ) {
                if (child.isDirectory()) {
                    ((Directory)child).walk(consumer);
                } else {
                    consumer.accept(child);
                }
            }
        }
    }

    // "resource" is .class or any other resource (compressed/uncompressed) in a jimage.
    // full path of the resource is the "name" of the resource.
    static class Resource extends Node {
        private final ImageLocation loc;

        private Resource(ImageLocation loc, BasicFileAttributes fileAttrs) {
            super(loc.getFullName(true), fileAttrs);
            this.loc = loc;
        }

        static Resource create(Directory parent, ImageLocation loc, BasicFileAttributes fileAttrs) {
            Resource rs = new Resource(loc, fileAttrs);
            parent.addChild(rs);
            return rs;
        }

        @Override
        public boolean isCompleted() {
            return true;
        }

        @Override
        public boolean isResource() {
            return true;
        }

        @Override
        public ImageLocation getLocation() {
            return loc;
        }

        @Override
        public long size() {
            return loc.getUncompressedSize();
        }

        @Override
        public long compressedSize() {
            return loc.getCompressedSize();
        }

        @Override
        public String extension() {
            return loc.getExtension();
        }

        @Override
        public long contentOffset() {
            return loc.getContentOffset();
        }
    }

    // represents a soft link to another Node
    static class LinkNode extends Node {
        private final Node link;

        private LinkNode(String name, Node link) {
            super(name, link.getFileAttributes());
            this.link = link;
        }

        static LinkNode create(Directory parent, String name, Node link) {
            LinkNode ln = new LinkNode(name, link);
            parent.addChild(ln);
            return ln;
        }

        @Override
        public boolean isCompleted() {
            return true;
        }

        @Override
        public Node resolveLink(boolean recursive) {
            return recursive && (link instanceof LinkNode)? ((LinkNode)link).resolveLink(true) : link;
        }

        @Override
        public boolean isLink() {
            return true;
        }
    }

    // directory management interface
    public Directory getRootDirectory() {
        return buildRootDirectory();
    }

    /**
     * To visit sub tree resources.
     */
    interface LocationVisitor {

        void visit(ImageLocation loc);
    }

    /**
     * Lazily build a node from a name.
    */
    private Node buildNode(String name) {
        Node n;
        boolean isPackages = name.startsWith("/packages");
        boolean isModules = !isPackages && name.startsWith("/modules");

        if (!(isModules || isPackages)) {
            return null;
        }

        ImageLocation loc = findLocation(name);

        if (loc != null) { // A sub tree node
            if (isPackages) {
                n = handlePackages(name, loc);
            } else { // modules sub tree
                n = handleModulesSubTree(name, loc);
            }
        } else { // Asking for a resource? /modules/java.base/java/lang/Object.class
            if (isModules) {
                n = handleResource(name);
            } else {
                // Possibly ask for /packages/java.lang/java.base
                // although /packages/java.base not created
                n = handleModuleLink(name);
            }
        }
        return n;
    }

    private void visitLocation(ImageLocation loc, LocationVisitor visitor) {
        byte[] offsets = getResource(loc);
        ByteBuffer buffer = ByteBuffer.wrap(offsets);
        buffer.order(getByteOrder());
        IntBuffer intBuffer = buffer.asIntBuffer();
        for (int i = 0; i < offsets.length / SIZE_OF_OFFSET; i++) {
            int offset = intBuffer.get(i);
            ImageLocation pkgLoc = getLocation(offset);
            visitor.visit(pkgLoc);
        }
    }

    private void visitPackageLocation(ImageLocation loc) {
        // Retrieve package name
        String pkgName = getBaseExt(loc);
        // Content is array of offsets in Strings table
        byte[] stringsOffsets = getResource(loc);
        ByteBuffer buffer = ByteBuffer.wrap(stringsOffsets);
        buffer.order(getByteOrder());
        IntBuffer intBuffer = buffer.asIntBuffer();
        // For each module, create a link node.
        for (int i = 0; i < stringsOffsets.length / SIZE_OF_OFFSET; i++) {
            // skip empty state, useless.
            intBuffer.get(i);
            i++;
            int offset = intBuffer.get(i);
            String moduleName = getString(offset);
            Node targetNode = findNode("/modules/" + moduleName);
            if (targetNode != null) {
                String pkgDirName = packagesDir.getName() + "/" + pkgName;
                Directory pkgDir = (Directory) nodes.get(pkgDirName);
                newLinkNode(pkgDir, pkgDir.getName() + "/" + moduleName, targetNode);
            }
        }
    }

    private Node handlePackages(String name, ImageLocation loc) {
        long size = loc.getUncompressedSize();
        Node n = null;
        // Only possiblities are /packages, /packages/package/module
        if (name.equals("/packages")) {
            visitLocation(loc, (childloc) -> {
                findNode(childloc.getFullName());
            });
            packagesDir.setCompleted(true);
            n = packagesDir;
        } else {
            if (size != 0) { // children are offsets to module in StringsTable
                String pkgName = getBaseExt(loc);
                Directory pkgDir = newDirectory(packagesDir, packagesDir.getName() + "/" + pkgName);
                visitPackageLocation(loc);
                pkgDir.setCompleted(true);
                n = pkgDir;
            } else { // Link to module
                String pkgName = loc.getParent();
                String modName = getBaseExt(loc);
                Node targetNode = findNode("/modules/" + modName);
                if (targetNode != null) {
                    String pkgDirName = packagesDir.getName() + "/" + pkgName;
                    Directory pkgDir = (Directory) nodes.get(pkgDirName);
                    Node linkNode = newLinkNode(pkgDir, pkgDir.getName() + "/" + modName, targetNode);
                    n = linkNode;
                }
            }
        }
        return n;
    }

    // Asking for /packages/package/module although
    // /packages/<pkg>/ not yet created, need to create it
    // prior to return the link to module node.
    private Node handleModuleLink(String name) {
        // eg: unresolved /packages/package/module
        // Build /packages/package node
        Node ret = null;
        String radical = "/packages/";
        String path = name;
        if (path.startsWith(radical)) {
            int start = radical.length();
            int pkgEnd = path.indexOf('/', start);
            if (pkgEnd != -1) {
                String pkg = path.substring(start, pkgEnd);
                String pkgPath = radical + pkg;
                Node n = findNode(pkgPath);
                // If not found means that this is a symbolic link such as:
                // /packages/java.util/java.base/java/util/Vector.class
                // and will be done by a retry of the filesystem
                for (Node child : n.getChildren()) {
                    if (child.name.equals(name)) {
                        ret = child;
                        break;
                    }
                }
            }
        }
        return ret;
    }

    private Node handleModulesSubTree(String name, ImageLocation loc) {
        Node n;
        assert (name.equals(loc.getFullName()));
        Directory dir = makeDirectories(name);
        visitLocation(loc, (childloc) -> {
            String path = childloc.getFullName();
            if (path.startsWith("/modules")) { // a package
                makeDirectories(path);
            } else { // a resource
                makeDirectories(childloc.buildName(true, true, false));
                newResource(dir, childloc);
            }
        });
        dir.setCompleted(true);
        n = dir;
        return n;
    }

    private Node handleResource(String name) {
        Node n = null;
        String locationPath = name.substring("/modules".length());
        ImageLocation resourceLoc = findLocation(locationPath);
        if (resourceLoc != null) {
            Directory dir = makeDirectories(resourceLoc.buildName(true, true, false));
            Resource res = newResource(dir, resourceLoc);
            n = res;
        }
        return n;
    }

    private String getBaseExt(ImageLocation loc) {
        String base = loc.getBase();
        String ext = loc.getExtension();
        if (ext != null && !ext.isEmpty()) {
            base = base + "." + ext;
        }
        return base;
    }

    public synchronized Node findNode(String name) {
        buildRootDirectory();
        Node n = nodes.get(name);
        if (n == null || !n.isCompleted()) {
            n = buildNode(name);
        }
        return n;
    }

    private synchronized void clearNodes() {
        nodes.clear();
        rootDir = null;
    }

    /**
     * Returns the file attributes of the image file.
     */
    private BasicFileAttributes imageFileAttributes() {
        BasicFileAttributes attrs = imageFileAttributes;
        if (attrs == null) {
            try {
                Path file = getImagePath();
                attrs = Files.readAttributes(file, BasicFileAttributes.class);
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
            imageFileAttributes = attrs;
        }
        return attrs;
    }

    private Directory buildRootDirectory() {
        Directory root = rootDir; // volatile read
        if (root != null) {
            return root;
        }

        synchronized (this) {
            root = rootDir;
            if (root != null) {
                return root;
            }

            // FIXME no time information per resource in jimage file (yet?)
            // we use file attributes of jimage itself.
            // root directory
            root = newDirectory(null, "/");
            root.setIsRootDir();

            // /packages dir
            packagesDir = newDirectory(root, "/packages");
            packagesDir.setIsPackagesDir();

            // /modules dir
            modulesDir = newDirectory(root, "/modules");
            modulesDir.setIsModulesDir();

            root.setCompleted(true);
            return rootDir = root;
        }
    }

    private Directory newDirectory(Directory parent, String name) {
        Directory dir = Directory.create(parent, name, imageFileAttributes());
        nodes.put(dir.getName(), dir);
        return dir;
    }

    private Resource newResource(Directory parent, ImageLocation loc) {
        Resource res = Resource.create(parent, loc, imageFileAttributes());
        nodes.put(res.getName(), res);
        return res;
    }

    private LinkNode newLinkNode(Directory dir, String name, Node link) {
        LinkNode linkNode = LinkNode.create(dir, name, link);
        nodes.put(linkNode.getName(), linkNode);
        return linkNode;
    }

    private Directory makeDirectories(String parent) {
        Directory last = rootDir;
        for (int offset = parent.indexOf('/', 1);
                offset != -1;
                offset = parent.indexOf('/', offset + 1)) {
            String dir = parent.substring(0, offset);
            last = makeDirectory(dir, last);
        }
        return makeDirectory(parent, last);

    }

    private Directory makeDirectory(String dir, Directory last) {
        Directory nextDir = (Directory) nodes.get(dir);
        if (nextDir == null) {
            nextDir = newDirectory(last, dir);
        }
        return nextDir;
    }

    public byte[] getResource(Node node) throws IOException {
        if (node.isResource()) {
            return super.getResource(node.getLocation());
        }
        throw new IOException("Not a resource: " + node);
    }

    public byte[] getResource(Resource rs) throws IOException {
        return super.getResource(rs.getLocation());
    }
}
