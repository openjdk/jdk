/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.nio.channels.SeekableByteChannel;
import java.net.URI;
import java.util.*;
import java.io.*;

/**
 * A "pass through" file system implementation that passes through, or delegates,
 * everything to the default file system.
 */

class PassThroughFileSystem extends FileSystem {
    private final FileSystemProvider provider;
    private final FileSystem delegate;

    PassThroughFileSystem(FileSystemProvider provider, FileSystem delegate) {
        this.provider = provider;
        this.delegate = delegate;
    }

    /**
     * Creates a new "pass through" file system. Useful for test environments
     * where the provider might not be deployed.
     */
    static FileSystem create() throws IOException {
        FileSystemProvider provider = new PassThroughProvider();
        Map<String,?> env = Collections.emptyMap();
        URI uri = URI.create("pass:///");
        return provider.newFileSystem(uri, env);
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public boolean isReadOnly() {
        return delegate.isReadOnly();
    }

    @Override
    public String getSeparator() {
        return delegate.getSeparator();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        final Iterable<Path> roots = delegate.getRootDirectories();
        return new Iterable<Path>() {
            @Override
            public Iterator<Path> iterator() {
                final Iterator<Path> itr = roots.iterator();
                return new Iterator<Path>() {
                    @Override
                    public boolean hasNext() {
                        return itr.hasNext();
                    }
                    @Override
                    public Path next() {
                        return new PassThroughPath(delegate, itr.next());
                    }
                    @Override
                    public void remove() {
                        itr.remove();
                    }
                };
            }
        };
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        // assume that unwrapped objects aren't exposed
        return delegate.getFileStores();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        // assume that unwrapped objects aren't exposed
        return delegate.supportedFileAttributeViews();
    }

    @Override
    public Path getPath(String path) {
        return new PassThroughPath(this, delegate.getPath(path));
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        final PathMatcher matcher = delegate.getPathMatcher(syntaxAndPattern);
        return new PathMatcher() {
            @Override
            public boolean matches(Path path) {
                return matcher.matches(PassThroughPath.unwrap(path));
            }
        };
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        // assume that unwrapped objects aren't exposed
        return delegate.getUserPrincipalLookupService();
    }

    @Override
    public WatchService newWatchService() throws IOException {
        // to keep it simple
        throw new UnsupportedOperationException();
    }

    static class PassThroughProvider extends FileSystemProvider {
        private static final String SCHEME = "pass";
        private static volatile PassThroughFileSystem delegate;

        public PassThroughProvider() { }

        @Override
        public String getScheme() {
            return SCHEME;
        }

        private void checkScheme(URI uri) {
            if (!uri.getScheme().equalsIgnoreCase(SCHEME))
                throw new IllegalArgumentException();
        }

        private void checkUri(URI uri) {
            checkScheme(uri);
            if (!uri.getSchemeSpecificPart().equals("///"))
                throw new IllegalArgumentException();
        }

        @Override
        public FileSystem newFileSystem(URI uri, Map<String,?> env)
            throws IOException
        {
            checkUri(uri);
            synchronized (PassThroughProvider.class) {
                if (delegate != null)
                    throw new FileSystemAlreadyExistsException();
                PassThroughFileSystem result =
                    new PassThroughFileSystem(this, FileSystems.getDefault());
                delegate = result;
                return result;
            }
        }

        @Override
        public FileSystem getFileSystem(URI uri) {
            checkUri(uri);
            FileSystem result = delegate;
            if (result == null)
                throw new FileSystemNotFoundException();
            return result;
        }

        @Override
        public Path getPath(URI uri) {
            checkScheme(uri);
            if (delegate == null)
                throw new FileSystemNotFoundException();
            uri = URI.create(delegate.provider().getScheme() + ":" +
                             uri.getSchemeSpecificPart());
            return new PassThroughPath(delegate, delegate.provider().getPath(uri));
        }
    }

    static class PassThroughPath extends Path {
        private final FileSystem fs;
        private final Path delegate;

        PassThroughPath(FileSystem fs, Path delegate) {
            this.fs = fs;
            this.delegate = delegate;
        }

        private Path wrap(Path path) {
            return (path != null) ? new PassThroughPath(fs, path) : null;
        }

        static Path unwrap(Path wrapper) {
            if (!(wrapper instanceof PassThroughPath))
                throw new ProviderMismatchException();
            return ((PassThroughPath)wrapper).delegate;
        }

        @Override
        public FileSystem getFileSystem() {
            return fs;
        }

        @Override
        public boolean isAbsolute() {
            return delegate.isAbsolute();
        }

        @Override
        public Path getRoot() {
            return wrap(delegate.getRoot());
        }


        @Override
        public Path getName() {
            return wrap(delegate.getName());
        }

        @Override
        public Path getParent() {
            return wrap(delegate.getParent());
        }

        @Override
        public int getNameCount() {
            return delegate.getNameCount();
        }

        @Override
        public Path getName(int index) {
            return wrap(delegate.getName(index));
        }

        @Override
        public Path subpath(int beginIndex, int endIndex) {
            return wrap(delegate.subpath(beginIndex, endIndex));
        }

        @Override
        public boolean startsWith(Path other) {
            return delegate.startsWith(unwrap(other));
        }

        @Override
        public boolean endsWith(Path other) {
            return delegate.endsWith(unwrap(other));
        }

        @Override
        public Path normalize() {
            return wrap(delegate.normalize());
        }

        @Override
        public Path resolve(Path other) {
            return wrap(delegate.resolve(unwrap(other)));
        }

        @Override
        public Path resolve(String other) {
            return wrap(delegate.resolve(other));
        }

        @Override
        public Path relativize(Path other) {
            return wrap(delegate.relativize(unwrap(other)));
        }

        @Override
        public void setAttribute(String attribute, Object value, LinkOption... options)
            throws IOException
        {
            delegate.setAttribute(attribute, value, options);
        }

        @Override
        public Object getAttribute(String attribute, LinkOption... options)
            throws IOException
        {
            // assume that unwrapped objects aren't exposed
            return delegate.getAttribute(attribute, options);
        }

        @Override
        public Map<String,?> readAttributes(String attributes, LinkOption... options)
            throws IOException
        {
            // assume that unwrapped objects aren't exposed
            return delegate.readAttributes(attributes, options);
        }

        @Override
        public <V extends FileAttributeView> V getFileAttributeView(Class<V> type,
                                                                    LinkOption... options)
        {
            return delegate.getFileAttributeView(type, options);
        }

        @Override
        public void delete() throws IOException {
            delegate.delete();
        }

        @Override
        public void deleteIfExists() throws IOException {
            delegate.deleteIfExists();
        }

        @Override
        public Path createSymbolicLink(Path target, FileAttribute<?>... attrs)
            throws IOException
        {
            delegate.createSymbolicLink(unwrap(target), attrs);
            return this;
        }

        @Override
        public Path createLink(Path existing) throws IOException {
            delegate.createLink(unwrap(existing));
            return this;
        }

        @Override
        public Path readSymbolicLink() throws IOException {
            return wrap(delegate.readSymbolicLink());
        }

        @Override
        public URI toUri() {
            String ssp = delegate.toUri().getSchemeSpecificPart();
            return URI.create(fs.provider().getScheme() + ":" + ssp);
        }

        @Override
        public Path toAbsolutePath() {
            return wrap(delegate.toAbsolutePath());
        }

        @Override
        public Path toRealPath(boolean resolveLinks) throws IOException {
            return wrap(delegate.toRealPath(resolveLinks));
        }

        @Override
        public Path copyTo(Path target, CopyOption... options) throws IOException {
            return wrap(delegate.copyTo(unwrap(target), options));
        }

        @Override
        public Path moveTo(Path target, CopyOption... options) throws IOException {
            return wrap(delegate.copyTo(unwrap(target), options));
        }

        private DirectoryStream<Path> wrap(final DirectoryStream<Path> stream) {
            return new DirectoryStream<Path>() {
                @Override
                public Iterator<Path> iterator() {
                    final Iterator<Path> itr = stream.iterator();
                    return new Iterator<Path>() {
                        @Override
                        public boolean hasNext() {
                            return itr.hasNext();
                        }
                        @Override
                        public Path next() {
                            return wrap(itr.next());
                        }
                        @Override
                        public void remove() {
                            itr.remove();
                        }
                    };
                }
                @Override
                public void close() throws IOException {
                    stream.close();
                }
            };
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream() throws IOException {
            return wrap(delegate.newDirectoryStream());
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(String glob)
            throws IOException
        {
            return wrap(delegate.newDirectoryStream(glob));
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(DirectoryStream.Filter<? super Path> filter)
            throws IOException
        {
            return wrap(delegate.newDirectoryStream(filter));
        }

        @Override
        public Path createFile(FileAttribute<?>... attrs) throws IOException {
            delegate.createFile(attrs);
            return this;
        }

        @Override
        public Path createDirectory(FileAttribute<?>... attrs)
            throws IOException
        {
            delegate.createDirectory(attrs);
            return this;
        }

        @Override
        public SeekableByteChannel newByteChannel(Set<? extends OpenOption> options,
                                                       FileAttribute<?>... attrs)
            throws IOException
        {
            return delegate.newByteChannel(options, attrs);
        }

        @Override
        public SeekableByteChannel newByteChannel(OpenOption... options)
            throws IOException
        {
            return delegate.newByteChannel(options);
        }

        @Override
        public InputStream newInputStream(OpenOption... options) throws IOException {
            return delegate.newInputStream();
        }

        @Override
        public OutputStream newOutputStream(OpenOption... options)
            throws IOException
        {
            return delegate.newOutputStream(options);
        }

        @Override
        public boolean isHidden() throws IOException {
            return delegate.isHidden();
        }

        @Override
        public void checkAccess(AccessMode... modes) throws IOException {
            delegate.checkAccess(modes);
        }

        @Override
        public boolean exists() {
            return delegate.exists();
        }

        @Override
        public boolean notExists() {
            return delegate.notExists();
        }

        @Override
        public FileStore getFileStore() throws IOException {
            return delegate.getFileStore();
        }

        @Override
        public WatchKey register(WatchService watcher,
                                      WatchEvent.Kind<?>[] events,
                                      WatchEvent.Modifier... modifiers)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public  WatchKey register(WatchService watcher,
                                      WatchEvent.Kind<?>... events)
        {
            throw new UnsupportedOperationException();
        }


        @Override
        public Iterator<Path> iterator() {
            final Iterator<Path> itr = delegate.iterator();
            return new Iterator<Path>() {
                @Override
                public boolean hasNext() {
                    return itr.hasNext();
                }
                @Override
                public Path next() {
                    return wrap(itr.next());
                }
                @Override
                public void remove() {
                    itr.remove();
                }
            };
        }

        @Override
        public int compareTo(Path other) {
            return delegate.compareTo(unwrap(other));
        }

        @Override
        public boolean isSameFile(Path other) throws IOException {
            return delegate.isSameFile(unwrap(other));
        }


        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PassThroughPath))
                return false;
            return delegate.equals(unwrap((PassThroughPath)other));
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
