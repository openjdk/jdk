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
package jdk.internal.jrtfs;

import java.nio.file.LinkOption;
import java.nio.file.attribute.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * File attribute view for jrt file system.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
final class JrtFileAttributeView implements BasicFileAttributeView {

    private static enum AttrID {

        size,
        creationTime,
        lastAccessTime,
        lastModifiedTime,
        isDirectory,
        isRegularFile,
        isSymbolicLink,
        isOther,
        fileKey,
        compressedSize,
        extension
    };

    private final AbstractJrtPath path;
    private final boolean isJrtView;
    private final LinkOption[] options;

    private JrtFileAttributeView(AbstractJrtPath path, boolean isJrtView, LinkOption... options) {
        this.path = path;
        this.isJrtView = isJrtView;
        this.options = options;
    }

    @SuppressWarnings("unchecked") // Cast to V
    static <V extends FileAttributeView> V get(AbstractJrtPath path, Class<V> type, LinkOption... options) {
        if (type == null) {
            throw new NullPointerException();
        }
        if (type == BasicFileAttributeView.class) {
            return (V) new JrtFileAttributeView(path, false, options);
        }
        if (type == JrtFileAttributeView.class) {
            return (V) new JrtFileAttributeView(path, true, options);
        }
        return null;
    }

    static JrtFileAttributeView get(AbstractJrtPath path, String type, LinkOption... options) {
        if (type == null) {
            throw new NullPointerException();
        }
        if (type.equals("basic")) {
            return new JrtFileAttributeView(path, false, options);
        }
        if (type.equals("jrt")) {
            return new JrtFileAttributeView(path, true, options);
        }
        return null;
    }

    @Override
    public String name() {
        return isJrtView ? "jrt" : "basic";
    }

    @Override
    public AbstractJrtFileAttributes readAttributes() throws IOException {
        return path.getAttributes(options);
    }

    @Override
    public void setTimes(FileTime lastModifiedTime,
            FileTime lastAccessTime,
            FileTime createTime)
            throws IOException {
        path.setTimes(lastModifiedTime, lastAccessTime, createTime);
    }

    void setAttribute(String attribute, Object value)
            throws IOException {
        try {
            if (AttrID.valueOf(attribute) == AttrID.lastModifiedTime) {
                setTimes((FileTime) value, null, null);
            }
            if (AttrID.valueOf(attribute) == AttrID.lastAccessTime) {
                setTimes(null, (FileTime) value, null);
            }
            if (AttrID.valueOf(attribute) == AttrID.creationTime) {
                setTimes(null, null, (FileTime) value);
            }
            return;
        } catch (IllegalArgumentException x) {
        }
        throw new UnsupportedOperationException("'" + attribute
                + "' is unknown or read-only attribute");
    }

    Map<String, Object> readAttributes(String attributes)
            throws IOException {
        AbstractJrtFileAttributes jrtfas = readAttributes();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        if ("*".equals(attributes)) {
            for (AttrID id : AttrID.values()) {
                try {
                    map.put(id.name(), attribute(id, jrtfas));
                } catch (IllegalArgumentException x) {
                }
            }
        } else {
            String[] as = attributes.split(",");
            for (String a : as) {
                try {
                    map.put(a, attribute(AttrID.valueOf(a), jrtfas));
                } catch (IllegalArgumentException x) {
                }
            }
        }
        return map;
    }

    Object attribute(AttrID id, AbstractJrtFileAttributes jrtfas) {
        switch (id) {
            case size:
                return jrtfas.size();
            case creationTime:
                return jrtfas.creationTime();
            case lastAccessTime:
                return jrtfas.lastAccessTime();
            case lastModifiedTime:
                return jrtfas.lastModifiedTime();
            case isDirectory:
                return jrtfas.isDirectory();
            case isRegularFile:
                return jrtfas.isRegularFile();
            case isSymbolicLink:
                return jrtfas.isSymbolicLink();
            case isOther:
                return jrtfas.isOther();
            case fileKey:
                return jrtfas.fileKey();
            case compressedSize:
                if (isJrtView) {
                    return jrtfas.compressedSize();
                }
                break;
            case extension:
                if (isJrtView) {
                    return jrtfas.extension();
                }
                break;
        }
        return null;
    }
}
