/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

import javax.tools.JavaFileObject;

import com.sun.tools.javac.file.JavacFileManager.Archive;
import com.sun.tools.javac.file.RelativePath.RelativeDirectory;
import com.sun.tools.javac.file.RelativePath.RelativeFile;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.List;

/**
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class ZipFileIndexArchive implements Archive {

    private final ZipFileIndex zfIndex;
    private final JavacFileManager fileManager;

    public ZipFileIndexArchive(JavacFileManager fileManager, ZipFileIndex zdir) throws IOException {
        super();
        this.fileManager = fileManager;
        this.zfIndex = zdir;
    }

    public boolean contains(RelativePath name) {
        return zfIndex.contains(name);
    }

    public List<String> getFiles(RelativeDirectory subdirectory) {
        return zfIndex.getFiles(subdirectory);
    }

    public JavaFileObject getFileObject(RelativeDirectory subdirectory, String file) {
        RelativeFile fullZipFileName = new RelativeFile(subdirectory, file);
        ZipFileIndex.Entry entry = zfIndex.getZipIndexEntry(fullZipFileName);
        JavaFileObject ret = new ZipFileIndexFileObject(fileManager, zfIndex, entry, zfIndex.getZipFile());
        return ret;
    }

    public Set<RelativeDirectory> getSubdirectories() {
        return zfIndex.getAllDirectories();
    }

    public void close() throws IOException {
        zfIndex.close();
    }

    @Override
    public String toString() {
        return "ZipFileIndexArchive[" + zfIndex + "]";
    }

    /**
     * A subclass of JavaFileObject representing zip entries using the com.sun.tools.javac.file.ZipFileIndex implementation.
     */
    public static class ZipFileIndexFileObject extends BaseFileObject {

        /** The entry's name.
         */
        private String name;

        /** The zipfile containing the entry.
         */
        ZipFileIndex zfIndex;

        /** The underlying zip entry object.
         */
        ZipFileIndex.Entry entry;

        /** The name of the zip file where this entry resides.
         */
        Path zipName;


        ZipFileIndexFileObject(JavacFileManager fileManager, ZipFileIndex zfIndex, ZipFileIndex.Entry entry, Path zipFileName) {
            super(fileManager);
            this.name = entry.getFileName();
            this.zfIndex = zfIndex;
            this.entry = entry;
            this.zipName = zipFileName;
        }

        @Override @DefinedBy(Api.COMPILER)
        public URI toUri() {
            return createJarUri(zipName, getPrefixedEntryName());
        }

        @Override @DefinedBy(Api.COMPILER)
        public String getName() {
            return zipName + "(" + getPrefixedEntryName() + ")";
        }

        @Override
        public String getShortName() {
            return zipName.getFileName() + "(" + entry.getName() + ")";
        }

        @Override @DefinedBy(Api.COMPILER)
        public JavaFileObject.Kind getKind() {
            return getKind(entry.getName());
        }

        @Override @DefinedBy(Api.COMPILER)
        public InputStream openInputStream() throws IOException {
            Assert.checkNonNull(entry); // see constructor
            return new ByteArrayInputStream(zfIndex.read(entry));
        }

        @Override @DefinedBy(Api.COMPILER)
        public OutputStream openOutputStream() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override @DefinedBy(Api.COMPILER)
        public CharBuffer getCharContent(boolean ignoreEncodingErrors) throws IOException {
            CharBuffer cb = fileManager.getCachedContent(this);
            if (cb == null) {
                try (InputStream in = new ByteArrayInputStream(zfIndex.read(entry))) {
                    ByteBuffer bb = fileManager.makeByteBuffer(in);
                    JavaFileObject prev = fileManager.log.useSource(this);
                    try {
                        cb = fileManager.decode(bb, ignoreEncodingErrors);
                    } finally {
                        fileManager.log.useSource(prev);
                    }
                    fileManager.recycleByteBuffer(bb); // save for next time
                    if (!ignoreEncodingErrors)
                        fileManager.cache(this, cb);
                }
            }
            return cb;
        }

        @Override @DefinedBy(Api.COMPILER)
        public Writer openWriter() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override @DefinedBy(Api.COMPILER)
        public long getLastModified() {
            return entry.getLastModified();
        }

        @Override @DefinedBy(Api.COMPILER)
        public boolean delete() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected CharsetDecoder getDecoder(boolean ignoreEncodingErrors) {
            return fileManager.getDecoder(fileManager.getEncodingName(), ignoreEncodingErrors);
        }

        @Override
        protected String inferBinaryName(Iterable<? extends Path> path) {
            String entryName = entry.getName();
            if (zfIndex.symbolFilePrefix != null) {
                String prefix = zfIndex.symbolFilePrefix.path;
                if (entryName.startsWith(prefix))
                    entryName = entryName.substring(prefix.length());
            }
            return removeExtension(entryName).replace('/', '.');
        }

        @Override @DefinedBy(Api.COMPILER)
        public boolean isNameCompatible(String cn, JavaFileObject.Kind k) {
            Objects.requireNonNull(cn);
            if (k == Kind.OTHER && getKind() != k)
                return false;
            return name.equals(cn + k.extension);
        }

        /**
         * Check if two file objects are equal.
         * Two ZipFileIndexFileObjects are equal if the absolute paths of the underlying
         * zip files are equal and if the paths within those zip files are equal.
         */
        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;

            if (!(other instanceof ZipFileIndexFileObject))
                return false;

            ZipFileIndexFileObject o = (ZipFileIndexFileObject) other;
            return zfIndex.getAbsoluteFile().equals(o.zfIndex.getAbsoluteFile())
                    && entry.equals(o.entry);
        }

        @Override
        public int hashCode() {
            return zfIndex.getAbsoluteFile().hashCode() + entry.hashCode();
        }

        private String getPrefixedEntryName() {
            if (zfIndex.symbolFilePrefix != null)
                return zfIndex.symbolFilePrefix.path + entry.getName();
            else
                return entry.getName();
        }
    }

}
