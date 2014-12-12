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

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public final class JrtFileSystemProvider extends FileSystemProvider {
    private volatile FileSystem theFileSystem;

    public JrtFileSystemProvider() { }

    @Override
    public String getScheme() {
        return "jrt";
    }

    /**
     * Need FilePermission ${java.home}/-", "read" to create or get jrt:/
     */
    private void checkPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            String home = SystemImages.RUNTIME_HOME;
            FilePermission perm =
                new FilePermission(home + File.separator + "-", "read");
            sm.checkPermission(perm);
        }
    }

    private void checkUri(URI uri) {
        if (!uri.getScheme().equalsIgnoreCase(getScheme()))
            throw new IllegalArgumentException("URI does not match this provider");
        if (uri.getAuthority() != null)
            throw new IllegalArgumentException("Authority component present");
        if (uri.getPath() == null)
            throw new IllegalArgumentException("Path component is undefined");
        if (!uri.getPath().equals("/"))
            throw new IllegalArgumentException("Path component should be '/'");
        if (uri.getQuery() != null)
            throw new IllegalArgumentException("Query component present");
        if (uri.getFragment() != null)
            throw new IllegalArgumentException("Fragment component present");
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env)
        throws IOException
    {
        checkPermission();
        checkUri(uri);
        return new JrtFileSystem(this, env);
    }

    @Override
    public Path getPath(URI uri) {
        checkPermission();
        if (!uri.getScheme().equalsIgnoreCase(getScheme()))
            throw new IllegalArgumentException("URI does not match this provider");
        if (uri.getAuthority() != null)
            throw new IllegalArgumentException("Authority component present");
        if (uri.getQuery() != null)
            throw new IllegalArgumentException("Query component present");
        if (uri.getFragment() != null)
            throw new IllegalArgumentException("Fragment component present");
        String path = uri.getPath();
        if (path == null || path.charAt(0) != '/')
            throw new IllegalArgumentException("Invalid path component");
        return getTheFileSystem().getPath(path);
    }

    private FileSystem getTheFileSystem() {
        checkPermission();
        FileSystem fs = this.theFileSystem;
        if (fs == null) {
            synchronized (this) {
                fs = this.theFileSystem;
                if (fs == null) {
                    try {
                        this.theFileSystem = fs = new JrtFileSystem(this, null) {
                            @Override public void close() {
                                throw new UnsupportedOperationException();
                            }
                        };
                    } catch (IOException ioe) {
                        throw new InternalError(ioe);
                    }
                }
            }
        }
        return fs;
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        checkPermission();
        checkUri(uri);
        return getTheFileSystem();
    }

    // Checks that the given file is a JrtPath
    static final JrtPath toJrtPath(Path path) {
        if (path == null)
            throw new NullPointerException();
        if (!(path instanceof JrtPath))
            throw new ProviderMismatchException();
        return (JrtPath)path;
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        toJrtPath(path).checkAccess(modes);
    }

    @Override
    public void copy(Path src, Path target, CopyOption... options)
        throws IOException
    {
        toJrtPath(src).copy(toJrtPath(target), options);
    }

    @Override
    public void createDirectory(Path path, FileAttribute<?>... attrs)
        throws IOException
    {
        toJrtPath(path).createDirectory(attrs);
    }

    @Override
    public final void delete(Path path) throws IOException {
        toJrtPath(path).delete();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V
        getFileAttributeView(Path path, Class<V> type, LinkOption... options)
    {
        return JrtFileAttributeView.get(toJrtPath(path), type);
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return toJrtPath(path).getFileStore();
    }

    @Override
    public boolean isHidden(Path path) {
        return toJrtPath(path).isHidden();
    }

    @Override
    public boolean isSameFile(Path path, Path other) throws IOException {
        return toJrtPath(path).isSameFile(other);
    }

    @Override
    public void move(Path src, Path target, CopyOption... options)
        throws IOException
    {
        toJrtPath(src).move(toJrtPath(target), options);
    }

    @Override
    public AsynchronousFileChannel newAsynchronousFileChannel(Path path,
            Set<? extends OpenOption> options,
            ExecutorService exec,
            FileAttribute<?>... attrs)
            throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path,
                                              Set<? extends OpenOption> options,
                                              FileAttribute<?>... attrs)
        throws IOException
    {
        return toJrtPath(path).newByteChannel(options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(
        Path path, Filter<? super Path> filter) throws IOException
    {
        return toJrtPath(path).newDirectoryStream(filter);
    }

    @Override
    public FileChannel newFileChannel(Path path,
                                      Set<? extends OpenOption> options,
                                      FileAttribute<?>... attrs)
        throws IOException
    {
        return toJrtPath(path).newFileChannel(options, attrs);
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options)
        throws IOException
    {
        return toJrtPath(path).newInputStream(options);
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options)
        throws IOException
    {
        return toJrtPath(path).newOutputStream(options);
    }

    @Override
    @SuppressWarnings("unchecked") // Cast to A
    public <A extends BasicFileAttributes> A
        readAttributes(Path path, Class<A> type, LinkOption... options)
        throws IOException
    {
        if (type == BasicFileAttributes.class || type == JrtFileAttributes.class)
            return (A)toJrtPath(path).getAttributes();
        return null;
    }

    @Override
    public Map<String, Object>
        readAttributes(Path path, String attribute, LinkOption... options)
        throws IOException
    {
        return toJrtPath(path).readAttributes(attribute, options);
    }

    @Override
    public void setAttribute(Path path, String attribute,
                             Object value, LinkOption... options)
        throws IOException
    {
        toJrtPath(path).setAttribute(attribute, value, options);
    }
}
