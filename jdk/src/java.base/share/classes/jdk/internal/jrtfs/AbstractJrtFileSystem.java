/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.CopyOption;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Base class for jrt file systems. jrt filesystem implementations are currently
 * available on top of .jimage file and on top "exploded" build directories.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
abstract class AbstractJrtFileSystem extends FileSystem {

    private final JrtFileSystemProvider provider;

    AbstractJrtFileSystem(JrtFileSystemProvider provider, Map<String, ?> options) {
        this.provider = provider;
    }

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    // static utility methods
    static ReadOnlyFileSystemException readOnly() {
        return new ReadOnlyFileSystemException();
    }

    // if a Path does not exist, throw exception
    static void checkExists(Path path) {
        if (Files.notExists(path)) {
            throw new FileSystemNotFoundException(path.toString());
        }
    }

    static byte[] getBytes(String name) {
        return name.getBytes(UTF_8);
    }

    static String getString(byte[] name) {
        return new String(name, UTF_8);
    }

    // do the supplied options imply that we have to chase symlinks?
    static boolean followLinks(LinkOption... options) {
        if (options != null) {
            for (LinkOption lo : options) {
                if (lo == LinkOption.NOFOLLOW_LINKS) {
                    return false;
                } else if (lo == null) {
                    throw new NullPointerException();
                } else {
                    throw new AssertionError("should not reach here");
                }
            }
        }
        return true;
    }

    // check that the options passed are supported by (read-only) jrt file system
    static void checkOptions(Set<? extends OpenOption> options) {
        // check for options of null type and option is an intance of StandardOpenOption
        for (OpenOption option : options) {
            if (option == null) {
                throw new NullPointerException();
            }
            if (!(option instanceof StandardOpenOption)) {
                throw new IllegalArgumentException();
            }
        }

        if (options.contains(StandardOpenOption.WRITE)
                || options.contains(StandardOpenOption.APPEND)) {
            throw readOnly();
        }
    }

    // FileSystem method implementations
    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        ArrayList<Path> pathArr = new ArrayList<>();
        pathArr.add(getRootPath());
        return pathArr;
    }

    @Override
    public AbstractJrtPath getPath(String first, String... more) {
        String path;
        if (more.length == 0) {
            path = first;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(first);
            for (String segment : more) {
                if (segment.length() > 0) {
                    if (sb.length() > 0) {
                        sb.append('/');
                    }
                    sb.append(segment);
                }
            }
            path = sb.toString();
        }
        return getRootPath().newJrtPath(getBytes(path));
    }

    @Override
    public final boolean isReadOnly() {
        return true;
    }

    @Override
    public final UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final Iterable<FileStore> getFileStores() {
        ArrayList<FileStore> list = new ArrayList<>(1);
        list.add(getFileStore(getRootPath()));
        return list;
    }

    private static final Set<String> supportedFileAttributeViews
            = Collections.unmodifiableSet(
                    new HashSet<String>(Arrays.asList("basic", "jrt")));

    @Override
    public final Set<String> supportedFileAttributeViews() {
        return supportedFileAttributeViews;
    }

    @Override
    public final String toString() {
        return "jrt:/";
    }

    @Override
    public final String getSeparator() {
        return "/";
    }

    private static final String GLOB_SYNTAX = "glob";
    private static final String REGEX_SYNTAX = "regex";

    @Override
    public PathMatcher getPathMatcher(String syntaxAndInput) {
        int pos = syntaxAndInput.indexOf(':');
        if (pos <= 0 || pos == syntaxAndInput.length()) {
            throw new IllegalArgumentException();
        }
        String syntax = syntaxAndInput.substring(0, pos);
        String input = syntaxAndInput.substring(pos + 1);
        String expr;
        if (syntax.equalsIgnoreCase(GLOB_SYNTAX)) {
            expr = JrtUtils.toRegexPattern(input);
        } else {
            if (syntax.equalsIgnoreCase(REGEX_SYNTAX)) {
                expr = input;
            } else {
                throw new UnsupportedOperationException("Syntax '" + syntax
                        + "' not recognized");
            }
        }
        // return matcher
        final Pattern pattern = Pattern.compile(expr);
        return (Path path) -> pattern.matcher(path.toString()).matches();
    }

    // These methods throw read only file system exception
    final void setTimes(AbstractJrtPath jrtPath, FileTime mtime, FileTime atime, FileTime ctime)
            throws IOException {
        throw readOnly();
    }

    final void createDirectory(AbstractJrtPath jrtPath, FileAttribute<?>... attrs) throws IOException {
        throw readOnly();
    }

    final void deleteFile(AbstractJrtPath jrtPath, boolean failIfNotExists)
            throws IOException {
        throw readOnly();
    }

    final OutputStream newOutputStream(AbstractJrtPath jrtPath, OpenOption... options)
            throws IOException {
        throw readOnly();
    }

    final void copyFile(boolean deletesrc, AbstractJrtPath srcPath, AbstractJrtPath dstPath, CopyOption... options)
            throws IOException {
        throw readOnly();
    }

    final FileChannel newFileChannel(AbstractJrtPath jrtPath,
            Set<? extends OpenOption> options,
            FileAttribute<?>... attrs)
            throws IOException {
        throw new UnsupportedOperationException("newFileChannel");
    }

    final InputStream newInputStream(AbstractJrtPath jrtPath) throws IOException {
        return new ByteArrayInputStream(getFileContent(jrtPath));
    }

    final SeekableByteChannel newByteChannel(AbstractJrtPath jrtPath,
            Set<? extends OpenOption> options,
            FileAttribute<?>... attrs)
            throws IOException {
        checkOptions(options);

        byte[] buf = getFileContent(jrtPath);
        final ReadableByteChannel rbc
                = Channels.newChannel(new ByteArrayInputStream(buf));
        final long size = buf.length;
        return new SeekableByteChannel() {
            long read = 0;

            @Override
            public boolean isOpen() {
                return rbc.isOpen();
            }

            @Override
            public long position() throws IOException {
                return read;
            }

            @Override
            public SeekableByteChannel position(long pos)
                    throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read(ByteBuffer dst) throws IOException {
                int n = rbc.read(dst);
                if (n > 0) {
                    read += n;
                }
                return n;
            }

            @Override
            public SeekableByteChannel truncate(long size)
                    throws IOException {
                throw new NonWritableChannelException();
            }

            @Override
            public int write(ByteBuffer src) throws IOException {
                throw new NonWritableChannelException();
            }

            @Override
            public long size() throws IOException {
                return size;
            }

            @Override
            public void close() throws IOException {
                rbc.close();
            }
        };
    }

    final JrtFileStore getFileStore(AbstractJrtPath jrtPath) {
        return new JrtFileStore(jrtPath);
    }

    final void ensureOpen() throws IOException {
        if (!isOpen()) {
            throw new ClosedFileSystemException();
        }
    }

    // abstract methods to be implemented by a particular jrt file system
    abstract AbstractJrtPath getRootPath();

    abstract boolean isSameFile(AbstractJrtPath jrtPath1, AbstractJrtPath jrtPath2) throws IOException;

    abstract boolean isLink(AbstractJrtPath jrtPath) throws IOException;

    abstract AbstractJrtPath resolveLink(AbstractJrtPath jrtPath) throws IOException;

    abstract AbstractJrtFileAttributes getFileAttributes(AbstractJrtPath jrtPath, LinkOption... options) throws IOException;

    abstract boolean exists(AbstractJrtPath jrtPath) throws IOException;

    abstract boolean isDirectory(AbstractJrtPath jrtPath, boolean resolveLinks) throws IOException;

    /**
     * returns the list of child paths of the given directory "path"
     *
     * @param path name of the directory whose content is listed
     * @return iterator for child paths of the given directory path
     */
    abstract Iterator<Path> iteratorOf(AbstractJrtPath jrtPath) throws IOException;

    // returns the content of the file resource specified by the path
    abstract byte[] getFileContent(AbstractJrtPath jrtPath) throws IOException;
}
