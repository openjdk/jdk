/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.nio.zipfs;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.FileStoreSpaceAttributeView;
import java.nio.file.attribute.FileStoreSpaceAttributes;
import java.nio.file.attribute.Attributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.Formatter;

/*
 *
 * @author  Xueming Shen, Rajendra Gutupalli, Jaya Hangal
 */

public class ZipFileStore extends FileStore {

    private final ZipFileSystem zfs;

    ZipFileStore(ZipPath zpath) {
        this.zfs = (ZipFileSystem)zpath.getFileSystem();
    }

    @Override
    public String name() {
        return zfs.toString() + "/";
    }

    @Override
    public String type() {
        return "zipfs";
    }

    @Override
    public boolean isReadOnly() {
        return zfs.isReadOnly();
    }

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return (type == BasicFileAttributeView.class ||
                type == ZipFileAttributeView.class);
    }

    @Override
    public boolean supportsFileAttributeView(String name) {
        return name.equals("basic") || name.equals("zip");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        if (type == null)
            throw new NullPointerException();
        if (type == FileStoreSpaceAttributeView.class)
            return (V) new ZipFileStoreAttributeView(this);
        return null;
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
         if (attribute.equals("space:totalSpace"))
               return new ZipFileStoreAttributeView(this).readAttributes().totalSpace();
         if (attribute.equals("space:usableSpace"))
               return new ZipFileStoreAttributeView(this).readAttributes().usableSpace();
         if (attribute.equals("space:unallocatedSpace"))
               return new ZipFileStoreAttributeView(this).readAttributes().unallocatedSpace();
         throw new UnsupportedOperationException("does not support the given attribute");
    }

    private static class ZipFileStoreAttributeView implements FileStoreSpaceAttributeView {

        private final ZipFileStore fileStore;

        public ZipFileStoreAttributeView(ZipFileStore fileStore) {
            this.fileStore = fileStore;
        }

        @Override
        public String name() {
            return "space";
        }

        @Override
        public FileStoreSpaceAttributes readAttributes() throws IOException {
            final String file = fileStore.name();
            Path path = FileSystems.getDefault().getPath(file);
            final long size = Attributes.readBasicFileAttributes(path).size();
            final FileStore fstore = path.getFileStore();
            final FileStoreSpaceAttributes fstoreAttrs =
                Attributes.readFileStoreSpaceAttributes(fstore);
            return new FileStoreSpaceAttributes() {
                public long totalSpace() {
                    return size;
                }

                public long usableSpace() {
                    if (!fstore.isReadOnly())
                        return fstoreAttrs.usableSpace();
                    return 0;
                }

                public long unallocatedSpace() {
                    if (!fstore.isReadOnly())
                        return fstoreAttrs.unallocatedSpace();
                    return 0;
                }

                public String toString() {
                    StringBuilder sb = new StringBuilder();
                    Formatter fm = new Formatter(sb);
                    fm.format("FileStoreSpaceAttributes[%s]%n", file);
                    fm.format("      totalSpace: %d%n", totalSpace());
                    fm.format("     usableSpace: %d%n", usableSpace());
                    fm.format("    unallocSpace: %d%n", unallocatedSpace());
                    fm.close();
                    return sb.toString();
                }
            };
        }
    }
}
