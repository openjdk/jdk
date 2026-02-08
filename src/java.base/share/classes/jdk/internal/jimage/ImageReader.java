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

import jdk.internal.jimage.ImageLocation.LocationType;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static jdk.internal.jimage.ImageLocation.LocationType.MODULES_DIR;
import static jdk.internal.jimage.ImageLocation.LocationType.MODULES_ROOT;
import static jdk.internal.jimage.ImageLocation.LocationType.PACKAGES_DIR;
import static jdk.internal.jimage.ImageLocation.LocationType.RESOURCE;
import static jdk.internal.jimage.ImageLocation.MODULES_PREFIX;
import static jdk.internal.jimage.ImageLocation.PACKAGES_PREFIX;
import static jdk.internal.jimage.ImageLocation.PREVIEW_INFIX;

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
     * Opens an image reader for a jimage file at the specified path.
     *
     * @param imagePath file system path of the jimage file.
     * @param mode whether to return preview resources.
     */
    public static ImageReader open(Path imagePath, PreviewMode mode) throws IOException {
        return open(imagePath, ByteOrder.nativeOrder(), mode);
    }

    /**
     * Opens an image reader for a jimage file at the specified path.
     *
     * @param imagePath file system path of the jimage file.
     * @param byteOrder the byte-order to be used when reading the jimage file.
     * @param mode controls whether preview resources are visible.
     */
    public static ImageReader open(Path imagePath, ByteOrder byteOrder, PreviewMode mode)
            throws IOException {
        Objects.requireNonNull(imagePath);
        Objects.requireNonNull(byteOrder);
        return SharedImageReader.open(imagePath, byteOrder, mode.isPreviewModeEnabled());
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

    // Package protected for use only by SystemImageReader.
    ResourceEntries getResourceEntries() {
        return reader.getResourceEntries();
    }

    private static final class SharedImageReader extends BasicImageReader {
        // There are >30,000 nodes in a complete jimage tree, and even relatively
        // common tasks (e.g. starting up javac) load somewhere in the region of
        // 1000 classes. Thus, an initial capacity of 2000 is a reasonable guess.
        private static final int INITIAL_NODE_CACHE_CAPACITY = 2000;

        static final class ReaderKey {
            private final Path imagePath;
            private final boolean previewMode;

            public ReaderKey(Path imagePath, boolean previewMode) {
                this.imagePath = imagePath;
                this.previewMode = previewMode;
            }

            @Override
            public boolean equals(Object obj) {
                // No pattern variables here (Java 8 compatible source).
                if (obj instanceof ReaderKey) {
                    ReaderKey other = (ReaderKey) obj;
                    return this.imagePath.equals(other.imagePath) && this.previewMode == other.previewMode;
                }
                return false;
            }

            @Override
            public int hashCode() {
                return imagePath.hashCode() ^ Boolean.hashCode(previewMode);
            }
        }

        private static final Map<ReaderKey, SharedImageReader> OPEN_FILES = new HashMap<>();

        // List of openers for this shared image.
        private final Set<ImageReader> openers = new HashSet<>();

        // Attributes of the jimage file. The jimage file does not contain
        // attributes for the individual resources (yet). We use attributes
        // of the jimage file itself (creation, modification, access times).
        private final BasicFileAttributes imageFileAttributes;

        // Cache of all user visible nodes, guarded by synchronizing 'this' instance.
        private final Map<String, Node> nodes;

        // Preview mode support.
        private final boolean previewMode;
        // A relativized mapping from non-preview name to directories containing
        // preview-only nodes. This is used to add preview-only content to
        // directories as they are completed.
        private final HashMap<String, Directory> previewDirectoriesToMerge;

        private SharedImageReader(Path imagePath, ByteOrder byteOrder, boolean previewMode) throws IOException {
            super(imagePath, byteOrder);
            this.imageFileAttributes = Files.readAttributes(imagePath, BasicFileAttributes.class);
            this.nodes = new HashMap<>(INITIAL_NODE_CACHE_CAPACITY);
            this.previewMode = previewMode;

            // Node creation is very lazy, so we can just make the top-level directories
            // now without the risk of triggering the building of lots of other nodes.
            Directory packages = ensureCached(newDirectory(PACKAGES_PREFIX));
            Directory modules = ensureCached(newDirectory(MODULES_PREFIX));

            Directory root = newDirectory("/");
            root.setChildren(Arrays.asList(packages, modules));
            ensureCached(root);

            // By scanning the /packages directory information early we can determine
            // which module/package pairs have preview resources, and build the (small)
            // set of preview nodes early. This also ensures that preview-only entries
            // in the /packages directory are not present in non-preview mode.
            this.previewDirectoriesToMerge = previewMode ? new HashMap<>() : null;
            packages.setChildren(processPackagesDirectory(previewMode));
        }

        /**
         * Process {@code "/packages/xxx"} entries to build the child nodes for the
         * root {@code "/packages"} node. Preview-only entries will be skipped if
         * {@code previewMode == false}.
         *
         * <p>If {@code previewMode == true}, this method also populates the {@link
         * #previewDirectoriesToMerge} map with any preview-only nodes, to be merged
         * into directories as they are completed. It also caches preview resources
         * and preview-only directories for direct lookup.
         */
        private ArrayList<Node> processPackagesDirectory(boolean previewMode) {
            ImageLocation pkgRoot = findLocation(PACKAGES_PREFIX);
            assert pkgRoot != null : "Invalid jimage file";
            IntBuffer offsets = getOffsetBuffer(pkgRoot);
            ArrayList<Node> pkgDirs = new ArrayList<>(offsets.capacity());
            // Package path to module map, sorted in reverse order so that
            // longer child paths get processed first.
            Map<String, List<String>> previewPackagesToModules =
                    new TreeMap<>(Comparator.reverseOrder());
            for (int i = 0; i < offsets.capacity(); i++) {
                ImageLocation pkgDir = getLocation(offsets.get(i));
                int flags = pkgDir.getFlags();
                // A package subdirectory is "preview only" if all the modules
                // it references have that package marked as preview only.
                // Skipping these entries avoids empty package subdirectories.
                if (previewMode || !ImageLocation.isPreviewOnly(flags)) {
                    pkgDirs.add(ensureCached(newDirectory(pkgDir.getFullName())));
                }
                if (previewMode && ImageLocation.hasPreviewVersion(flags)) {
                    // Only do this in preview mode for the small set of packages with
                    // preview versions (the number of preview entries should be small).
                    List<String> moduleNames = new ArrayList<>();
                    ModuleReference.readNameOffsets(getOffsetBuffer(pkgDir), /*normal*/ false, /*preview*/ true)
                            .forEachRemaining(n -> moduleNames.add(getString(n)));
                    previewPackagesToModules.put(pkgDir.getBase().replace('.', '/'), moduleNames);
                }
            }
            // Reverse sorted map means child directories are processed first.
            previewPackagesToModules.forEach((pkgPath, modules) ->
                    modules.forEach(modName -> processPreviewDir(MODULES_PREFIX + "/" + modName, pkgPath)));
            // We might have skipped some preview-only package entries.
            pkgDirs.trimToSize();
            return pkgDirs;
        }

        void processPreviewDir(String namePrefix, String pkgPath) {
            String previewDirName = namePrefix + PREVIEW_INFIX + "/" + pkgPath;
            ImageLocation previewLoc = findLocation(previewDirName);
            assert previewLoc != null : "Missing preview directory location: " + previewDirName;
            String nonPreviewDirName = namePrefix + "/" + pkgPath;
            List<Node> previewOnlyChildren = createChildNodes(previewLoc, 0, childLoc -> {
                String baseName = getBaseName(childLoc);
                String nonPreviewChildName = nonPreviewDirName + "/" + baseName;
                boolean isPreviewOnly = ImageLocation.isPreviewOnly(childLoc.getFlags());
                LocationType type = childLoc.getType();
                if (type == RESOURCE) {
                    // Preview resources are cached to override non-preview versions.
                    Node childNode = ensureCached(newResource(nonPreviewChildName, childLoc));
                    return isPreviewOnly ? childNode : null;
                } else {
                    // Child directories are not cached here (they are either cached
                    // already or have been added to previewDirectoriesToMerge).
                    assert type == MODULES_DIR : "Invalid location type: " + childLoc;
                    Node childNode = nodes.get(nonPreviewChildName);
                    assert isPreviewOnly == (childNode != null) :
                            "Inconsistent child node: " + nonPreviewChildName;
                    return childNode;
                }
            });
            Directory previewDir = newDirectory(nonPreviewDirName);
            previewDir.setChildren(previewOnlyChildren);
            if (ImageLocation.isPreviewOnly(previewLoc.getFlags())) {
                // If we are preview-only, our children are also preview-only, so
                // this directory is a complete hierarchy and should be cached.
                assert !previewOnlyChildren.isEmpty() : "Invalid empty preview-only directory: " + nonPreviewDirName;
                ensureCached(previewDir);
            } else if (!previewOnlyChildren.isEmpty()) {
                // A partial directory containing extra preview-only nodes
                // to be merged when the non-preview directory is completed.
                previewDirectoriesToMerge.put(nonPreviewDirName, previewDir);
            }
        }

        // Adds a node to the cache, ensuring that no matching entry already existed.
        private <T extends Node> T ensureCached(T node) {
            Node existingNode = nodes.put(node.getName(), node);
            assert existingNode == null : "Unexpected node already cached for: " + node;
            return node;
        }

        private static ImageReader open(Path imagePath, ByteOrder byteOrder, boolean previewMode) throws IOException {
            Objects.requireNonNull(imagePath);
            Objects.requireNonNull(byteOrder);

            synchronized (OPEN_FILES) {
                ReaderKey key = new ReaderKey(imagePath, previewMode);
                SharedImageReader reader = OPEN_FILES.get(key);

                if (reader == null) {
                    // Will fail with an IOException if wrong byteOrder.
                    reader = new SharedImageReader(imagePath, byteOrder, previewMode);
                    OPEN_FILES.put(key, reader);
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

                    if (!OPEN_FILES.remove(new ReaderKey(getImagePath(), previewMode), this)) {
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
            // Root directories "/", "/modules" and "/packages", as well
            // as all "/packages/xxx" subdirectories are already cached.
            Node node = nodes.get(name);
            if (node == null) {
                if (name.startsWith(MODULES_PREFIX + "/")) {
                    node = buildAndCacheModulesNode(name);
                } else if (name.startsWith(PACKAGES_PREFIX + "/")) {
                    node = buildAndCacheLinkNode(name);
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
            String nodeName = MODULES_PREFIX + "/" + moduleName + "/" + resourcePath;
            // Synchronize as tightly as possible to reduce locking contention.
            synchronized (this) {
                Node node = nodes.get(nodeName);
                if (node == null) {
                    ImageLocation loc = findLocation(moduleName, resourcePath);
                    if (loc != null && loc.getType() == RESOURCE) {
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
         * search). As such, it skips checking the nodes cache if possible, and
         * only checks for an entry in the jimage file, as this is faster if the
         * resource is not present. This also means it doesn't need
         * synchronization most of the time.
         */
        boolean containsResource(String moduleName, String resourcePath) {
            if (moduleName.indexOf('/') >= 0) {
                throw new IllegalArgumentException("invalid module name: " + moduleName);
            }
            // In preview mode, preview-only resources are eagerly added to the
            // cache, so we must check that first.
            if (previewMode) {
                String nodeName = MODULES_PREFIX + "/" + moduleName + "/" + resourcePath;
                // Synchronize as tightly as possible to reduce locking contention.
                synchronized (this) {
                    Node node = nodes.get(nodeName);
                    if (node != null) {
                        return node.isResource();
                    }
                }
            }
            ImageLocation loc = findLocation(moduleName, resourcePath);
            return loc != null && loc.getType() == RESOURCE;
        }

        /**
         * Builds a node in the "/modules/..." namespace.
         *
         * <p>Called by {@link #findNode(String)} if a {@code /modules/...} node
         * is not present in the cache.
         */
        private Node buildAndCacheModulesNode(String name) {
            assert name.startsWith(MODULES_PREFIX + "/") : "Invalid module node name: " + name;
            if (isPreviewName(name)) {
                return null;
            }
            // Returns null for non-directory resources, since the jimage name does not
            // start with "/modules" (e.g. "/java.base/java/lang/Object.class").
            ImageLocation loc = findLocation(name);
            if (loc != null) {
                assert name.equals(loc.getFullName()) : "Mismatched location for directory: " + name;
                assert loc.getType() == MODULES_DIR : "Invalid modules directory: " + name;
                return ensureCached(completeModuleDirectory(newDirectory(name), loc));
            }
            // Now try the non-prefixed resource name, but be careful to avoid false
            // positives for names like "/modules/modules/xxx" which could return a
            // location of a directory entry.
            loc = findLocation(name.substring(MODULES_PREFIX.length()));
            return loc != null && loc.getType() == RESOURCE
                    ? ensureCached(newResource(name, loc))
                    : null;
        }

        /**
         * Returns whether a directory name in the "/modules/" directory could be referencing
         * the "META-INF" directory".
         */
        private boolean isMetaInf(Directory dir) {
            String name = dir.getName();
            int pathStart = name.indexOf('/', MODULES_PREFIX.length() + 1);
            return name.length() == pathStart + "/META-INF".length()
                    && name.endsWith("/META-INF");
        }

        /**
         * Returns whether a node name in the "/modules/" directory could be referencing
         * a preview resource or directory under "META-INF/preview".
         */
        private boolean isPreviewName(String name) {
            int pathStart = name.indexOf('/', MODULES_PREFIX.length() + 1);
            int previewEnd = pathStart + PREVIEW_INFIX.length();
            return pathStart > 0
                    && name.regionMatches(pathStart, PREVIEW_INFIX, 0, PREVIEW_INFIX.length())
                    && (name.length() == previewEnd || name.charAt(previewEnd) == '/');
        }

        private String getBaseName(ImageLocation loc) {
            // Matches logic in ImageLocation#getFullName() regarding extensions.
            String trailingParts = loc.getBase()
                    + ((loc.getExtensionOffset() != 0) ? "." + loc.getExtension() : "");
            return trailingParts.substring(trailingParts.lastIndexOf('/') + 1);
        }

        /**
         * Builds a link node of the form "/packages/xxx/yyy".
         *
         * <p>Called by {@link #findNode(String)} if a {@code /packages/...}
         * node is not present in the cache (the name is not trusted).
         */
        private Node buildAndCacheLinkNode(String name) {
            // There are only locations for "/packages" or "/packages/xxx"
            // directories, but not the symbolic links below them (links are
            // derived from the name information in the parent directory).
            int packageStart = PACKAGES_PREFIX.length() + 1;
            int packageEnd = name.indexOf('/', packageStart);
            // We already built the 2-level "/packages/xxx" directories,
            // so if this is a 2-level name, it cannot reference a node.
            if (packageEnd >= 0) {
                String dirName = name.substring(0, packageEnd);
                // If no parent exists here, the name cannot be valid.
                Directory parent = (Directory) nodes.get(dirName);
                if (parent != null) {
                    if (!parent.isCompleted()) {
                        // This caches all child links of the parent directory.
                        completePackageSubdirectory(parent, findLocation(dirName));
                    }
                    return nodes.get(name);
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
            assert name.startsWith(MODULES_PREFIX) || name.startsWith(PACKAGES_PREFIX);
            ImageLocation loc = findLocation(name);
            assert loc != null && name.equals(loc.getFullName()) : "Invalid location for name: " + name;
            LocationType type = loc.getType();
            if (type == MODULES_DIR || type == MODULES_ROOT) {
                completeModuleDirectory(dir, loc);
            } else {
                assert type == PACKAGES_DIR : "Invalid location type: " + loc;
                completePackageSubdirectory(dir, loc);
            }
            assert dir.isCompleted() : "Directory must be complete by now: " + dir;
        }

        /** Completes a modules directory by setting the list of child nodes. */
        private Directory completeModuleDirectory(Directory dir, ImageLocation loc) {
            assert dir.getName().equals(loc.getFullName()) : "Mismatched location for directory: " + dir;
            List<Node> previewOnlyNodes = getPreviewNodesToMerge(dir);
            // We hide preview names from direct lookup, but must also prevent
            // the preview directory from appearing in any META-INF directories.
            boolean parentIsMetaInfDir = isMetaInf(dir);
            List<Node> children = createChildNodes(loc, previewOnlyNodes.size(), childLoc -> {
                LocationType type = childLoc.getType();
                if (type == MODULES_DIR) {
                    String name = childLoc.getFullName();
                    return parentIsMetaInfDir && name.endsWith("/preview")
                            ? null
                            : nodes.computeIfAbsent(name, this::newDirectory);
                } else {
                    assert type == RESOURCE : "Invalid location type: " + loc;
                    // Add "/modules" prefix to image location paths to get node names.
                    String resourceName = childLoc.getFullName(true);
                    return nodes.computeIfAbsent(resourceName, n -> newResource(n, childLoc));
                }
            });
            children.addAll(previewOnlyNodes);
            dir.setChildren(children);
            return dir;
        }

        /** Completes a package directory by setting the list of child nodes. */
        private void completePackageSubdirectory(Directory dir, ImageLocation loc) {
            assert dir.getName().equals(loc.getFullName()) : "Mismatched location for directory: " + dir;
            assert !dir.isCompleted() : "Directory already completed: " + dir;
            assert loc.getType() == PACKAGES_DIR : "Invalid location type: " + loc.getType();

            // In non-preview mode we might skip a very small number of preview-only
            // entries, but it's not worth "right-sizing" the array for that.
            IntBuffer offsets = getOffsetBuffer(loc);
            List<Node> children = new ArrayList<>(offsets.capacity() / 2);
            ModuleReference.readNameOffsets(offsets, /*normal*/ true, previewMode)
                    .forEachRemaining(n -> {
                        String modName = getString(n);
                        Node link = newLinkNode(dir.getName() + "/" + modName, MODULES_PREFIX + "/" + modName);
                        children.add(ensureCached(link));
                    });
            // If the parent directory exists, there must be at least one child node.
            assert !children.isEmpty() : "Invalid empty package directory: " + dir;
            dir.setChildren(children);
        }

        /**
         * Returns the list of child preview nodes to be merged into the given directory.
         *
         * <p>Because this is only called once per-directory (since the result is cached
         * indefinitely) we can remove any entries we find from the cache. If ever the
         * node cache allowed entries to expire, this would have to be changed so that
         * directories could be completed more than once.
         */
        List<Node> getPreviewNodesToMerge(Directory dir) {
            if (previewDirectoriesToMerge != null) {
                Directory mergeDir = previewDirectoriesToMerge.remove(dir.getName());
                if (mergeDir != null) {
                    return mergeDir.children;
                }
            }
            return Collections.emptyList();
        }

        /**
         * Creates the list of child nodes for a modules {@code Directory} from
         * its parent location.
         *
         * <p>The {@code getChildFn} may return existing cached nodes rather
         * than creating them, and if newly created nodes are to be cached,
         * it is the job of {@code getChildFn}, or the caller of this method,
         * to do that.
         *
         * @param loc a location relating to a "/modules" directory.
         * @param extraNodesCount a known number of preview-only child nodes
         *     which will be merged onto the end of the returned list later.
         * @param getChildFn a function to return a node for each child location
         *     (or null to skip putting anything in the list).
         * @return the list of the non-null child nodes, returned by
         *     {@code getChildFn}, in the order of the locations entries.
         */
        private List<Node> createChildNodes(ImageLocation loc, int extraNodesCount, Function<ImageLocation, Node> getChildFn) {
            LocationType type = loc.getType();
            assert type == MODULES_DIR || type == MODULES_ROOT : "Invalid location type: " + loc;
            IntBuffer offsets = getOffsetBuffer(loc);
            int childCount = offsets.capacity();
            List<Node> children = new ArrayList<>(childCount + extraNodesCount);
            for (int i = 0; i < childCount; i++) {
                Node childNode = getChildFn.apply(getLocation(offsets.get(i)));
                if (childNode != null) {
                    children.add(childNode);
                }
            }
            return children;
        }

        /** Helper to extract the integer offset buffer from a directory location. */
        private IntBuffer getOffsetBuffer(ImageLocation dir) {
            assert dir.getType() != RESOURCE : "Not a directory: " + dir.getFullName();
            byte[] offsets = getResource(dir);
            ByteBuffer buffer = ByteBuffer.wrap(offsets);
            buffer.order(getByteOrder());
            return buffer.asIntBuffer();
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

        private void setChildren(List<? extends Node> children) {
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
