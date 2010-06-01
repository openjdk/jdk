/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.fs;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.nio.channels.*;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.io.IOException;
import java.util.*;

import sun.nio.ch.ThreadPool;

/**
 * Base implementation of FileSystemProvider
 */

public abstract class UnixFileSystemProvider
    extends FileSystemProvider
{
    private static final String USER_DIR = "user.dir";
    private final UnixFileSystem theFileSystem;

    public UnixFileSystemProvider() {
        String userDir = System.getProperty(USER_DIR);
        theFileSystem = newFileSystem(userDir);
    }

    /**
     * Constructs a new file system using the given default directory.
     */
    abstract UnixFileSystem newFileSystem(String dir);

    @Override
    public final String getScheme() {
        return "file";
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
    public final FileSystem newFileSystem(URI uri, Map<String,?> env) {
        checkUri(uri);
        throw new FileSystemAlreadyExistsException();
    }

    @Override
    public final FileSystem getFileSystem(URI uri) {
        checkUri(uri);
        return theFileSystem;
    }

    @Override
    public Path getPath(URI uri) {
        return UnixUriUtils.fromUri(theFileSystem, uri);
    }

    protected UnixPath checkPath(Path obj) {
        if (obj == null)
            throw new NullPointerException();
        if (!(obj instanceof UnixPath))
            throw new ProviderMismatchException();
        return (UnixPath)obj;
    }

    @Override
    public FileChannel newFileChannel(Path obj,
                                      Set<? extends OpenOption> options,
                                      FileAttribute<?>... attrs)
        throws IOException
    {
        UnixPath file = checkPath(obj);
        int mode = UnixFileModeAttribute
            .toUnixMode(UnixFileModeAttribute.ALL_READWRITE, attrs);
        try {
            return UnixChannelFactory.newFileChannel(file, options, mode);
        } catch (UnixException x) {
            x.rethrowAsIOException(file);
            return null;
        }
    }

    @Override
    public AsynchronousFileChannel newAsynchronousFileChannel(Path obj,
                                                              Set<? extends OpenOption> options,
                                                              ExecutorService executor,
                                                              FileAttribute<?>... attrs) throws IOException
    {
        UnixPath file = checkPath(obj);
        int mode = UnixFileModeAttribute
            .toUnixMode(UnixFileModeAttribute.ALL_READWRITE, attrs);
        ThreadPool pool = (executor == null) ? null : ThreadPool.wrap(executor, 0);
        try {
            return UnixChannelFactory
                .newAsynchronousFileChannel(file, options, mode, pool);
        } catch (UnixException x) {
            x.rethrowAsIOException(file);
            return null;
        }
    }
}
