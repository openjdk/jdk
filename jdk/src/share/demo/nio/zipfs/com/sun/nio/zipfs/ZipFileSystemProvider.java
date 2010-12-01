/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.nio.zipfs;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileRef;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/*
 *
 * @author  Xueming Shen, Rajendra Gutupalli, Jaya Hangal
 */

public class ZipFileSystemProvider extends FileSystemProvider {


    private final Map<Path, ZipFileSystem> filesystems = new HashMap<>();

    public ZipFileSystemProvider() {}

    @Override
    public String getScheme() {
        return "zip";
    }

    protected Path uriToPath(URI uri) {
        String scheme = uri.getScheme();
        if ((scheme == null) || !scheme.equalsIgnoreCase(getScheme())) {
            throw new IllegalArgumentException("URI scheme is not '" + getScheme() + "'");
        }
        try {
            return Paths.get(new URI("file", uri.getHost(), uri.getPath(), null))
                        .toAbsolutePath();
        } catch (URISyntaxException e) {
            throw new AssertionError(e); //never thrown
        }
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env)
        throws IOException
    {
        return newFileSystem(uriToPath(uri), env);
    }

    @Override
    public FileSystem newFileSystem(FileRef file, Map<String, ?> env)
        throws IOException
    {
        if (!(file instanceof Path))
            throw new UnsupportedOperationException();
        Path path = (Path)file;
        if (!path.toUri().getScheme().equalsIgnoreCase("file")) {
            throw new UnsupportedOperationException();
        }
        return newFileSystem(path, env);
    }

    private FileSystem newFileSystem(Path path, Map<String, ?> env)
        throws IOException
    {
        synchronized(filesystems) {
            Path realPath = null;
            if (path.exists()) {
                realPath = path.toRealPath(true);
                if (filesystems.containsKey(realPath))
                    throw new FileSystemAlreadyExistsException();
            }
            ZipFileSystem zipfs = new ZipFileSystem(this, path, env);
            if (realPath == null)
                realPath = path.toRealPath(true);
            filesystems.put(realPath, zipfs);
            return zipfs;
        }
    }

    @Override
    public Path getPath(URI uri) {
        FileSystem fs = getFileSystem(uri);
        String fragment = uri.getFragment();
        if (fragment == null) {
            throw new IllegalArgumentException("URI: "
                + uri
                + " does not contain path fragment ex. zip:///c:/foo.zip#/BAR");
        }
        return fs.getPath(fragment);
    }

    @Override
    public FileChannel newFileChannel(Path path,
            Set<? extends OpenOption> options,
            FileAttribute<?>... attrs)
            throws IOException
    {
        if (path == null)
            throw new NullPointerException("path is null");
        if (path instanceof ZipPath)
            return ((ZipPath)path).newFileChannel(options, attrs);
        throw new ProviderMismatchException();
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        synchronized (filesystems) {
            ZipFileSystem zipfs = null;
            try {
                zipfs = filesystems.get(uriToPath(uri).toRealPath(true));
            } catch (IOException x) {
                // ignore the ioe from toRealPath(), return FSNFE
            }
            if (zipfs == null)
                throw new FileSystemNotFoundException();
            return zipfs;
        }
    }

    void removeFileSystem(Path zfpath) throws IOException {
        synchronized (filesystems) {
            filesystems.remove(zfpath.toRealPath(true));
        }
    }
}
