/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.ClosedFileSystemException;
import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
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
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import static java.util.stream.Collectors.toList;
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

        byte[] root = new byte[] { '/' };
        rootPath = new JrtPath(this, root);
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

            // close all image reader and null out
            bootImage.close();
            bootImage = null;
            extImage.close();
            extImage = null;
            appImage.close();
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

    private NodeAndImage lookup(byte[] path) {
        Node node = bootImage.findNode(path);
        ImageReader image = bootImage;
        if (node == null) {
            node = extImage.findNode(path);
            image = extImage;
        }
        if (node == null) {
            node = appImage.findNode(path);
            image = appImage;
        }
        return node != null? new NodeAndImage(node, image) : null;
    }

    private NodeAndImage lookupSymbolic(byte[] path) {
        for (int i = 1; i < path.length; i++) {
            if (path[i] == (byte)'/') {
                byte[] prefix = Arrays.copyOfRange(path, 0, i);
                NodeAndImage ni = lookup(prefix);
                if (ni == null) {
                    break;
                }

                if (ni.node.isLink()) {
                    Node link = ni.node.resolveLink(true);
                    // resolved symbolic path concatenated to the rest of the path
                    UTF8String resPath = link.getName().concat(new UTF8String(path, i));
                    byte[] resPathBytes = resPath.getBytesCopy();
                    ni = lookup(resPathBytes);
                    return ni != null? ni : lookupSymbolic(resPathBytes);
                }
            }
        }

        return null;
    }

    private NodeAndImage findNode(byte[] path) throws IOException {
        NodeAndImage ni = lookup(path);
        if (ni == null) {
            ni = lookupSymbolic(path);
            if (ni == null) {
                throw new NoSuchFileException(getString(path));
            }
        }
        return ni;
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

    // package private helpers
    JrtFileAttributes getFileAttributes(byte[] path, LinkOption... options)
            throws IOException {
        NodeAndImage ni = checkNode(path);
        if (ni.node.isLink() && followLinks(options)) {
            return new JrtFileAttributes(ni.node.resolveLink(true));
        }
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

    boolean isDirectory(byte[] path, boolean resolveLinks)
            throws IOException {
        ensureOpen();
        NodeAndImage ni = checkNode(path);
        return resolveLinks && ni.node.isLink()?
            ni.node.resolveLink(true).isDirectory() :
            ni.node.isDirectory();
    }

    JrtPath toJrtPath(String path) {
        return toJrtPath(getBytes(path));
    }

    JrtPath toJrtPath(byte[] path) {
        return new JrtPath(this, path);
    }

    boolean isSameFile(JrtPath p1, JrtPath p2) throws IOException {
        NodeAndImage n1 = findNode(p1.getName());
        NodeAndImage n2 = findNode(p2.getName());
        return n1.node.equals(n2.node);
    }

    boolean isLink(JrtPath jrtPath) throws IOException {
        return findNode(jrtPath.getName()).node.isLink();
    }

    JrtPath resolveLink(JrtPath jrtPath) throws IOException {
        NodeAndImage ni = findNode(jrtPath.getName());
        if (ni.node.isLink()) {
            Node node = ni.node.resolveLink();
            return toJrtPath(node.getName().getBytesCopy());
        }

        return jrtPath;
    }

    private Map<UTF8String, List<Node>> packagesTreeChildren = new ConcurrentHashMap<>();

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
        Node node = ni.node.resolveLink(true);

        if (!node.isDirectory()) {
            throw new NotDirectoryException(getString(path));
        }

        if (node.isRootDir()) {
            return rootDirIterator(path, childPrefix);
        } else if (node.isModulesDir()) {
            return modulesDirIterator(path, childPrefix);
        } else if (node.isPackagesDir()) {
            return packagesDirIterator(path, childPrefix);
        } else if (node.getNameString().startsWith("/packages/")) {
            if (ni.image != appImage) {
                UTF8String name = node.getName();
                List<Node> children = packagesTreeChildren.get(name);
                if (children != null) {
                    return nodesToIterator(toJrtPath(path), childPrefix, children);
                }

                children = new ArrayList<>();
                children.addAll(node.getChildren());
                Node tmpNode = null;
                // found in boot
                if (ni.image == bootImage) {
                    tmpNode = extImage.findNode(name);
                    if (tmpNode != null) {
                        children.addAll(tmpNode.getChildren());
                    }
                }

                // found in ext
                tmpNode = appImage.findNode(name);
                if (tmpNode != null) {
                    children.addAll(tmpNode.getChildren());
                }

                packagesTreeChildren.put(name, children);
                return nodesToIterator(toJrtPath(path), childPrefix, children);
            }
        }

        return nodesToIterator(toJrtPath(path), childPrefix, node.getChildren());
    }

    private Iterator<Path> nodesToIterator(Path path, String childPrefix, List<Node> childNodes) {
        Function<Node, Path> f = childPrefix == null
                ? child -> toJrtPath(child.getNameString())
                : child -> toJrtPath(childPrefix + child.getNameString().substring(1));
         return childNodes.stream().map(f).collect(toList()).iterator();
    }

    private void addRootDirContent(List<Node> children) {
        for (Node child : children) {
            if (!(child.isModulesDir() || child.isPackagesDir())) {
                rootChildren.add(child);
            }
        }
    }

    private List<Node> rootChildren;
    private synchronized void initRootChildren(byte[] path) {
        if (rootChildren == null) {
            rootChildren = new ArrayList<>();
            rootChildren.addAll(bootImage.findNode(path).getChildren());
            addRootDirContent(extImage.findNode(path).getChildren());
            addRootDirContent(appImage.findNode(path).getChildren());
        }
    }

    private Iterator<Path> rootDirIterator(byte[] path, String childPrefix) throws IOException {
        initRootChildren(path);
        return nodesToIterator(rootPath, childPrefix, rootChildren);
    }

    private List<Node> modulesChildren;
    private synchronized void initModulesChildren(byte[] path) {
        if (modulesChildren == null) {
            modulesChildren = new ArrayList<>();
            modulesChildren.addAll(bootImage.findNode(path).getChildren());
            modulesChildren.addAll(appImage.findNode(path).getChildren());
            modulesChildren.addAll(extImage.findNode(path).getChildren());
        }
    }

    private Iterator<Path> modulesDirIterator(byte[] path, String childPrefix) throws IOException {
        initModulesChildren(path);
        return nodesToIterator(new JrtPath(this, path), childPrefix, modulesChildren);
    }

    private List<Node> packagesChildren;
    private synchronized void initPackagesChildren(byte[] path) {
        if (packagesChildren == null) {
            packagesChildren = new ArrayList<>();
            packagesChildren.addAll(bootImage.findNode(path).getChildren());
            packagesChildren.addAll(extImage.findNode(path).getChildren());
            packagesChildren.addAll(appImage.findNode(path).getChildren());
        }
    }
    private Iterator<Path> packagesDirIterator(byte[] path, String childPrefix) throws IOException {
        initPackagesChildren(path);
        return nodesToIterator(new JrtPath(this, path), childPrefix, packagesChildren);
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
