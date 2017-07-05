/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.nio.zipfs;

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
import java.util.zip.ZipError;
import java.util.concurrent.ExecutorService;

/*
 *
 * @author  Xueming Shen, Rajendra Gutupalli, Jaya Hangal
 */

public class ZipFileSystemProvider extends FileSystemProvider {


    private final Map<Path, ZipFileSystem> filesystems = new HashMap<>();

    public ZipFileSystemProvider() {}

    @Override
    public String getScheme() {
        return "jar";
    }

    protected Path uriToPath(URI uri) {
        String scheme = uri.getScheme();
        if ((scheme == null) || !scheme.equalsIgnoreCase(getScheme())) {
            throw new IllegalArgumentException("URI scheme is not '" + getScheme() + "'");
        }
        try {
            // only support legacy JAR URL syntax  jar:{uri}!/{entry} for now
            String spec = uri.getRawSchemeSpecificPart();
            int sep = spec.indexOf("!/");
            if (sep != -1)
                spec = spec.substring(0, sep);
            return Paths.get(new URI(spec)).toAbsolutePath();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private boolean ensureFile(Path path) {
        try {
            BasicFileAttributes attrs =
                Files.readAttributes(path, BasicFileAttributes.class);
            if (!attrs.isRegularFile())
                throw new UnsupportedOperationException();
            return true;
        } catch (IOException ioe) {
            return false;
        }
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env)
        throws IOException
    {
        Path path = uriToPath(uri);
        synchronized(filesystems) {
            Path realPath = null;
            if (ensureFile(path)) {
                realPath = path.toRealPath();
                if (filesystems.containsKey(realPath))
                    throw new FileSystemAlreadyExistsException();
            }
            ZipFileSystem zipfs = null;
            try {
                zipfs = new ZipFileSystem(this, path, env);
            } catch (ZipError ze) {
                String pname = path.toString();
                if (pname.endsWith(".zip") || pname.endsWith(".jar"))
                    throw ze;
                // assume NOT a zip/jar file
                throw new UnsupportedOperationException();
            }
            filesystems.put(realPath, zipfs);
            return zipfs;
        }
    }

    @Override
    public FileSystem newFileSystem(Path path, Map<String, ?> env)
        throws IOException
    {
        if (path.getFileSystem() != FileSystems.getDefault()) {
            throw new UnsupportedOperationException();
        }
        ensureFile(path);
        try {
            return new ZipFileSystem(this, path, env);
        } catch (ZipError ze) {
            String pname = path.toString();
            if (pname.endsWith(".zip") || pname.endsWith(".jar"))
                throw ze;
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Path getPath(URI uri) {

        String spec = uri.getSchemeSpecificPart();
        int sep = spec.indexOf("!/");
        if (sep == -1)
            throw new IllegalArgumentException("URI: "
                + uri
                + " does not contain path info ex. jar:file:/c:/foo.zip!/BAR");
        return getFileSystem(uri).getPath(spec.substring(sep + 1));
    }


    @Override
    public FileSystem getFileSystem(URI uri) {
        synchronized (filesystems) {
            ZipFileSystem zipfs = null;
            try {
                zipfs = filesystems.get(uriToPath(uri).toRealPath());
            } catch (IOException x) {
                // ignore the ioe from toRealPath(), return FSNFE
            }
            if (zipfs == null)
                throw new FileSystemNotFoundException();
            return zipfs;
        }
    }

    // Checks that the given file is a UnixPath
    static final ZipPath toZipPath(Path path) {
        if (path == null)
            throw new NullPointerException();
        if (!(path instanceof ZipPath))
            throw new ProviderMismatchException();
        return (ZipPath)path;
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        toZipPath(path).checkAccess(modes);
    }

    @Override
    public void copy(Path src, Path target, CopyOption... options)
        throws IOException
    {
        toZipPath(src).copy(toZipPath(target), options);
    }

    @Override
    public void createDirectory(Path path, FileAttribute<?>... attrs)
        throws IOException
    {
        toZipPath(path).createDirectory(attrs);
    }

    @Override
    public final void delete(Path path) throws IOException {
        toZipPath(path).delete();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V
        getFileAttributeView(Path path, Class<V> type, LinkOption... options)
    {
        return ZipFileAttributeView.get(toZipPath(path), type);
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return toZipPath(path).getFileStore();
    }

    @Override
    public boolean isHidden(Path path) {
        return toZipPath(path).isHidden();
    }

    @Override
    public boolean isSameFile(Path path, Path other) throws IOException {
        return toZipPath(path).isSameFile(other);
    }

    @Override
    public void move(Path src, Path target, CopyOption... options)
        throws IOException
    {
        toZipPath(src).move(toZipPath(target), options);
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
        return toZipPath(path).newByteChannel(options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(
        Path path, Filter<? super Path> filter) throws IOException
    {
        return toZipPath(path).newDirectoryStream(filter);
    }

    @Override
    public FileChannel newFileChannel(Path path,
                                      Set<? extends OpenOption> options,
                                      FileAttribute<?>... attrs)
        throws IOException
    {
        return toZipPath(path).newFileChannel(options, attrs);
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options)
        throws IOException
    {
        return toZipPath(path).newInputStream(options);
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options)
        throws IOException
    {
        return toZipPath(path).newOutputStream(options);
    }

    @Override
    public <A extends BasicFileAttributes> A
        readAttributes(Path path, Class<A> type, LinkOption... options)
        throws IOException
    {
        if (type == BasicFileAttributes.class || type == ZipFileAttributes.class)
            return (A)toZipPath(path).getAttributes();
        return null;
    }

    @Override
    public Map<String, Object>
        readAttributes(Path path, String attribute, LinkOption... options)
        throws IOException
    {
        return toZipPath(path).readAttributes(attribute, options);
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void setAttribute(Path path, String attribute,
                             Object value, LinkOption... options)
        throws IOException
    {
        toZipPath(path).setAttribute(attribute, value, options);
    }

    //////////////////////////////////////////////////////////////
    void removeFileSystem(Path zfpath, ZipFileSystem zfs) throws IOException {
        synchronized (filesystems) {
            zfpath = zfpath.toRealPath();
            if (filesystems.get(zfpath) == zfs)
                filesystems.remove(zfpath);
        }
    }
}
