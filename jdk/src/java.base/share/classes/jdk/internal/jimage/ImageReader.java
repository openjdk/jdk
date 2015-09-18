/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import static jdk.internal.jimage.UTF8String.*;

public class ImageReader extends BasicImageReader {
    // well-known strings needed for image file system.
    static final UTF8String ROOT_STRING = UTF8String.SLASH_STRING;

    // attributes of the .jimage file. jimage file does not contain
    // attributes for the individual resources (yet). We use attributes
    // of the jimage file itself (creation, modification, access times).
    // Iniitalized lazily, see {@link #imageFileAttributes()}.
    private BasicFileAttributes imageFileAttributes;

    private final ImageModuleData moduleData;

    // directory management implementation
    private final Map<UTF8String, Node> nodes;
    private volatile Directory rootDir;

    private Directory packagesDir;
    private Directory modulesDir;

    ImageReader(String imagePath, ByteOrder byteOrder) throws IOException {
        super(imagePath, byteOrder);
        this.moduleData = new ImageModuleData(this);
        this.nodes = Collections.synchronizedMap(new HashMap<>());
    }

    ImageReader(String imagePath) throws IOException {
        this(imagePath, ByteOrder.nativeOrder());
    }

    public static ImageReader open(String imagePath, ByteOrder byteOrder) throws IOException {
        return new ImageReader(imagePath, byteOrder);
    }

    /**
     * Opens the given file path as an image file, returning an {@code ImageReader}.
     */
    public static ImageReader open(String imagePath) throws IOException {
        return open(imagePath, ByteOrder.nativeOrder());
    }

    @Override
    public synchronized void close() throws IOException {
        super.close();
        clearNodes();
    }

    @Override
    public ImageLocation findLocation(UTF8String name) {
        ImageLocation location = super.findLocation(name);

        // NOTE: This should be removed when module system is up in full.
        if (location == null) {
            int index = name.lastIndexOf('/');

            if (index != -1) {
                UTF8String packageName = name.substring(0, index);
                UTF8String moduleName = moduleData.packageToModule(packageName);

                if (moduleName != null) {
                    UTF8String fullName = UTF8String.SLASH_STRING.concat(moduleName,
                            UTF8String.SLASH_STRING, name);
                    location = super.findLocation(fullName);
                }
            } else {
                // No package, try all modules.
                for (String mod : moduleData.allModuleNames()) {
                    location = super.findLocation("/" + mod + "/" + name);
                    if (location != null) {
                        break;
                    }
                }
            }
        }

        return location;
    }

    /**
     * Return the module name that contains the given package name.
     */
    public String getModule(String packageName) {
        return moduleData.packageToModule(packageName);
    }

    // jimage file does not store directory structure. We build nodes
    // using the "path" strings found in the jimage file.
    // Node can be a directory or a resource
    public abstract static class Node {
        private static final int ROOT_DIR = 0b0000_0000_0000_0001;
        private static final int PACKAGES_DIR = 0b0000_0000_0000_0010;
        private static final int MODULES_DIR = 0b0000_0000_0000_0100;

        private int flags;
        private final UTF8String name;
        private final BasicFileAttributes fileAttrs;
        private boolean completed;

        Node(UTF8String name, BasicFileAttributes fileAttrs) {
            assert name != null;
            assert fileAttrs != null;
            this.name = name;
            this.fileAttrs = fileAttrs;
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

        public final UTF8String getName() {
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
            return name.toString();
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

        private Directory(Directory parent, UTF8String name, BasicFileAttributes fileAttrs) {
            super(name, fileAttrs);
            children = new ArrayList<>();
        }

        static Directory create(Directory parent, UTF8String name, BasicFileAttributes fileAttrs) {
            Directory dir = new Directory(parent, name, fileAttrs);
            if (parent != null) {
                parent.addChild(dir);
            }
            return dir;
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

        private Resource(Directory parent, ImageLocation loc, BasicFileAttributes fileAttrs) {
            this(parent, loc.getFullName(true), loc, fileAttrs);
        }

        private Resource(Directory parent, UTF8String name, ImageLocation loc, BasicFileAttributes fileAttrs) {
            super(name, fileAttrs);
            this.loc = loc;
         }

        static Resource create(Directory parent, ImageLocation loc, BasicFileAttributes fileAttrs) {
            Resource resource = new Resource(parent, loc, fileAttrs);
            parent.addChild(resource);
            return resource;
        }

        static Resource create(Directory parent, UTF8String name, ImageLocation loc, BasicFileAttributes fileAttrs) {
            Resource resource = new Resource(parent, name, loc, fileAttrs);
            parent.addChild(resource);
            return resource;
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
            return loc.getExtensionString();
        }

        @Override
        public long contentOffset() {
            return loc.getContentOffset();
        }
    }

    // represents a soft link to another Node
    static class LinkNode extends Node {
        private final Node link;

        private LinkNode(Directory parent, UTF8String name, Node link) {
            super(name, link.getFileAttributes());
            this.link = link;
        }

        static LinkNode create(Directory parent, UTF8String name, Node link) {
            LinkNode linkNode = new LinkNode(parent, name, link);
            parent.addChild(linkNode);
            return linkNode;
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

    public Node findNode(String name) {
        return findNode(new UTF8String(name));
    }

    public Node findNode(byte[] name) {
        return findNode(new UTF8String(name));
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
    private final class NodeBuilder {

        private static final int SIZE_OF_OFFSET = 4;

        private final UTF8String name;

        private NodeBuilder(UTF8String name) {
            this.name = name;
        }

        private Node buildNode() {
            Node n = null;
            boolean isPackages = false;
            boolean isModules = false;
            String strName = name.toString();
            if (strName.startsWith("" + PACKAGES_STRING)) {
                isPackages = true;
            } else {
                if (strName.startsWith("" + MODULES_STRING)) {
                    isModules = true;
                }
            }
            if (!isModules && !isPackages) {
                return null;
            }

            ImageLocation loc = findLocation(name);

            if (loc != null) { // A sub tree node
                if (isPackages) {
                    n = handlePackages(strName, loc);
                } else { // modules sub tree
                    n = handleModulesSubTree(strName, loc);
                }
            } else { // Asking for a resource? /modules/java.base/java/lang/Object.class
                if (isModules) {
                    n = handleResource(strName, loc);
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

        private Node handlePackages(String name, ImageLocation loc) {
            long size = loc.getUncompressedSize();
            Node n = null;
            // Only possiblities are /packages, /packages/package/module
            if (name.equals("" + PACKAGES_STRING)) {
                visitLocation(loc, (childloc) -> {
                    findNode(childloc.getFullName());
                });
                packagesDir.setCompleted(true);
                n = packagesDir;
            } else {
                if (size != 0) { // children are links to module
                    String pkgName = getBaseExt(loc);
                    Directory pkgDir = newDirectory(packagesDir,
                            packagesDir.getName().concat(SLASH_STRING, new UTF8String(pkgName)));
                    visitLocation(loc, (childloc) -> {
                        findNode(childloc.getFullName());
                    });
                    pkgDir.setCompleted(true);
                    n = pkgDir;
                } else { // Link to module
                    String pkgName = loc.getParentString();
                    String modName = getBaseExt(loc);
                    Node targetNode = findNode(MODULES_STRING + "/" + modName);
                    if (targetNode != null) {
                        UTF8String pkgDirName = packagesDir.getName().concat(SLASH_STRING, new UTF8String(pkgName));
                        Directory pkgDir = (Directory) nodes.get(pkgDirName);
                        Node linkNode = newLinkNode(pkgDir,
                                pkgDir.getName().concat(SLASH_STRING, new UTF8String(modName)), targetNode);
                        n = linkNode;
                    }
                }
            }
            return n;
        }

        private Node handleModulesSubTree(String name, ImageLocation loc) {
            Node n;
            Directory dir = makeDirectories(loc.getFullName());
            visitLocation(loc, (childloc) -> {
                String path = childloc.getFullNameString();
                if (path.startsWith(MODULES_STRING.toString())) { // a package
                    makeDirectories(childloc.getFullName());
                } else { // a resource
                    makeDirectories(childloc.buildName(true, true, false));
                    newResource(dir, childloc);
                }
            });
            dir.setCompleted(true);
            n = dir;
            return n;
        }

        private Node handleResource(String name, ImageLocation loc) {
            Node n = null;
            String locationPath = name.substring((MODULES_STRING).length());
            ImageLocation resourceLoc = findLocation(locationPath);
            if (resourceLoc != null) {
                Directory dir = makeDirectories(resourceLoc.buildName(true, true, false));
                Resource res = newResource(dir, resourceLoc);
                n = res;
            }
            return n;
        }

        private String getBaseExt(ImageLocation loc) {
            String base = loc.getBaseString();
            String ext = loc.getExtensionString();
            if (ext != null && !ext.isEmpty()) {
                base = base + "." + ext;
            }
            return base;
        }
    }

    public synchronized Node findNode(UTF8String name) {
        buildRootDirectory();
        Node n = nodes.get(name);
        if (n == null || !n.isCompleted()) {
            NodeBuilder builder = new NodeBuilder(name);
            n = builder.buildNode();
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
                Path file = Paths.get(imagePath());
                attrs = Files.readAttributes(file, BasicFileAttributes.class);
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
            imageFileAttributes = attrs;
        }
        return attrs;
    }

    private synchronized Directory buildRootDirectory() {
        if (rootDir != null) {
            return rootDir;
        }

        // FIXME no time information per resource in jimage file (yet?)
        // we use file attributes of jimage itself.
        // root directory
        rootDir = newDirectory(null, ROOT_STRING);
        rootDir.setIsRootDir();

        // /packages dir
        packagesDir = newDirectory(rootDir, PACKAGES_STRING);
        packagesDir.setIsPackagesDir();

        // /modules dir
        modulesDir = newDirectory(rootDir, MODULES_STRING);
        modulesDir.setIsModulesDir();

        rootDir.setCompleted(true);
        return rootDir;
    }

    private Directory newDirectory(Directory parent, UTF8String name) {
        Directory dir = Directory.create(parent, name, imageFileAttributes());
        nodes.put(dir.getName(), dir);
        return dir;
    }

    private Resource newResource(Directory parent, ImageLocation loc) {
        Resource res = Resource.create(parent, loc, imageFileAttributes());
        nodes.put(res.getName(), res);
        return res;
    }

    private LinkNode newLinkNode(Directory dir, UTF8String name, Node link) {
        LinkNode linkNode = LinkNode.create(dir, name, link);
        nodes.put(linkNode.getName(), linkNode);
        return linkNode;
    }

    private List<UTF8String> dirs(UTF8String parent) {
        List<UTF8String> splits = new ArrayList<>();

        for (int i = 1; i < parent.length(); i++) {
            if (parent.byteAt(i) == '/') {
                splits.add(parent.substring(0, i));
            }
        }

        splits.add(parent);

        return splits;
    }

    private Directory makeDirectories(UTF8String parent) {
        Directory last = rootDir;
        List<UTF8String> dirs = dirs(parent);

        for (UTF8String dir : dirs) {
            Directory nextDir = (Directory) nodes.get(dir);
            if (nextDir == null) {
                nextDir = newDirectory(last, dir);
            }
            last = nextDir;
        }

        return last;
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
