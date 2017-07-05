/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.nio.zipfs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.Formatter;

/*
 *
 * @author  Xueming Shen, Rajendra Gutupalli, Jaya Hangal
 */

public class ZipFileStore extends FileStore {

    private final ZipFileSystem zfs;

    ZipFileStore(ZipPath zpath) {
        this.zfs = zpath.getFileSystem();
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
        return (V)null;
    }

    @Override
    public long getTotalSpace() throws IOException {
         return new ZipFileStoreAttributes(this).totalSpace();
    }

    @Override
    public long getUsableSpace() throws IOException {
         return new ZipFileStoreAttributes(this).usableSpace();
    }

    @Override
    public long getUnallocatedSpace() throws IOException {
         return new ZipFileStoreAttributes(this).unallocatedSpace();
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
         if (attribute.equals("totalSpace"))
               return getTotalSpace();
         if (attribute.equals("usableSpace"))
               return getUsableSpace();
         if (attribute.equals("unallocatedSpace"))
               return getUnallocatedSpace();
         throw new UnsupportedOperationException("does not support the given attribute");
    }

    private static class ZipFileStoreAttributes {
        final FileStore fstore;
        final long size;

        public ZipFileStoreAttributes(ZipFileStore fileStore)
            throws IOException
        {
            Path path = FileSystems.getDefault().getPath(fileStore.name());
            this.size = Files.size(path);
            this.fstore = Files.getFileStore(path);
        }

        public long totalSpace() {
            return size;
        }

        public long usableSpace() throws IOException {
            if (!fstore.isReadOnly())
                return fstore.getUsableSpace();
            return 0;
        }

        public long unallocatedSpace()  throws IOException {
            if (!fstore.isReadOnly())
                return fstore.getUnallocatedSpace();
            return 0;
        }
    }
}
