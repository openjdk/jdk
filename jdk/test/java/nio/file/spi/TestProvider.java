/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.spi.FileSystemProvider;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.channels.SeekableByteChannel;
import java.net.URI;
import java.util.*;
import java.io.IOException;

public class TestProvider extends FileSystemProvider {

    private final FileSystem theFileSystem;

    public TestProvider(FileSystemProvider defaultProvider) {
        theFileSystem = new TestFileSystem(this);
    }

    @Override
    public String getScheme() {
        return "file";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String,?> env) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        return theFileSystem;
    }

    @Override
    public Path getPath(URI uri) {
        throw new RuntimeException("not implemented");
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
    public DirectoryStream<Path> newDirectoryStream(Path dir,
                                                    DirectoryStream.Filter<? super Path> filter)
        throws IOException
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs)
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
    public boolean isHidden(Path file) throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public FileStore getFileStore(Path file) throws IOException {
        throw new RuntimeException("not implemented");
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

    static class TestFileSystem extends FileSystem {
        private final TestProvider provider;

        TestFileSystem(TestProvider provider) {
            this.provider = provider;
        }

        @Override
        public FileSystemProvider provider() {
            return provider;
        }

        @Override
        public void close() throws IOException {
            throw new RuntimeException("not implemented");
        }

        @Override
        public boolean isOpen() {
            throw new RuntimeException("not implemented");
        }

        @Override
        public boolean isReadOnly() {
            throw new RuntimeException("not implemented");
        }

        @Override
        public String getSeparator() {
            throw new RuntimeException("not implemented");
        }

        @Override
        public Iterable<Path> getRootDirectories() {
            throw new RuntimeException("not implemented");
        }

        @Override
        public Iterable<FileStore> getFileStores() {
            throw new RuntimeException("not implemented");
        }

        @Override
        public Set<String> supportedFileAttributeViews() {
            throw new RuntimeException("not implemented");
        }

        @Override
        public Path getPath(String first, String... more) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public PathMatcher getPathMatcher(String syntaxAndPattern) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public UserPrincipalLookupService getUserPrincipalLookupService() {
            throw new RuntimeException("not implemented");
        }

        @Override
        public WatchService newWatchService() throws IOException {
            throw new RuntimeException("not implemented");
        }
    }
}
