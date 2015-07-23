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

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A class to build a sorted tree of Resource paths as a tree of ImageLocation.
 *
 */
// XXX Public only due to the JImageTask / JImageTask code duplication
public final class ImageResourcesTree {

    private static final String MODULES = "modules";
    private static final String PACKAGES = "packages";
    public static final String MODULES_STRING = UTF8String.MODULES_STRING.toString();
    public static final String PACKAGES_STRING = UTF8String.PACKAGES_STRING.toString();

    public static boolean isTreeInfoResource(String path) {
        return path.startsWith(PACKAGES_STRING) || path.startsWith(MODULES_STRING);
    }

    /**
     * Path item tree node.
     */
    private static final class Node {

        private final String name;
        private final Map<String, Node> children = new TreeMap<>();
        private final Node parent;
        private ImageLocationWriter loc;
        private boolean isResource;

        private Node(String name, Node parent) {
            this.name = name;
            this.parent = parent;

            if (parent != null) {
                parent.children.put(name, this);
            }
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

    /**
     * Tree of nodes.
     */
    private static final class Tree {

        private final Map<String, Node> directAccess = new HashMap<>();
        private final List<String> paths;
        private final Node root;
        private Node modules;
        private Node packages;

        private Tree(List<String> paths) {
            this.paths = paths;
            root = new Node("", null);
            buildTree();
        }

        private void buildTree() {
            modules = new Node(MODULES, root);
            directAccess.put(modules.getPath(), modules);

            Map<String, Set<String>> moduleToPackage = new TreeMap<>();
            Map<String, Set<String>> packageToModule = new TreeMap<>();

            for (String p : paths) {
                if (!p.startsWith("/")) {
                    continue;
                }
                String[] split = p.split("/");
                Node current = modules;
                String module = null;
                for (int i = 0; i < split.length; i++) {
                    String s = split[i];
                    if (!s.isEmpty()) {
                        if (module == null) {
                            module = s;
                        }
                        Node n = current.children.get(s);
                        if (n == null) {
                            n = new Node(s, current);
                            if (i == split.length - 1) { // Leaf
                                n.isResource = true;
                                String pkg = toPackageName(n.parent);
                                if (pkg != null && !pkg.startsWith("META-INF")) {
                                    Set<String> pkgs = moduleToPackage.get(module);
                                    if (pkgs == null) {
                                        pkgs = new TreeSet<>();
                                        moduleToPackage.put(module, pkgs);
                                    }
                                    pkgs.add(pkg);
                                }
                            } else { // put only sub trees, no leaf
                                directAccess.put(n.getPath(), n);
                                String pkg = toPackageName(n);
                                if (pkg != null && !pkg.startsWith("META-INF")) {
                                    Set<String> mods = packageToModule.get(pkg);
                                    if (mods == null) {
                                        mods = new TreeSet<>();
                                        packageToModule.put(pkg, mods);
                                    }
                                    mods.add(module);

                                }
                            }
                        }
                        current = n;
                    }
                }
            }
            packages = new Node(PACKAGES, root);
            directAccess.put(packages.getPath(), packages);
            for (Map.Entry<String, Set<String>> entry : moduleToPackage.entrySet()) {
                for (String pkg : entry.getValue()) {
                    Node pkgNode = new Node(pkg, packages);
                    directAccess.put(pkgNode.getPath(), pkgNode);

                    Node modNode = new Node(entry.getKey(), pkgNode);
                    directAccess.put(modNode.getPath(), modNode);
                }
            }
            for (Map.Entry<String, Set<String>> entry : packageToModule.entrySet()) {
                Node pkgNode = new Node(entry.getKey(), packages);
                directAccess.put(pkgNode.getPath(), pkgNode);
                for (String module : entry.getValue()) {
                    Node modNode = new Node(module, pkgNode);
                    directAccess.put(modNode.getPath(), modNode);
                }
            }
        }

        public String toResourceName(Node node) {
            if (!node.children.isEmpty()) {
                throw new RuntimeException("Node is not a resource");
            }
            return removeRadical(node);
        }

        public String getModule(Node node) {
            if (node.parent == null || node.getName().equals(MODULES) ||
                node.getName().startsWith(PACKAGES)) {
                return null;
            }
            String path = removeRadical(node);
            // "/xxx/...";
            path = path.substring(1);
            int i = path.indexOf("/");
            if (i == -1) {
                return path;
            } else {
                return path.substring(0, i);
            }
        }

        public String toPackageName(Node node) {
            if (node.parent == null) {
                return null;
            }
            String path = removeRadical(node.getPath(), "/" + MODULES + "/");
            String module = getModule(node);
            if (path.equals(module)) {
                return null;
            }
            String pkg = removeRadical(path, module + "/");
            return pkg.replaceAll("/", ".");
        }

        public String removeRadical(Node node) {
            String s = node.getPath();
            return removeRadical(node.getPath(), "/" + MODULES);
        }

        private String removeRadical(String path, String str) {
            return path.substring(str.length());
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
            int[] ret = new int[current.children.size()];
            int i = 0;
            for (java.util.Map.Entry<String, Node> entry : current.children.entrySet()) {
                ret[i] = addLocations(entry.getValue());
                i += 1;
            }
            if (current != tree.getRoot() && !current.isResource) {
                int size = ret.length * 4;
                writer.addLocation(current.getPath(), offset, 0, size);
                offset += size;
            }
            return 0;
        }

        private List<byte[]> computeContent() {
            // Map used to associate Tree item with locations offset.
            Map<String, ImageLocationWriter> outLocations = new HashMap<>();
            for (ImageLocationWriter wr : writer.getLocations()) {
                outLocations.put(wr.getFullNameString(), wr);
            }
            // Attach location to node
            for (Map.Entry<String, ImageLocationWriter> entry : outLocations.entrySet()) {
                Node item = tree.getMap().get(entry.getKey());
                if (item != null) {
                    item.loc = entry.getValue();
                }
            }
            computeContent(tree.getRoot(), outLocations);
            return content;
        }

        private int computeContent(Node current, Map<String, ImageLocationWriter> outLocations) {
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
                if (current.isResource) {
                    // A resource location, remove "/modules"
                    String s = tree.toResourceName(current);
                    current.loc = outLocations.get(s);
                } else {
                    // "/packages" leaf node, empty "/packages" or empty "/modules" paths
                    current.loc = outLocations.get(current.getPath());
                }
            }
            return current == tree.getRoot() ? 0 : current.loc.getLocationOffset();
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
