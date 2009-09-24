/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javac.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.tools.JavaFileObject;

import com.sun.tools.javac.file.JavacFileManager.Archive;
import com.sun.tools.javac.file.RelativePath.RelativeDirectory;
import com.sun.tools.javac.file.RelativePath.RelativeFile;
import com.sun.tools.javac.util.List;

/**
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class ZipArchive implements Archive {

    public ZipArchive(JavacFileManager fm, ZipFile zdir) throws IOException {
        this(fm, zdir, true);
    }

    protected ZipArchive(JavacFileManager fm, ZipFile zdir, boolean initMap) throws IOException {
        this.fileManager = fm;
        this.zdir = zdir;
        this.map = new HashMap<RelativeDirectory,List<String>>();
        if (initMap)
            initMap();
    }

    protected void initMap() throws IOException {
        for (Enumeration<? extends ZipEntry> e = zdir.entries(); e.hasMoreElements(); ) {
            ZipEntry entry;
            try {
                entry = e.nextElement();
            } catch (InternalError ex) {
                IOException io = new IOException();
                io.initCause(ex); // convenience constructors added in Mustang :-(
                throw io;
            }
            addZipEntry(entry);
        }
    }

    void addZipEntry(ZipEntry entry) {
        String name = entry.getName();
        int i = name.lastIndexOf('/');
        RelativeDirectory dirname = new RelativeDirectory(name.substring(0, i+1));
        String basename = name.substring(i+1);
        if (basename.length() == 0)
            return;
        List<String> list = map.get(dirname);
        if (list == null)
            list = List.nil();
        list = list.prepend(basename);
        map.put(dirname, list);
    }

    public boolean contains(RelativePath name) {
        RelativeDirectory dirname = name.dirname();
        String basename = name.basename();
        if (basename.length() == 0)
            return false;
        List<String> list = map.get(dirname);
        return (list != null && list.contains(basename));
    }

    public List<String> getFiles(RelativeDirectory subdirectory) {
        return map.get(subdirectory);
    }

    public JavaFileObject getFileObject(RelativeDirectory subdirectory, String file) {
        ZipEntry ze = new RelativeFile(subdirectory, file).getZipEntry(zdir);
        return new ZipFileObject(this, file, ze);
    }

    public Set<RelativeDirectory> getSubdirectories() {
        return map.keySet();
    }

    public void close() throws IOException {
        zdir.close();
    }

    @Override
    public String toString() {
        return "ZipArchive[" + zdir.getName() + "]";
    }

    protected JavacFileManager fileManager;
    protected final Map<RelativeDirectory,List<String>> map;
    protected final ZipFile zdir;

    /**
     * A subclass of JavaFileObject representing zip entries.
     */
    public static class ZipFileObject extends BaseFileObject {

        private String name;
        ZipArchive zarch;
        ZipEntry entry;

        protected ZipFileObject(ZipArchive zarch, String name, ZipEntry entry) {
            super(zarch.fileManager);
            this.zarch = zarch;
            this.name = name;
            this.entry = entry;
        }

        public URI toUri() {
            File zipFile = new File(zarch.zdir.getName());
            return createJarUri(zipFile, entry.getName());
        }

        @Override
        public String getName() {
            return zarch.zdir.getName() + "(" + entry.getName() + ")";
        }

        @Override
        public String getShortName() {
            return new File(zarch.zdir.getName()).getName() + "(" + entry + ")";
        }

        @Override
        public JavaFileObject.Kind getKind() {
            return getKind(entry.getName());
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return zarch.zdir.getInputStream(entry);
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public CharBuffer getCharContent(boolean ignoreEncodingErrors) throws IOException {
            CharBuffer cb = fileManager.getCachedContent(this);
            if (cb == null) {
                InputStream in = zarch.zdir.getInputStream(entry);
                try {
                    ByteBuffer bb = fileManager.makeByteBuffer(in);
                    JavaFileObject prev = fileManager.log.useSource(this);
                    try {
                        cb = fileManager.decode(bb, ignoreEncodingErrors);
                    } finally {
                        fileManager.log.useSource(prev);
                    }
                    fileManager.recycleByteBuffer(bb);
                    if (!ignoreEncodingErrors) {
                        fileManager.cache(this, cb);
                    }
                } finally {
                    in.close();
                }
            }
            return cb;
        }

        @Override
        public Writer openWriter() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLastModified() {
            return entry.getTime();
        }

        @Override
        public boolean delete() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected CharsetDecoder getDecoder(boolean ignoreEncodingErrors) {
            return fileManager.getDecoder(fileManager.getEncodingName(), ignoreEncodingErrors);
        }

        @Override
        protected String inferBinaryName(Iterable<? extends File> path) {
            String entryName = entry.getName();
            return removeExtension(entryName).replace('/', '.');
        }

        @Override
        public boolean isNameCompatible(String cn, JavaFileObject.Kind k) {
            cn.getClass();
            // null check
            if (k == Kind.OTHER && getKind() != k) {
                return false;
            }
            return name.equals(cn + k.extension);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ZipFileObject)) {
                return false;
            }
            ZipFileObject o = (ZipFileObject) other;
            return zarch.zdir.equals(o.zarch.zdir) || name.equals(o.name);
        }

        @Override
        public int hashCode() {
            return zarch.zdir.hashCode() + name.hashCode();
        }
    }

}
