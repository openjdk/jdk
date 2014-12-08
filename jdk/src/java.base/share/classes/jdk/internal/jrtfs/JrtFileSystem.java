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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.file.AccessMode;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.CopyOption;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jdk.internal.jimage.ImageReader;
import jdk.internal.jimage.ImageReader.Node;
import jdk.internal.jimage.UTF8String;

/**
 * A FileSystem built on System jimage files.
 */
class JrtFileSystem extends FileSystem {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private final JrtFileSystemProvider provider;
    // System image readers
    private ImageReader bootImage;
    private ImageReader extImage;
    private ImageReader appImage;
    // root path
    private final JrtPath rootPath;
    private volatile boolean isOpen;

    private static void checkExists(Path path) {
        if (Files.notExists(path)) {
            throw new FileSystemNotFoundException(path.toString());
        }
    }

    // open a .jimage and build directory structure
    private static ImageReader openImage(Path path) throws IOException {
        ImageReader image = ImageReader.open(path.toString());
        image.getRootDirectory();
        return image;
    }

    JrtFileSystem(JrtFileSystemProvider provider,
            Map<String, ?> env)
            throws IOException {
        this.provider = provider;
        checkExists(SystemImages.bootImagePath);
        checkExists(SystemImages.extImagePath);
        checkExists(SystemImages.appImagePath);

        // open image files
        this.bootImage = openImage(SystemImages.bootImagePath);
        this.extImage = openImage(SystemImages.extImagePath);
        this.appImage = openImage(SystemImages.appImagePath);

        rootPath = new JrtPath(this, new byte[]{'/'});
        isOpen = true;
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public void close() throws IOException {
        cleanup();
    }

    @Override
    protected void finalize() {
        try {
            cleanup();
        } catch (IOException ignored) {}
    }

    // clean up this file system - called from finalize and close
    private void cleanup() throws IOException {
        if (!isOpen) {
            return;
        }

        synchronized(this) {
            isOpen = false;

            // close all image readers and null out
            bootImage.close();
            extImage.close();
            appImage.close();
            bootImage = null;
            extImage = null;
            appImage = null;
        }
    }

    private void ensureOpen() throws IOException {
        if (!isOpen) {
            throw new ClosedFileSystemException();
        }
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    private ReadOnlyFileSystemException readOnly() {
        return new ReadOnlyFileSystemException();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        ArrayList<Path> pathArr = new ArrayList<>();
        pathArr.add(rootPath);
        return pathArr;
    }

    JrtPath getRootPath() {
        return rootPath;
    }

    @Override
    public JrtPath getPath(String first, String... more) {
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
        return new JrtPath(this, getBytes(path));
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    FileStore getFileStore(JrtPath path) {
        return new JrtFileStore(path);
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        ArrayList<FileStore> list = new ArrayList<>(1);
        list.add(new JrtFileStore(new JrtPath(this, new byte[]{'/'})));
        return list;
    }

    private static final Set<String> supportedFileAttributeViews
            = Collections.unmodifiableSet(
                    new HashSet<String>(Arrays.asList("basic", "jrt")));

    @Override
    public Set<String> supportedFileAttributeViews() {
        return supportedFileAttributeViews;
    }

    @Override
    public String toString() {
        return "jrt:/";
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
        if (syntax.equals(GLOB_SYNTAX)) {
            expr = JrtUtils.toRegexPattern(input);
        } else {
            if (syntax.equals(REGEX_SYNTAX)) {
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

    static byte[] getBytes(String name) {
        return name.getBytes(UTF_8);
    }

    static String getString(byte[] name) {
        return new String(name, UTF_8);
    }

    private static class NodeAndImage {
        final Node node;
        final ImageReader image;

        NodeAndImage(Node node, ImageReader image) {
            this.node = node; this.image = image;
        }

        byte[] getResource() throws IOException {
            return image.getResource(node);
        }
    }

    private NodeAndImage findNode(byte[] path) throws IOException {
        ImageReader image = bootImage;
        Node node = bootImage.findNode(path);
        if (node == null) {
            image = extImage;
            node = extImage.findNode(path);
        }
        if (node == null) {
            image = appImage;
            node = appImage.findNode(path);
        }
        if (node == null || node.isHidden()) {
            throw new NoSuchFileException(getString(path));
        }
        return new NodeAndImage(node, image);
    }

    private NodeAndImage checkNode(byte[] path) throws IOException {
        ensureOpen();
        return findNode(path);
    }

    private NodeAndImage checkResource(byte[] path) throws IOException {
        NodeAndImage ni = checkNode(path);
        if (ni.node.isDirectory()) {
            throw new FileSystemException(getString(path) + " is a directory");
        }

        assert ni.node.isResource() : "resource node expected here";
        return ni;
    }

    // package private helpers
    JrtFileAttributes getFileAttributes(byte[] path)
            throws IOException {
        NodeAndImage ni = checkNode(path);
        return new JrtFileAttributes(ni.node);
    }

    void setTimes(byte[] path, FileTime mtime, FileTime atime, FileTime ctime)
            throws IOException {
        throw readOnly();
    }

    boolean exists(byte[] path) throws IOException {
        ensureOpen();
        try {
            findNode(path);
        } catch (NoSuchFileException exp) {
            return false;
        }
        return true;
    }

    boolean isDirectory(byte[] path)
            throws IOException {
        ensureOpen();
        NodeAndImage ni = checkNode(path);
        return ni.node.isDirectory();
    }

    JrtPath toJrtPath(String path) {
        return toJrtPath(getBytes(path));
    }

    JrtPath toJrtPath(byte[] path) {
        return new JrtPath(this, path);
    }

    /**
     * returns the list of child paths of the given directory "path"
     *
     * @param path name of the directory whose content is listed
     * @param childPrefix prefix added to returned children names - may be null
              in which case absolute child paths are returned
     * @return iterator for child paths of the given directory path
     */
    Iterator<Path> iteratorOf(byte[] path, String childPrefix)
            throws IOException {
        NodeAndImage ni = checkNode(path);
        if (!ni.node.isDirectory()) {
            throw new NotDirectoryException(getString(path));
        }

        if (ni.node.isRootDir()) {
            return rootDirIterator(path, childPrefix);
        }

        return nodesToIterator(toJrtPath(path), childPrefix, ni.node.getChildren());
    }

    private Iterator<Path> nodesToIterator(Path path, String childPrefix, List<Node> childNodes) {
        List<Path> childPaths;
        if (childPrefix == null) {
            childPaths = childNodes.stream()
                .filter(Node::isVisible)
                .map(child -> toJrtPath(child.getNameString()))
                .collect(Collectors.toCollection(ArrayList::new));
        } else {
            childPaths = childNodes.stream()
                .filter(Node::isVisible)
                .map(child -> toJrtPath(childPrefix + child.getNameString().substring(1)))
                .collect(Collectors.toCollection(ArrayList::new));
        }
        return childPaths.iterator();
    }

    private List<Node> rootChildren;
    private static void addRootDirContent(List<Node> dest, List<Node> src) {
        for (Node n : src) {
            // only module directories at the top level. Filter other stuff!
            if (n.isModuleDir()) {
                dest.add(n);
            }
        }
    }

    private synchronized void initRootChildren(byte[] path) {
        if (rootChildren == null) {
            rootChildren = new ArrayList<>();
            addRootDirContent(rootChildren, bootImage.findNode(path).getChildren());
            addRootDirContent(rootChildren, extImage.findNode(path).getChildren());
            addRootDirContent(rootChildren, appImage.findNode(path).getChildren());
        }
    }

    private Iterator<Path> rootDirIterator(byte[] path, String childPrefix) throws IOException {
        initRootChildren(path);
        return nodesToIterator(rootPath, childPrefix, rootChildren);
    }

    void createDirectory(byte[] dir, FileAttribute<?>... attrs)
            throws IOException {
        throw readOnly();
    }

    void copyFile(boolean deletesrc, byte[] src, byte[] dst, CopyOption... options)
            throws IOException {
        throw readOnly();
    }

    public void deleteFile(byte[] path, boolean failIfNotExists)
            throws IOException {
        throw readOnly();
    }

    OutputStream newOutputStream(byte[] path, OpenOption... options)
            throws IOException {
        throw readOnly();
    }

    private void checkOptions(Set<? extends OpenOption> options) {
        // check for options of null type and option is an intance of StandardOpenOption
        for (OpenOption option : options) {
            if (option == null) {
                throw new NullPointerException();
            }
            if (!(option instanceof StandardOpenOption)) {
                throw new IllegalArgumentException();
            }
        }
    }

    // Returns an input stream for reading the contents of the specified
    // file entry.
    InputStream newInputStream(byte[] path) throws IOException {
        final NodeAndImage ni = checkResource(path);
        return new ByteArrayInputStream(ni.getResource());
    }

    SeekableByteChannel newByteChannel(byte[] path,
            Set<? extends OpenOption> options,
            FileAttribute<?>... attrs)
            throws IOException {
        checkOptions(options);
        if (options.contains(StandardOpenOption.WRITE)
                || options.contains(StandardOpenOption.APPEND)) {
            throw readOnly();
        }

        NodeAndImage ni = checkResource(path);
        byte[] buf = ni.getResource();
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

    // Returns a FileChannel of the specified path.
    FileChannel newFileChannel(byte[] path,
            Set<? extends OpenOption> options,
            FileAttribute<?>... attrs)
            throws IOException {
        throw new UnsupportedOperationException("newFileChannel");
    }
}
