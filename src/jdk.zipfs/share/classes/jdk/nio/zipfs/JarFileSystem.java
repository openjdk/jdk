/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
    private Function<byte[],byte[]> lookup;

    @Override
    IndexNode getInode(byte[] path) {
        // check for an alias to a versioned entry
        byte[] versionedPath = lookup.apply(path);
        return versionedPath == null ? super.getInode(path) : super.getInode(versionedPath);
    }

    JarFileSystem(ZipFileSystemProvider provider, Path zfpath, Map<String,?> env)
            throws IOException {
        super(provider, zfpath, env);
        lookup = path -> path;  // lookup needs to be set before isMultiReleaseJar is called
                                // because it eventually calls getEntry
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
            lookup = createVersionedLinks(version < 0 ? 0 : version);
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
    private Function<byte[],byte[]> createVersionedLinks(int version) {
        HashMap<IndexNode,byte[]> aliasMap = new HashMap<>();
        IndexNode verdir = getInode(getBytes("/META-INF/versions"));
        if (verdir != null) {
            getVersionMap(version, verdir).values()
                .forEach(versionNode -> {   // for each META-INF/versions/{n} directory
                    // put all the leaf inodes, i.e. entries, into the alias map
                    // possibly shadowing lower versioned entries
                    walk(versionNode, entryNode -> {
                        byte[] rootName = getRootName(versionNode, entryNode);
                        if (rootName != null) {
                            IndexNode rootNode = getInode(rootName);
                            if (rootNode == null) { // no matching root node, make a virtual one
                                rootNode = IndexNode.keyOf(rootName);
                            }
                            aliasMap.put(rootNode, entryNode.name);
                        }
                    });
                });
        }
        return path -> aliasMap.get(IndexNode.keyOf(path));
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
            Integer key = getVersion(child.name, metaInfVersions.name.length + 1);
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
    private Integer getVersion(byte[] name, int offset) {
        try {
            return Integer.parseInt(getString(Arrays.copyOfRange(name, offset, name.length)));
        } catch (NumberFormatException x) {
            // ignore this even though it might indicate issues with the JAR structure
            return null;
        }
    }

    /**
     * walk the IndexNode tree processing all leaf nodes
     */
    private void walk(IndexNode inode, Consumer<IndexNode> process) {
        if (inode == null) return;
        if (inode.isDir()) {
            walk(inode.child, process);
        } else {
            process.accept(inode);
        }
        walk(inode.sibling, process);
    }

    /**
     * extract the root name from a versioned entry name
     *   given inode for META-INF/versions/9/foo/bar.class
     *   and prefix META-INF/versions/9/
     *   returns foo/bar.class
     */
    private byte[] getRootName(IndexNode prefix, IndexNode inode) {
        int offset = prefix.name.length;
        byte[] fullName = inode.name;
        return Arrays.copyOfRange(fullName, offset, fullName.length);
    }
}
