/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.nio;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.util.BaseFileManager;


/**
 *  Implementation of JavaFileObject using java.nio.file API.
 *
 *  <p>PathFileObjects are, for the most part, straightforward wrappers around
 *  Path objects. The primary complexity is the support for "inferBinaryName".
 *  This is left as an abstract method, implemented by each of a number of
 *  different factory methods, which compute the binary name based on
 *  information available at the time the file object is created.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
abstract class PathFileObject implements JavaFileObject {
    private JavacPathFileManager fileManager;
    private Path path;

    /**
     * Create a PathFileObject within a directory, such that the binary name
     * can be inferred from the relationship to the parent directory.
     */
    static PathFileObject createDirectoryPathFileObject(JavacPathFileManager fileManager,
            final Path path, final Path dir) {
        return new PathFileObject(fileManager, path) {
            @Override
            String inferBinaryName(Iterable<? extends Path> paths) {
                return toBinaryName(dir.relativize(path));
            }
        };
    }

    /**
     * Create a PathFileObject in a file system such as a jar file, such that
     * the binary name can be inferred from its position within the filesystem.
     */
    static PathFileObject createJarPathFileObject(JavacPathFileManager fileManager,
            final Path path) {
        return new PathFileObject(fileManager, path) {
            @Override
            String inferBinaryName(Iterable<? extends Path> paths) {
                return toBinaryName(path);
            }
        };
    }

    /**
     * Create a PathFileObject whose binary name can be inferred from the
     * relative path to a sibling.
     */
    static PathFileObject createSiblingPathFileObject(JavacPathFileManager fileManager,
            final Path path, final String relativePath) {
        return new PathFileObject(fileManager, path) {
            @Override
            String inferBinaryName(Iterable<? extends Path> paths) {
                return toBinaryName(relativePath, "/");
            }
        };
    }

    /**
     * Create a PathFileObject whose binary name might be inferred from its
     * position on a search path.
     */
    static PathFileObject createSimplePathFileObject(JavacPathFileManager fileManager,
            final Path path) {
        return new PathFileObject(fileManager, path) {
            @Override
            String inferBinaryName(Iterable<? extends Path> paths) {
                Path absPath = path.toAbsolutePath();
                for (Path p: paths) {
                    Path ap = p.toAbsolutePath();
                    if (absPath.startsWith(ap)) {
                        try {
                            Path rp = ap.relativize(absPath);
                            if (rp != null) // maybe null if absPath same as ap
                                return toBinaryName(rp);
                        } catch (IllegalArgumentException e) {
                            // ignore this p if cannot relativize path to p
                        }
                    }
                }
                return null;
            }
        };
    }

    protected PathFileObject(JavacPathFileManager fileManager, Path path) {
        fileManager.getClass(); // null check
        path.getClass();        // null check
        this.fileManager = fileManager;
        this.path = path;
    }

    abstract String inferBinaryName(Iterable<? extends Path> paths);

    /**
     * Return the Path for this object.
     * @return the Path for this object.
     */
    Path getPath() {
        return path;
    }

    @Override
    public Kind getKind() {
        return BaseFileManager.getKind(path.getFileName().toString());
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        simpleName.getClass();
        // null check
        if (kind == Kind.OTHER && getKind() != kind) {
            return false;
        }
        String sn = simpleName + kind.extension;
        String pn = path.getFileName().toString();
        if (pn.equals(sn)) {
            return true;
        }
        if (pn.equalsIgnoreCase(sn)) {
            try {
                // allow for Windows
                return path.toRealPath(LinkOption.NOFOLLOW_LINKS).getFileName().toString().equals(sn);
            } catch (IOException e) {
            }
        }
        return false;
    }

    @Override
    public NestingKind getNestingKind() {
        return null;
    }

    @Override
    public Modifier getAccessLevel() {
        return null;
    }

    @Override
    public URI toUri() {
        return path.toUri();
    }

    @Override
    public String getName() {
        return path.toString();
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        fileManager.flushCache(this);
        ensureParentDirectoriesExist();
        return Files.newOutputStream(path);
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        CharsetDecoder decoder = fileManager.getDecoder(fileManager.getEncodingName(), ignoreEncodingErrors);
        return new InputStreamReader(openInputStream(), decoder);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        CharBuffer cb = fileManager.getCachedContent(this);
        if (cb == null) {
            InputStream in = openInputStream();
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
        return new OutputStreamWriter(Files.newOutputStream(path), fileManager.getEncodingName());
    }

    @Override
    public long getLastModified() {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public boolean delete() {
        try {
            Files.delete(path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isSameFile(PathFileObject other) {
        try {
            return Files.isSameFile(path, other.path);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof PathFileObject && path.equals(((PathFileObject) other).path));
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + path + "]";
    }

    private void ensureParentDirectoriesExist() throws IOException {
        Path parent = path.getParent();
        if (parent != null)
            Files.createDirectories(parent);
    }

    private long size() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }

    protected static String toBinaryName(Path relativePath) {
        return toBinaryName(relativePath.toString(),
                relativePath.getFileSystem().getSeparator());
    }

    protected static String toBinaryName(String relativePath, String sep) {
        return removeExtension(relativePath).replace(sep, ".");
    }

    protected static String removeExtension(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        return (lastDot == -1 ? fileName : fileName.substring(0, lastDot));
    }
}
