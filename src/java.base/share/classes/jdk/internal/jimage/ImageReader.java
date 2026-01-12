/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A view over the entries of a jimage file with a unified namespace suitable
 * for file system use. The jimage entries (resources, module and package
 * information) are mapped into a unified hierarchy of named nodes, which serve
 * as the underlying structure for {@code JrtFileSystem} and other utilities.
 *
 * <p>Entries in jimage are expressed as one of three {@link Node} types;
 * resource nodes, directory nodes and link nodes.
 *
 * <p>When remapping jimage entries, jimage location names (e.g. {@code
 * "/java.base/java/lang/Integer.class"}) are prefixed with {@code "/modules"}
 * to form the names of resource nodes. This aligns with the naming of module
 * entries in jimage (e.g. "/modules/java.base/java/lang"), which appear as
 * directory nodes in {@code ImageReader}.
 *
 * <p>Package entries (e.g. {@code "/packages/java.lang"} appear as directory
 * nodes containing link nodes, which resolve back to the root directory of the
 * module in which that package exists (e.g. {@code "/modules/java.base"}).
 * Unlike other nodes, the jimage file does not contain explicit entries for
 * link nodes, and their existence is derived only from the contents of the
 * parent directory.
 *
 * <p>While similar to {@code BasicImageReader}, this class is not a conceptual
 * subtype of it, and deliberately hides types such as {@code ImageLocation} to
 * give a focused API based only on nodes.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public final class ImageReader implements AutoCloseable {
    private final SharedImageReader reader;

    private volatile boolean closed;

    private ImageReader(SharedImageReader reader) {
        this.reader = reader;
    }

    /**
     * Opens an image reader for a jimage file at the specified path, using the
     * given byte order.
     */
    public static ImageReader open(Path imagePath, ByteOrder byteOrder) throws IOException {
        Objects.requireNonNull(imagePath);
        Objects.requireNonNull(byteOrder);

        return SharedImageReader.open(imagePath, byteOrder);
    }

    /**
     * Opens an image reader for a jimage file at the specified path, using the
     * platform native byte order.
     */
    public static ImageReader open(Path imagePath) throws IOException {
        return open(imagePath, ByteOrder.nativeOrder());
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            throw new IOException("image file already closed");
        }
        reader.close(this);
        closed = true;
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("image file closed");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("image file closed");
        }
    }

    /**
     * Finds the node with the given name.
     *
     * @param name a node name of the form {@code "/modules/<module>/...} or
     *     {@code "/packages/<package>/...}.
     * @return a node representing a resource, directory or symbolic link.
     */
    public Node findNode(String name) throws IOException {
        ensureOpen();
        return reader.findNode(name);
    }

    /**
     * Returns a resource node in the given module, or null if no resource of
     * that name exists.
     *
     * <p>This is equivalent to:
     * <pre>{@code
     * findNode("/modules/" + moduleName + "/" + resourcePath)
     * }</pre>
     * but more performant, and returns {@code null} for directories.
     *
     * @param moduleName The module name of the requested resource.
     * @param resourcePath Trailing module-relative resource path, not starting
     *     with {@code '/'}.
     */
    public Node findResourceNode(String moduleName, String resourcePath)
            throws IOException {
        ensureOpen();
        return reader.findResourceNode(moduleName, resourcePath);
    }

    /**
     * Returns whether a resource exists in the given module.
     *
     * <p>This is equivalent to:
     * <pre>{@code
     * findResourceNode(moduleName, resourcePath) != null
     * }</pre>
     * but more performant, and will not create or cache new nodes.
     *
     * @param moduleName The module name of the resource being tested for.
     * @param resourcePath Trailing module-relative resource path, not starting
     *     with {@code '/'}.
     */
    public boolean containsResource(String moduleName, String resourcePath)
            throws IOException {
        ensureOpen();
        return reader.containsResource(moduleName, resourcePath);
    }

    /**
     * Returns a copy of the content of a resource node. The buffer returned by
     * this method is not cached by the node, and each call returns a new array
     * instance.
     *
     * @throws IOException if the content cannot be returned (including if the
     * given node is not a resource node).
     */
    public byte[] getResource(Node node) throws IOException {
        ensureOpen();
        return reader.getResource(node);
    }

    /**
     * Returns the content of a resource node in a newly allocated byte buffer.
     */
    public ByteBuffer getResourceBuffer(Node node) {
        requireOpen();
        if (!node.isResource()) {
            throw new IllegalArgumentException("Not a resource node: " + node);
        }
        return reader.getResourceBuffer(node.getLocation());
    }

    private static final class SharedImageReader extends BasicImageReader {
        private static final Map<Path, SharedImageReader> OPEN_FILES = new HashMap<>();
        private static final String MODULES_ROOT = "/modules";
        private static final String PACKAGES_ROOT = "/packages";
        // There are >30,000 nodes in a complete jimage tree, and even relatively
        // common tasks (e.g. starting up javac) load somewhere in the region of
        // 1000 classes. Thus, an initial capacity of 2000 is a reasonable guess.
        private static final int INITIAL_NODE_CACHE_CAPACITY = 2000;

        // List of openers for this shared image.
        private final Set<ImageReader> openers = new HashSet<>();

        // Attributes of the jimage file. The jimage file does not contain
        // attributes for the individual resources (yet). We use attributes
        // of the jimage file itself (creation, modification, access times).
        private final BasicFileAttributes imageFileAttributes;

        // Cache of all user visible nodes, guarded by synchronizing 'this' instance.
        private final Map<String, Node> nodes;
        // Used to classify ImageLocation instances without string comparison.
        private final int modulesStringOffset;
        private final int packagesStringOffset;

        private SharedImageReader(Path imagePath, ByteOrder byteOrder) throws IOException {
            super(imagePath, byteOrder);
            this.imageFileAttributes = Files.readAttributes(imagePath, BasicFileAttributes.class);
            this.nodes = new HashMap<>(INITIAL_NODE_CACHE_CAPACITY);
            // Pick stable jimage names from which to extract string offsets (we cannot
            // use "/modules" or "/packages", since those have a module offset of zero).
            this.modulesStringOffset = getModuleOffset("/modules/java.base");
            this.packagesStringOffset = getModuleOffset("/packages/java.lang");

            // Node creation is very lazy, so we can just make the top-level directories
            // now without the risk of triggering the building of lots of other nodes.
            Directory packages = newDirectory(PACKAGES_ROOT);
            nodes.put(packages.getName(), packages);
            Directory modules = newDirectory(MODULES_ROOT);
            nodes.put(modules.getName(), modules);

            Directory root = newDirectory("/");
            root.setChildren(Arrays.asList(packages, modules));
            nodes.put(root.getName(), root);
        }

        /**
         * Returns the offset of the string denoting the leading "module" segment in
         * the given path (e.g. {@code <module>/<path>}). We can't just pass in the
         * {@code /<module>} string here because that has a module offset of zero.
         */
        private int getModuleOffset(String path) {
            ImageLocation location = findLocation(path);
            assert location != null : "Cannot find expected jimage location: " + path;
            int offset = location.getModuleOffset();
            assert offset != 0 : "Invalid module offset for jimage location: " + path;
            return offset;
        }

        private static ImageReader open(Path imagePath, ByteOrder byteOrder) throws IOException {
            Objects.requireNonNull(imagePath);
            Objects.requireNonNull(byteOrder);

            synchronized (OPEN_FILES) {
                SharedImageReader reader = OPEN_FILES.get(imagePath);

                if (reader == null) {
                    // Will fail with an IOException if wrong byteOrder.
                    reader =  new SharedImageReader(imagePath, byteOrder);
                    OPEN_FILES.put(imagePath, reader);
                } else if (reader.getByteOrder() != byteOrder) {
                    throw new IOException("\"" + reader.getName() + "\" is not an image file");
                }

                ImageReader image = new ImageReader(reader);
                reader.openers.add(image);

                return image;
            }
        }

        public void close(ImageReader image) throws IOException {
            Objects.requireNonNull(image);

            synchronized (OPEN_FILES) {
                if (!openers.remove(image)) {
                    throw new IOException("image file already closed");
                }

                if (openers.isEmpty()) {
                    close();
                    nodes.clear();

                    if (!OPEN_FILES.remove(this.getImagePath(), this)) {
                        throw new IOException("image file not found in open list");
                    }
                }
            }
        }

        /**
         * Returns a node with the given name, or null if no resource or directory of
         * that name exists.
         *
         * <p>Note that there is no reentrant calling back to this method from within
         * the node handling code.
         *
         * @param name an absolute, {@code /}-separated path string, prefixed with either
         *     "/modules" or "/packages".
         */
        synchronized Node findNode(String name) {
            Node node = nodes.get(name);
            if (node == null) {
                // We cannot get the root paths ("/modules" or "/packages") here
                // because those nodes are already in the nodes cache.
                if (name.startsWith(MODULES_ROOT + "/")) {
                    // This may perform two lookups, one for a directory (in
                    // "/modules/...") and one for a non-prefixed resource
                    // (with "/modules" removed).
                    node = buildModulesNode(name);
                } else if (name.startsWith(PACKAGES_ROOT + "/")) {
                    node = buildPackagesNode(name);
                }
                if (node != null) {
                    nodes.put(node.getName(), node);
                }
            } else if (!node.isCompleted()) {
                // Only directories can be incomplete.
                assert node instanceof Directory : "Invalid incomplete node: " + node;
                completeDirectory((Directory) node);
            }
            assert node == null || node.isCompleted() : "Incomplete node: " + node;
            return node;
        }

        /**
         * Returns a resource node in the given module, or null if no resource of
         * that name exists.
         *
         * <p>Note that there is no reentrant calling back to this method from within
         * the node handling code.
         */
        Node findResourceNode(String moduleName, String resourcePath) {
            // Unlike findNode(), this method makes only one lookup in the
            // underlying jimage, but can only reliably return resource nodes.
            if (moduleName.indexOf('/') >= 0) {
                throw new IllegalArgumentException("invalid module name: " + moduleName);
            }
            String nodeName = MODULES_ROOT + "/" + moduleName + "/" + resourcePath;
            // Synchronize as tightly as possible to reduce locking contention.
            synchronized (this) {
                Node node = nodes.get(nodeName);
                if (node == null) {
                    ImageLocation loc = findLocation(moduleName, resourcePath);
                    if (loc != null && isResource(loc)) {
                        node = newResource(nodeName, loc);
                        nodes.put(node.getName(), node);
                    }
                    return node;
                } else {
                    return node.isResource() ? node : null;
                }
            }
        }

        /**
         * Returns whether a resource exists in the given module.
         *
         * <p>This method is expected to be called frequently for resources
         * which do not exist in the given module (e.g. as part of classpath
         * search). As such, it skips checking the nodes cache and only checks
         * for an entry in the jimage file, as this is faster if the resource
         * is not present. This also means it doesn't need synchronization.
         */
        boolean containsResource(String moduleName, String resourcePath) {
            if (moduleName.indexOf('/') >= 0) {
                throw new IllegalArgumentException("invalid module name: " + moduleName);
            }
            // If the given module name is 'modules', then 'isResource()'
            // returns false to prevent false positives.
            ImageLocation loc = findLocation(moduleName, resourcePath);
            return loc != null && isResource(loc);
        }

        /**
         * Builds a node in the "/modules/..." namespace.
         *
         * <p>Called by {@link #findNode(String)} if a {@code /modules/...} node
         * is not present in the cache.
         */
        private Node buildModulesNode(String name) {
            assert name.startsWith(MODULES_ROOT + "/") : "Invalid module node name: " + name;
            // Returns null for non-directory resources, since the jimage name does not
            // start with "/modules" (e.g. "/java.base/java/lang/Object.class").
            ImageLocation loc = findLocation(name);
            if (loc != null) {
                assert name.equals(loc.getFullName()) : "Mismatched location for directory: " + name;
                assert isModulesSubdirectory(loc) : "Invalid modules directory: " + name;
                return completeModuleDirectory(newDirectory(name), loc);
            }
            // Now try the non-prefixed resource name, but be careful to avoid false
            // positives for names like "/modules/modules/xxx" which could return a
            // location of a directory entry.
            loc = findLocation(name.substring(MODULES_ROOT.length()));
            return loc != null && isResource(loc) ? newResource(name, loc) : null;
        }

        /**
         * Builds a node in the "/packages/..." namespace.
         *
         * <p>Called by {@link #findNode(String)} if a {@code /packages/...} node
         * is not present in the cache.
         */
        private Node buildPackagesNode(String name) {
            // There are only locations for the root "/packages" or "/packages/xxx"
            // directories, but not the symbolic links below them (the links can be
            // entirely derived from the name information in the parent directory).
            // However, unlike resources this means that we do not have a constant
            // time lookup for link nodes when creating them.
            int packageStart = PACKAGES_ROOT.length() + 1;
            int packageEnd = name.indexOf('/', packageStart);
            if (packageEnd == -1) {
                ImageLocation loc = findLocation(name);
                return loc != null ? completePackageDirectory(newDirectory(name), loc) : null;
            } else {
                // We cannot assume that the parent directory exists for a link node, since
                // the given name is untrusted and could reference a non-existent link.
                // However, if the parent directory is present, we can conclude that the
                // given name was not a valid link (or else it would already be cached).
                String dirName = name.substring(0, packageEnd);
                if (!nodes.containsKey(dirName)) {
                    ImageLocation loc = findLocation(dirName);
                    // If the parent location doesn't exist, the link node cannot exist.
                    if (loc != null) {
                        nodes.put(dirName, completePackageDirectory(newDirectory(dirName), loc));
                        // When the parent is created its child nodes are created and cached,
                        // but this can still return null if given name wasn't a valid link.
                        return nodes.get(name);
                    }
                }
            }
            return null;
        }

        /** Completes a directory by ensuring its child list is populated correctly. */
        private void completeDirectory(Directory dir) {
            String name = dir.getName();
            // Since the node exists, we can assert that its name starts with
            // either "/modules" or "/packages", making differentiation easy.
            // It also means that the name is valid, so it must yield a location.
            assert name.startsWith(MODULES_ROOT) || name.startsWith(PACKAGES_ROOT);
            ImageLocation loc = findLocation(name);
            assert loc != null && name.equals(loc.getFullName()) : "Invalid location for name: " + name;
            // We cannot use 'isXxxSubdirectory()' methods here since we could
            // be given a top-level directory (for which that test doesn't work).
            // The string MUST start "/modules" or "/packages" here.
            if (name.charAt(1) == 'm') {
                completeModuleDirectory(dir, loc);
            } else {
                completePackageDirectory(dir, loc);
            }
            assert dir.isCompleted() : "Directory must be complete by now: " + dir;
        }

        /**
         * Completes a modules directory by setting the list of child nodes.
         *
         * <p>The given directory can be the top level {@code /modules} directory,
         * so it is NOT safe to use {@code isModulesSubdirectory(loc)} here.
         */
        private Directory completeModuleDirectory(Directory dir, ImageLocation loc) {
            assert dir.getName().equals(loc.getFullName()) : "Mismatched location for directory: " + dir;
            List<Node> children = createChildNodes(loc, childLoc -> {
                if (isModulesSubdirectory(childLoc)) {
                    return nodes.computeIfAbsent(childLoc.getFullName(), this::newDirectory);
                } else {
                    // Add "/modules" prefix to image location paths to get node names.
                    String resourceName = childLoc.getFullName(true);
                    return nodes.computeIfAbsent(resourceName, n -> newResource(n, childLoc));
                }
            });
            dir.setChildren(children);
            return dir;
        }

        /**
         * Completes a package directory by setting the list of child nodes.
         *
         * <p>The given directory can be the top level {@code /packages} directory,
         * so it is NOT safe to use {@code isPackagesSubdirectory(loc)} here.
         */
        private Directory completePackageDirectory(Directory dir, ImageLocation loc) {
            assert dir.getName().equals(loc.getFullName()) : "Mismatched location for directory: " + dir;
            // The only directories in the "/packages" namespace are "/packages" or
            // "/packages/<package>". However, unlike "/modules" directories, the
            // location offsets mean different things.
            List<Node> children;
            if (dir.getName().equals(PACKAGES_ROOT)) {
                // Top-level directory just contains a list of subdirectories.
                children = createChildNodes(loc, c -> nodes.computeIfAbsent(c.getFullName(), this::newDirectory));
            } else {
                // A package directory's content is array of offset PAIRS in the
                // Strings table, but we only need the 2nd value of each pair.
                IntBuffer intBuffer = getOffsetBuffer(loc);
                int offsetCount = intBuffer.capacity();
                assert (offsetCount & 0x1) == 0 : "Offset count must be even: " + offsetCount;
                children = new ArrayList<>(offsetCount / 2);
                // Iterate the 2nd offset in each pair (odd indices).
                for (int i = 1; i < offsetCount; i += 2) {
                    String moduleName = getString(intBuffer.get(i));
                    children.add(nodes.computeIfAbsent(
                            dir.getName() + "/" + moduleName,
                            n -> newLinkNode(n, MODULES_ROOT + "/" + moduleName)));
                }
            }
            // This only happens once and "completes" the directory.
            dir.setChildren(children);
            return dir;
        }

        /**
         * Creates the list of child nodes for a {@code Directory} based on a given
         *
         * <p>Note: This cannot be used for package subdirectories as they have
         * child offsets stored differently to other directories.
         */
        private List<Node> createChildNodes(ImageLocation loc, Function<ImageLocation, Node> newChildFn) {
            IntBuffer offsets = getOffsetBuffer(loc);
            int childCount = offsets.capacity();
            List<Node> children = new ArrayList<>(childCount);
            for (int i = 0; i < childCount; i++) {
                children.add(newChildFn.apply(getLocation(offsets.get(i))));
            }
            return children;
        }

        /** Helper to extract the integer offset buffer from a directory location. */
        private IntBuffer getOffsetBuffer(ImageLocation dir) {
            assert !isResource(dir) : "Not a directory: " + dir.getFullName();
            byte[] offsets = getResource(dir);
            ByteBuffer buffer = ByteBuffer.wrap(offsets);
            buffer.order(getByteOrder());
            return buffer.asIntBuffer();
        }

        /**
         * Efficiently determines if an image location is a resource.
         *
         * <p>A resource must have a valid module associated with it, so its
         * module offset must be non-zero, and not equal to the offsets for
         * "/modules/..." or "/packages/..." entries.
         */
        private boolean isResource(ImageLocation loc) {
            int moduleOffset = loc.getModuleOffset();
            return moduleOffset != 0
                    && moduleOffset != modulesStringOffset
                    && moduleOffset != packagesStringOffset;
        }

        /**
         * Determines if an image location is a directory in the {@code /modules}
         * namespace (if so, the location name is the node name).
         *
         * <p>In jimage, every {@code ImageLocation} under {@code /modules/} is a
         * directory and has the same value for {@code getModule()}, and {@code
         * getModuleOffset()}.
         */
        private boolean isModulesSubdirectory(ImageLocation loc) {
            return loc.getModuleOffset() == modulesStringOffset;
        }

        /**
         * Creates an "incomplete" directory node with no child nodes set.
         * Directories need to be "completed" before they are returned by
         * {@link #findNode(String)}.
         */
        private Directory newDirectory(String name) {
            return new Directory(name, imageFileAttributes);
        }

        /**
         * Creates a new resource from an image location. This is the only case
         * where the image location name does not match the requested node name.
         * In image files, resource locations are NOT prefixed by {@code /modules}.
         */
        private Resource newResource(String name, ImageLocation loc) {
            assert name.equals(loc.getFullName(true)) : "Mismatched location for resource: " + name;
            return new Resource(name, loc, imageFileAttributes);
        }

        /**
         * Creates a new link node pointing at the given target name.
         *
         * <p>Note that target node is resolved each time {@code resolve()} is called,
         * so if a link node is retained after its reader is closed, it will fail.
         */
        private LinkNode newLinkNode(String name, String targetName) {
            return new LinkNode(name, () -> findNode(targetName), imageFileAttributes);
        }

        /** Returns the content of a resource node. */
        private byte[] getResource(Node node) throws IOException {
            // We could have been given a non-resource node here.
            if (node.isResource()) {
                return super.getResource(node.getLocation());
            }
            throw new IOException("Not a resource: " + node);
        }
    }

    /**
     * A directory, resource or symbolic link.
     *
     * <h3 id="node_equality">Node Equality</h3>
     *
     * Nodes are identified solely by their name, and it is not valid to attempt
     * to compare nodes from different reader instances. Different readers may
     * produce nodes with the same names, but different contents.
     *
     * <p>Furthermore, since a {@link ImageReader} provides "perfect" caching of
     * nodes, equality of nodes from the same reader is equivalent to instance
     * identity.
     */
    public abstract static class Node {
        private final String name;
        private final BasicFileAttributes fileAttrs;

        /**
         * Creates an abstract {@code Node}, which is either a resource, directory
         * or symbolic link.
         *
         * <p>This constructor is only non-private so it can be used by the
         * {@code ExplodedImage} class, and must not be used otherwise.
         */
        protected Node(String name, BasicFileAttributes fileAttrs) {
            this.name = Objects.requireNonNull(name);
            this.fileAttrs = Objects.requireNonNull(fileAttrs);
        }

        // A node is completed when all its direct children have been built.
        // As such, non-directory nodes are always complete.
        boolean isCompleted() {
            return true;
        }

        // Only resources can return a location.
        ImageLocation getLocation() {
            throw new IllegalStateException("not a resource: " + getName());
        }

        /**
         * Returns the name of this node (e.g. {@code
         * "/modules/java.base/java/lang/Object.class"} or {@code
         * "/packages/java.lang"}).
         *
         * <p>Note that for resource nodes this is NOT the underlying jimage
         * resource name (it is prefixed with {@code "/modules"}).
         */
        public final String getName() {
            return name;
        }

        /**
         * Returns file attributes for this node. The value returned may be the
         * same for all nodes, and should not be relied upon for accuracy.
         */
        public final BasicFileAttributes getFileAttributes() {
            return fileAttrs;
        }

        /**
         * Resolves a symbolic link to its target node. If this code is not a
         * symbolic link, then it resolves to itself.
         */
        public final Node resolveLink() {
            return resolveLink(false);
        }

        /**
         * Resolves a symbolic link to its target node. If this code is not a
         * symbolic link, then it resolves to itself.
         */
        public Node resolveLink(boolean recursive) {
            return this;
        }

        /** Returns whether this node is a symbolic link. */
        public boolean isLink() {
            return false;
        }

        /**
         * Returns whether this node is a directory. Directory nodes can have
         * {@link #getChildNames()} invoked to get the fully qualified names
         * of any child nodes.
         */
        public boolean isDirectory() {
            return false;
        }

        /**
         * Returns whether this node is a resource. Resource nodes can have
         * their contents obtained via {@link ImageReader#getResource(Node)}
         * or {@link ImageReader#getResourceBuffer(Node)}.
         */
        public boolean isResource() {
            return false;
        }

        /**
         * Returns the fully qualified names of any child nodes for a directory.
         *
         * <p>By default, this method throws {@link IllegalStateException} and
         * is overridden for directories.
         */
        public Stream<String> getChildNames() {
            throw new IllegalStateException("not a directory: " + getName());
        }

        /**
         * Returns the uncompressed size of this node's content. If this node is
         * not a resource, this method returns zero.
         */
        public long size() {
            return 0L;
        }

        /**
         * Returns the compressed size of this node's content. If this node is
         * not a resource, this method returns zero.
         */
        public long compressedSize() {
            return 0L;
        }

        /**
         * Returns the extension string of a resource node. If this node is not
         * a resource, this method returns null.
         */
        public String extension() {
            return null;
        }

        @Override
        public final String toString() {
            return getName();
        }

        /** See <a href="#node_equality">Node Equality</a>. */
        @Override
        public final int hashCode() {
            return name.hashCode();
        }

        /** See <a href="#node_equality">Node Equality</a>. */
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

    /**
     * Directory node (referenced from a full path, without a trailing '/').
     *
     * <p>Directory nodes have two distinct states:
     * <ul>
     *     <li>Incomplete: The child list has not been set.
     *     <li>Complete: The child list has been set.
     * </ul>
     *
     * <p>When a directory node is returned by {@link ImageReader#findNode(String)}
     * it is always complete, but this DOES NOT mean that its child nodes are
     * complete yet.
     *
     * <p>To avoid users being able to access incomplete child nodes, the
     * {@code Node} API offers only a way to obtain child node names, forcing
     * callers to invoke {@code findNode()} if they need to access the child
     * node itself.
     *
     * <p>This approach allows directories to be implemented lazily with respect
     * to child nodes, while retaining efficiency when child nodes are accessed
     * (since any incomplete nodes will be created and placed in the node cache
     * when the parent was first returned to the user).
     */
    private static final class Directory extends Node {
        // Monotonic reference, will be set to the unmodifiable child list exactly once.
        private List<Node> children = null;

        private Directory(String name, BasicFileAttributes fileAttrs) {
            super(name, fileAttrs);
        }

        @Override
        boolean isCompleted() {
            return children != null;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public Stream<String> getChildNames() {
            if (children != null) {
                return children.stream().map(Node::getName);
            }
            throw new IllegalStateException("Cannot get child nodes of an incomplete directory: " + getName());
        }

        private void setChildren(List<Node> children) {
            assert this.children == null : this + ": Cannot set child nodes twice!";
            this.children = Collections.unmodifiableList(children);
        }
    }
    /**
     * Resource node (e.g. a ".class" entry, or any other data resource).
     *
     * <p>Resources are leaf nodes referencing an underlying image location. They
     * are lightweight, and do not cache their contents.
     *
     * <p>Unlike directories (where the node name matches the jimage path for the
     * corresponding {@code ImageLocation}), resource node names are NOT the same
     * as the corresponding jimage path. The difference is that node names for
     * resources are prefixed with "/modules", which is missing from the
     * equivalent jimage path.
     */
    private static class Resource extends Node {
        private final ImageLocation loc;

        private Resource(String name, ImageLocation loc, BasicFileAttributes fileAttrs) {
            super(name, fileAttrs);
            this.loc = loc;
        }

        @Override
        ImageLocation getLocation() {
            return loc;
        }

        @Override
        public boolean isResource() {
            return true;
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
    }

    /**
     * Link node (a symbolic link to a top-level modules directory).
     *
     * <p>Link nodes resolve their target by invoking a given supplier, and do
     * not cache the result. Since nodes are cached by the {@code ImageReader},
     * this means that only the first call to {@link #resolveLink(boolean)}
     * could do any significant work.
     */
    private static class LinkNode extends Node {
        private final Supplier<Node> link;

        private LinkNode(String name, Supplier<Node> link, BasicFileAttributes fileAttrs) {
            super(name, fileAttrs);
            this.link = link;
        }

        @Override
        public Node resolveLink(boolean recursive) {
            // No need to use or propagate the recursive flag, since the target
            // cannot possibly be a link node (links only point to directories).
            return link.get();
        }

        @Override
        public boolean isLink() {
            return true;
        }
    }
}
