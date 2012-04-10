/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import javax.tools.JavaFileObject;

/**
 * A subclass of JavaFileObject representing regular files.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
class RegularFileObject extends BaseFileObject {

    /** Have the parent directories been created?
     */
    private boolean hasParents = false;
    private String name;
    final File file;
    private Reference<File> absFileRef;

    public RegularFileObject(JavacFileManager fileManager, File f) {
        this(fileManager, f.getName(), f);
    }

    public RegularFileObject(JavacFileManager fileManager, String name, File f) {
        super(fileManager);
        if (f.isDirectory()) {
            throw new IllegalArgumentException("directories not supported");
        }
        this.name = name;
        this.file = f;
    }

    @Override
    public URI toUri() {
        return file.toURI().normalize();
    }

    @Override
    public String getName() {
        return file.getPath();
    }

    @Override
    public String getShortName() {
        return name;
    }

    @Override
    public JavaFileObject.Kind getKind() {
        return getKind(name);
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        fileManager.flushCache(this);
        ensureParentDirectoriesExist();
        return new FileOutputStream(file);
    }

    @Override
    public CharBuffer getCharContent(boolean ignoreEncodingErrors) throws IOException {
        CharBuffer cb = fileManager.getCachedContent(this);
        if (cb == null) {
            InputStream in = new FileInputStream(file);
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
        fileManager.flushCache(this);
        ensureParentDirectoriesExist();
        return new OutputStreamWriter(new FileOutputStream(file), fileManager.getEncodingName());
    }

    @Override
    public long getLastModified() {
        return file.lastModified();
    }

    @Override
    public boolean delete() {
        return file.delete();
    }

    @Override
    protected CharsetDecoder getDecoder(boolean ignoreEncodingErrors) {
        return fileManager.getDecoder(fileManager.getEncodingName(), ignoreEncodingErrors);
    }

    @Override
    protected String inferBinaryName(Iterable<? extends File> path) {
        String fPath = file.getPath();
        //System.err.println("RegularFileObject " + file + " " +r.getPath());
        for (File dir: path) {
            //System.err.println("dir: " + dir);
            String dPath = dir.getPath();
            if (dPath.length() == 0)
                dPath = System.getProperty("user.dir");
            if (!dPath.endsWith(File.separator))
                dPath += File.separator;
            if (fPath.regionMatches(true, 0, dPath, 0, dPath.length())
                && new File(fPath.substring(0, dPath.length())).equals(new File(dPath))) {
                String relativeName = fPath.substring(dPath.length());
                return removeExtension(relativeName).replace(File.separatorChar, '.');
            }
        }
        return null;
    }

    @Override
    public boolean isNameCompatible(String cn, JavaFileObject.Kind kind) {
        cn.getClass();
        // null check
        if (kind == Kind.OTHER && getKind() != kind) {
            return false;
        }
        String n = cn + kind.extension;
        if (name.equals(n)) {
            return true;
        }
        if (name.equalsIgnoreCase(n)) {
            try {
                // allow for Windows
                return file.getCanonicalFile().getName().equals(n);
            } catch (IOException e) {
            }
        }
        return false;
    }

    private void ensureParentDirectoriesExist() throws IOException {
        if (!hasParents) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    if (!parent.exists() || !parent.isDirectory()) {
                        throw new IOException("could not create parent directories");
                    }
                }
            }
            hasParents = true;
        }
    }

    /**
     * Check if two file objects are equal.
     * Two RegularFileObjects are equal if the absolute paths of the underlying
     * files are equal.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;

        if (!(other instanceof RegularFileObject))
            return false;

        RegularFileObject o = (RegularFileObject) other;
        return getAbsoluteFile().equals(o.getAbsoluteFile());
    }

    @Override
    public int hashCode() {
        return getAbsoluteFile().hashCode();
    }

    private File getAbsoluteFile() {
        File absFile = (absFileRef == null ? null : absFileRef.get());
        if (absFile == null) {
            absFile = file.getAbsoluteFile();
            absFileRef = new SoftReference<File>(absFile);
        }
        return absFile;
    }
}
