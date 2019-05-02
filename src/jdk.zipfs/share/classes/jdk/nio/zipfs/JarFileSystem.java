/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nio.zipfs;

import java.io.IOException;
import java.io.InputStream;
import java.lang.Runtime.Version;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Adds aliasing to ZipFileSystem to support multi-release jar files.  An alias map
 * is created by {@link JarFileSystem#createVersionedLinks(int)}.  The map is then
 * consulted when an entry is looked up in {@link JarFileSystem#getEntry(byte[])}
 * to determine if the entry has a corresponding versioned entry.  If so, the
 * versioned entry is returned.
 *
 * @author Steve Drach
 */
class JarFileSystem extends ZipFileSystem {
    // lookup needs to be initialized because isMultiReleaseJar is called before createVersionedLinks
    private Function<byte[], byte[]> lookup = path -> path;

    @Override
    IndexNode getInode(byte[] path) {
        // check for an alias to a versioned entry
        return super.getInode(lookup.apply(path));
    }

    JarFileSystem(ZipFileSystemProvider provider, Path zfpath, Map<String,?> env) throws IOException {
        super(provider, zfpath, env);
        if (isMultiReleaseJar()) {
            int version;
            Object o = env.get("multi-release");
            if (o instanceof String) {
                String s = (String)o;
                if (s.equals("runtime")) {
                    version = Runtime.version().feature();
                } else {
                    version = Integer.parseInt(s);
                }
            } else if (o instanceof Integer) {
                version = (Integer)o;
            } else if (o instanceof Version) {
                version = ((Version)o).feature();
            } else {
                throw new IllegalArgumentException("env parameter must be String, Integer, "
                        + "or Version");
            }
            createVersionedLinks(version < 0 ? 0 : version);
            setReadOnly();
        }
    }

    private boolean isMultiReleaseJar() throws IOException {
        try (InputStream is = newInputStream(getBytes("/META-INF/MANIFEST.MF"))) {
            String multiRelease = new Manifest(is).getMainAttributes()
                .getValue(Attributes.Name.MULTI_RELEASE);
            return "true".equalsIgnoreCase(multiRelease);
        } catch (NoSuchFileException x) {
            return false;
        }
    }

    /**
     * create a map of aliases for versioned entries, for example:
     *   version/PackagePrivate.class -> META-INF/versions/9/version/PackagePrivate.class
     *   version/PackagePrivate.java -> META-INF/versions/9/version/PackagePrivate.java
     *   version/Version.class -> META-INF/versions/10/version/Version.class
     *   version/Version.java -> META-INF/versions/10/version/Version.java
     *
     * then wrap the map in a function that getEntry can use to override root
     * entry lookup for entries that have corresponding versioned entries
     */
    private void createVersionedLinks(int version) {
        IndexNode verdir = getInode(getBytes("/META-INF/versions"));
        // nothing to do, if no /META-INF/versions
        if (verdir == null) {
            return;
        }
        // otherwise, create a map and for each META-INF/versions/{n} directory
        // put all the leaf inodes, i.e. entries, into the alias map
        // possibly shadowing lower versioned entries
        HashMap<IndexNode, byte[]> aliasMap = new HashMap<>();
        getVersionMap(version, verdir).values().forEach(versionNode ->
            walk(versionNode.child, entryNode ->
                aliasMap.put(
                    getNodeInRootTree(getRootName(entryNode, versionNode), entryNode.isdir),
                    entryNode.name))
        );
        lookup = path -> {
            byte[] entry = aliasMap.get(IndexNode.keyOf(path));
            return entry == null ? path : entry;
        };
    }

    /**
     * Return the node from the root tree. Create it, if it doesn't exist.
     */
    private IndexNode getNodeInRootTree(byte[] path, boolean isdir) {
        IndexNode node = getInode(path);
        if (node != null) {
            return node;
        }
        IndexNode parent = getParentDir(path);
        beginWrite();
        try {
            node = new IndexNode(path, isdir);
            node.sibling = parent.child;
            parent.child = node;
            inodes.put(node, node);
            return node;
        } finally {
            endWrite();
        }
    }

    /**
     * Return the parent directory node of a path. If the node doesn't exist,
     * it will be created. Parent directories will be created recursively.
     * Recursion fuse: We assume at latest the root path can be resolved to a node.
     */
    private IndexNode getParentDir(byte[] path) {
        byte[] parentPath = getParent(path);
        IndexNode node = inodes.get(IndexNode.keyOf(parentPath));
        if (node != null) {
            return node;
        }
        IndexNode parent = getParentDir(parentPath);
        beginWrite();
        try {
            node = new IndexNode(parentPath, true);
            node.sibling = parent.child;
            parent.child = node;
            inodes.put(node, node);
            return node;
        } finally {
            endWrite();
        }
    }

    /**
     * create a sorted version map of version -> inode, for inodes <= max version
     *   9 -> META-INF/versions/9
     *  10 -> META-INF/versions/10
     */
    private TreeMap<Integer, IndexNode> getVersionMap(int version, IndexNode metaInfVersions) {
        TreeMap<Integer,IndexNode> map = new TreeMap<>();
        IndexNode child = metaInfVersions.child;
        while (child != null) {
            Integer key = getVersion(child, metaInfVersions);
            if (key != null && key <= version) {
                map.put(key, child);
            }
            child = child.sibling;
        }
        return map;
    }

    /**
     * extract the integer version number -- META-INF/versions/9 returns 9
     */
    private Integer getVersion(IndexNode inode, IndexNode metaInfVersions) {
        try {
            byte[] fullName = inode.name;
            return Integer.parseInt(getString(Arrays
                .copyOfRange(fullName, metaInfVersions.name.length + 1, fullName.length)));
        } catch (NumberFormatException x) {
            // ignore this even though it might indicate issues with the JAR structure
            return null;
        }
    }

    /**
     * walk the IndexNode tree processing all leaf nodes
     */
    private void walk(IndexNode inode, Consumer<IndexNode> consumer) {
        if (inode == null) return;
        if (inode.isDir()) {
            walk(inode.child, consumer);
        } else {
            consumer.accept(inode);
        }
        walk(inode.sibling, consumer);
    }

    /**
     * extract the root name from a versioned entry name
     *   given inode for META-INF/versions/9/foo/bar.class
     *   and prefix META-INF/versions/9/
     *   returns foo/bar.class
     */
    private byte[] getRootName(IndexNode inode, IndexNode prefix) {
        byte[] fullName = inode.name;
        return Arrays.copyOfRange(fullName, prefix.name.length, fullName.length);
    }
}
