/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, JetBrains s.r.o.. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class CustomFileSystemProvider extends FileSystemProvider {

    private final FileSystemProvider defaultProvider;

    public CustomFileSystemProvider(FileSystemProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    FileSystemProvider defaultProvider() {
        return defaultProvider;
    }

    @Override
    public String getScheme() {
        return "file";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String,?> env) throws IOException {
        return defaultProvider.newFileSystem(uri, env);
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        return defaultProvider.getFileSystem(uri);
    }

    @Override
    public Path getPath(URI uri) {
        return defaultProvider.getPath(uri);
    }

    @Override
    public void setAttribute(Path file, String attribute, Object value,
                             LinkOption... options)
        throws IOException
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public Map<String,Object> readAttributes(Path file, String attributes,
                                             LinkOption... options)
        throws IOException
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path file,
                                                            Class<A> type,
                                                            LinkOption... options)
        throws IOException
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path file,
                                                                Class<V> type,
                                                                LinkOption... options)
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public boolean isHidden(Path file) throws IOException {
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public boolean isSameFile(Path file, Path other) throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void checkAccess(Path file, AccessMode... modes)
        throws IOException
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options)
        throws IOException
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void move(Path source, Path target, CopyOption... options)
        throws IOException
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void delete(Path file) throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs)
        throws IOException
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void createLink(Path link, Path existing) throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs)
        throws IOException
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir,
                                                    DirectoryStream.Filter<? super Path> filter)
        throws IOException
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public SeekableByteChannel newByteChannel(Path file,
                                              Set<? extends OpenOption> options,
                                              FileAttribute<?>... attrs)
        throws IOException
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public FileChannel newFileChannel(Path file,
                                      Set<? extends OpenOption> options,
                                      FileAttribute<?>... attrs)
        throws IOException
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public FileStore getFileStore(Path file) throws IOException {
        throw new RuntimeException("not implemented");
    }
}
