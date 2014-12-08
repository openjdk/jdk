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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;

final class JrtFileStore extends FileStore {

    private final JrtFileSystem jrtfs;

    JrtFileStore(JrtPath jrtPath) {
        this.jrtfs = jrtPath.getFileSystem();
    }

    @Override
    public String name() {
        return jrtfs.toString() + "/";
    }

    @Override
    public String type() {
        return "jrtfs";
    }

    @Override
    public boolean isReadOnly() {
        return jrtfs.isReadOnly();
    }

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return (type == BasicFileAttributeView.class ||
                type == JrtFileAttributeView.class);
    }

    @Override
    public boolean supportsFileAttributeView(String name) {
        return name.equals("basic") || name.equals("jrt");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        if (type == null)
            throw new NullPointerException();
        return (V)null;
    }

    @Override
    public long getTotalSpace() throws IOException {
         throw new UnsupportedOperationException("getTotalSpace");
    }

    @Override
    public long getUsableSpace() throws IOException {
         throw new UnsupportedOperationException("getUsableSpace");
    }

    @Override
    public long getUnallocatedSpace() throws IOException {
         throw new UnsupportedOperationException("getUnallocatedSpace");
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
         throw new UnsupportedOperationException("does not support " + attribute);
    }
}
