/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal;

import jdk.internal.jimage.ImageLocation;
import jdk.internal.jimage.ModuleReference;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A class to build a sorted tree of Resource paths as a tree of ImageLocation.
 */
// XXX Public only due to the JImageTask / JImageTask code duplication
public final class ImageResourcesTree {
    /**
     * Path item tree node.
     */
    // Visible for testing only.
    static class Node {

        private final String name;
        private final Map<String, Node> children = new TreeMap<>();
        private final Node parent;
        private ImageLocationWriter loc;

        private Node(String name, Node parent) {
            this.name = name;
            this.parent = parent;

            if (parent != null) {
                parent.children.put(name, this);
            }
        }

        private void setLocation(ImageLocationWriter loc) {
            // This *can* be called more than once, but only with the same instance.
            if (this.loc != null && loc != this.loc) {
                throw new IllegalStateException("Cannot add different locations: " + name);
            }
            this.loc = Objects.requireNonNull(loc);
        }

        public String getPath() {
            if (parent == null) {
                return "/";
            }
            return buildPath(this);
        }

        public String getName() {
            return name;
        }

        public Node getChildren(String name) {
            Node item = children.get(name);
            return item;
        }

        private static String buildPath(Node item) {
            if (item == null) {
                return null;
            }
            String path = buildPath(item.parent);
            if (path == null) {
                return item.getName();
            } else {
                return path + "/" + item.getName();
            }
        }
    }

    // Visible for testing only.
    static final class ResourceNode extends Node {

        public ResourceNode(String name, Node parent) {
            super(name, parent);
        }
    }

    /**
     * A 2nd level package directory, {@code "/packages/<package-name>"}.
     *
     * <p>While package paths can exist within many modules, for each package
     * there is at most one module in which that package has resources.
     *
     * <p>For example, the package path {@code java/util} exists in both the
     * {@code java.base} and {@code java.logging} modules. This means both
     * {@code "/packages/java.util/java.base"} and
     * {@code "/packages/java.util/java.logging"} will exist, but only
     * {@code "java.base"} entry will be marked as having content.
     *
     * <p>When processing module references in non-preview mode, entries marked
     * as {@link ModuleReference#isPreviewOnly() preview-only} must be ignored.
     *
     * <p>If all references in a package are preview-only, then the entire
     * package is marked as preview-only, and must be ignored.
     */
    // Visible for testing only.
    static final class PackageNode extends Node {
        private final List<ModuleReference> moduleReferences;

        PackageNode(String name, List<ModuleReference> moduleReferences, Node parent) {
            super(name, parent);
            if (moduleReferences.isEmpty()) {
                throw new IllegalStateException("Package must be associated with modules: " + name);
            }
            if (moduleReferences.stream().filter(ModuleReference::hasResources).count() > 1) {
                throw new IllegalStateException("Multiple modules contain non-empty package: " + name);
            }
            this.moduleReferences = Collections.unmodifiableList(moduleReferences);
        }

        List<ModuleReference> getModuleReferences() {
            return moduleReferences;
        }
    }

    // Not serialized, and never stored in any field of any class that is.
    @SuppressWarnings("serial")
    private static final class InvalidTreeException extends Exception {
        public InvalidTreeException(Node badNode) {
            super("Resources tree, invalid data structure, skipping: " + badNode.getPath());
        }
        // Exception only used for program flow, not debugging.
        @Override
        public Throwable fillInStackTrace() {return this;}
    }

    /**
     * Tree of nodes.
     */
    // Visible for testing only.
    static final class Tree {
        private static final String PREVIEW_PREFIX = "META-INF/preview/";

        private final Map<String, Node> directAccess = new HashMap<>();
        private final List<String> paths;
        private final Node root;
        private Node packagesRoot;

        // Visible for testing only.
        Tree(List<String> paths) {
            this.paths = paths.stream().sorted(Comparator.reverseOrder()).toList();
            // Root node is not added to the directAccess map.
            root = new Node("", null);
            buildTree();
        }

        private void buildTree() {
            Node modulesRoot = new Node("modules", root);
            directAccess.put(modulesRoot.getPath(), modulesRoot);
            packagesRoot = new Node("packages", root);
            directAccess.put(packagesRoot.getPath(), packagesRoot);

            // Map of dot-separated package names to module references (those
            // in which the package appear). References are merged after to
            // ensure each module name appears only once, but temporarily a
            // module may have several entries per package (e.g. with-content,
            // without-content, normal, preview-only etc..).
            Map<String, Set<ModuleReference>> packageToModules = new TreeMap<>();
            for (String fullPath : paths) {
                try {
                    processPath(fullPath, modulesRoot, packageToModules);
                } catch (InvalidTreeException ex) {
                    // It has been observed some badly created jar file to contain
                    // invalid directory entry marked as not directory (see 8131762).
                    System.err.println(ex.getMessage());
                }
            }

            // We've collected information for all "packages", including the root
            // (empty) package and anything under "META-INF". However, these should
            // not have entries in the "/packages" directory.
            packageToModules.keySet().removeIf(p -> p.isEmpty() || p.equals("META-INF") || p.startsWith("META-INF."));
            packageToModules.forEach((pkgName, modRefs) -> {
                // Merge multiple refs for the same module.
                List<ModuleReference> pkgModules = modRefs.stream()
                        .collect(Collectors.groupingBy(ModuleReference::name))
                        .values().stream()
                        .map(refs -> refs.stream().reduce(ModuleReference::merge).orElseThrow())
                        .sorted()
                        .toList();
                PackageNode pkgNode = new PackageNode(pkgName, pkgModules, packagesRoot);
                directAccess.put(pkgNode.getPath(), pkgNode);
            });
        }

        private void processPath(
                String fullPath,
                Node modulesRoot,
                Map<String, Set<ModuleReference>> packageToModules)
                throws InvalidTreeException {
            // Paths are untrusted, so be careful about checking expected format.
            if (!fullPath.startsWith("/") || fullPath.endsWith("/") || fullPath.contains("//")) {
                return;
            }
            int modEnd = fullPath.indexOf('/', 1);
            // Ensure non-empty module name with non-empty suffix.
            if (modEnd <= 1) {
                return;
            }
            String modName = fullPath.substring(1, modEnd);
            String pkgPath = fullPath.substring(modEnd + 1);

            Node parentNode = getDirectoryNode(modName, modulesRoot);
            boolean isPreviewPath = false;
            if (pkgPath.startsWith(PREVIEW_PREFIX)) {
                // For preview paths, process nodes relative to the preview directory.
                pkgPath = pkgPath.substring(PREVIEW_PREFIX.length());
                Node metaInf = getDirectoryNode("META-INF", parentNode);
                parentNode = getDirectoryNode("preview", metaInf);
                isPreviewPath = true;
            }

            int pathEnd = pkgPath.lastIndexOf('/');
            // From invariants tested above, this must now be well-formed.
            String fullPkgName = (pathEnd == -1) ? "" : pkgPath.substring(0, pathEnd).replace('/', '.');
            String resourceName = pkgPath.substring(pathEnd + 1);
            // Intermediate packages are marked "empty" (no resources). This might
            // later be merged with a non-empty reference for the same package.
            ModuleReference emptyRef = ModuleReference.forEmptyPackage(modName, isPreviewPath);

            // Work down through empty packages to final resource.
            for (int i = pkgEndIndex(fullPkgName, 0); i != -1; i = pkgEndIndex(fullPkgName, i)) {
                // Due to invariants already checked, pkgName is non-empty.
                String pkgName = fullPkgName.substring(0, i);
                packageToModules.computeIfAbsent(pkgName, p -> new HashSet<>()).add(emptyRef);
                String childNodeName = pkgName.substring(pkgName.lastIndexOf('.') + 1);
                parentNode = getDirectoryNode(childNodeName, parentNode);
            }
            // Reached non-empty (leaf) package (could still be a duplicate).
            Node resourceNode = parentNode.getChildren(resourceName);
            if (resourceNode == null) {
                ModuleReference resourceRef = ModuleReference.forPackage(modName, isPreviewPath);
                packageToModules.computeIfAbsent(fullPkgName, p -> new HashSet<>()).add(resourceRef);
                // Init adds new node to parent (don't add resources to directAccess).
                new ResourceNode(resourceName, parentNode);
            } else if (!(resourceNode instanceof ResourceNode)) {
                throw new InvalidTreeException(resourceNode);
            }
        }

        private Node getDirectoryNode(String name, Node parent) throws InvalidTreeException {
            Node child = parent.getChildren(name);
            if (child == null) {
                // Adds child to parent during init.
                child = new Node(name, parent);
                directAccess.put(child.getPath(), child);
            } else if (child instanceof ResourceNode) {
                throw new InvalidTreeException(child);
            }
            return child;
        }

        // Helper to iterate package names up to, and including, the complete name.
        private int pkgEndIndex(String s, int i) {
            if (i >= 0 && i < s.length()) {
                i = s.indexOf('.', i + 1);
                return i != -1 ? i : s.length();
            }
            return -1;
        }

        private String toResourceName(Node node) {
            if (!node.children.isEmpty()) {
                throw new RuntimeException("Node is not a resource");
            }
            return removeRadical(node);
        }

        private String removeRadical(Node node) {
            return removeRadical(node.getPath(), "/modules");
        }

        private String removeRadical(String path, String str) {
            if (!(path.length() < str.length())) {
                path = path.substring(str.length());
            }
            return path;
        }

        public Node getRoot() {
            return root;
        }

        public Map<String, Node> getMap() {
            return directAccess;
        }
    }

    private static final class LocationsAdder {

        private long offset;
        private final List<byte[]> content = new ArrayList<>();
        private final BasicImageWriter writer;
        private final Tree tree;

        LocationsAdder(Tree tree, long offset, BasicImageWriter writer) {
            this.tree = tree;
            this.offset = offset;
            this.writer = writer;
            addLocations(tree.getRoot());
        }

        private int addLocations(Node current) {
            if (current instanceof PackageNode) {
                List<ModuleReference> refs = ((PackageNode) current).getModuleReferences();
                // "/packages/<pkg name>" entries have 8-byte entries (flags+offset).
                int size = refs.size() * 8;
                writer.addLocation(current.getPath(), offset, 0, size, ImageLocation.getPackageFlags(refs));
                offset += size;
            } else {
                int[] ret = new int[current.children.size()];
                int i = 0;
                for (java.util.Map.Entry<String, Node> entry : current.children.entrySet()) {
                    ret[i] = addLocations(entry.getValue());
                    i += 1;
                }
                if (current != tree.getRoot() && !(current instanceof ResourceNode)) {
                    int locFlags = ImageLocation.getFlags(current.getPath(), tree.directAccess::containsKey);
                    // Normal directory entries have 4-byte entries (offset only).
                    int size = ret.length * 4;
                    writer.addLocation(current.getPath(), offset, 0, size, locFlags);
                    offset += size;
                }
            }
            return 0;
        }

        private List<byte[]> computeContent() {
            // Map used to associate Tree item with locations offset.
            Map<String, ImageLocationWriter> outLocations = new HashMap<>();
            for (ImageLocationWriter wr : writer.getLocations()) {
                outLocations.put(wr.getFullName(), wr);
            }
            // Attach location to node
            for (Map.Entry<String, ImageLocationWriter> entry : outLocations.entrySet()) {
                Node item = tree.getMap().get(entry.getKey());
                if (item != null) {
                    item.setLocation(entry.getValue());
                }
            }
            computeContent(tree.getRoot(), outLocations);
            return content;
        }

        private int computeContent(Node current, Map<String, ImageLocationWriter> outLocations) {
            if (current instanceof PackageNode) {
                // "/packages/<pkg name>" entries have 8-byte entries (flags+offset).
                List<ModuleReference> refs = ((PackageNode) current).getModuleReferences();
                ByteBuffer byteBuffer = ByteBuffer.allocate(8 * refs.size());
                byteBuffer.order(writer.getByteOrder());
                ModuleReference.write(refs, byteBuffer.asIntBuffer(), writer::addString);
                content.add(byteBuffer.array());
                current.setLocation(outLocations.get(current.getPath()));
            } else {
                int[] ret = new int[current.children.size()];
                int i = 0;
                for (java.util.Map.Entry<String, Node> entry : current.children.entrySet()) {
                    ret[i] = computeContent(entry.getValue(), outLocations);
                    i += 1;
                }
                if (ret.length > 0) {
                    int size = ret.length * 4;
                    ByteBuffer buff = ByteBuffer.allocate(size);
                    buff.order(writer.getByteOrder());
                    for (int val : ret) {
                        buff.putInt(val);
                    }
                    byte[] arr = buff.array();
                    content.add(arr);
                } else {
                    if (current instanceof ResourceNode) {
                        // A resource location, remove "/modules"
                        String s = tree.toResourceName(current);
                        current.setLocation(outLocations.get(s));
                    } else {
                        // empty "/packages" or empty "/modules" paths
                        current.setLocation(outLocations.get(current.getPath()));
                    }
                }
                if (current.loc == null && current != tree.getRoot()) {
                    System.err.println("Invalid path in metadata, skipping " + current.getPath());
                }
            }
            return current.loc == null ? 0 : current.loc.getLocationOffset();
        }
    }

    private final List<String> paths;
    private final LocationsAdder adder;

    public ImageResourcesTree(long offset, BasicImageWriter writer, List<String> paths) {
        this.paths = new ArrayList<>();
        this.paths.addAll(paths);
        Collections.sort(this.paths);
        Tree tree = new Tree(this.paths);
        adder = new LocationsAdder(tree, offset, writer);
    }

    public void addContent(DataOutputStream out) throws IOException {
        List<byte[]> content = adder.computeContent();
        for (byte[] c : content) {
            out.write(c, 0, c.length);
        }
    }
}
