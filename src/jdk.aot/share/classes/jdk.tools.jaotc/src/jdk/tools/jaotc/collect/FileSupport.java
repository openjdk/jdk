/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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



package jdk.tools.jaotc.collect;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public class FileSupport {
    public boolean exists(Path path) {
        return Files.exists(path);
    }

    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    private static FileSystem makeJarFileSystem(Path path) {
        try {
            return FileSystems.newFileSystem(makeJarFileURI(path), new HashMap<>());
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    private static URI makeJarFileURI(Path path) {
        try {
            String name = path.toAbsolutePath().toString();
            name = name.replace('\\', '/');
            return new URI("jar:file:///" + name + "!/");
        } catch (URISyntaxException e) {
            throw new InternalError(e);
        }
    }

    public ClassLoader createClassLoader(Path path, ClassLoader parent) {
        try {
            return URLClassLoader.newInstance(buildUrls(path), parent);
        } catch (MalformedURLException e) {
            throw new InternalError(e);
        }
    }

    public ClassLoader createClassLoader(Path path) throws MalformedURLException {
        return URLClassLoader.newInstance(buildUrls(path));
    }

    private static URL[] buildUrls(Path path) throws MalformedURLException {
        return new URL[]{path.toUri().toURL()};
    }

    public Path getJarFileSystemRoot(Path jarFile) {
        FileSystem fileSystem = makeJarFileSystem(jarFile);
        return fileSystem.getPath("/");
    }

    public boolean isAbsolute(Path entry) {
        return entry.isAbsolute();
    }

    public Path getSubDirectory(FileSystem fileSystem, Path root, Path path) throws IOException {
        DirectoryStream<Path> paths = fileSystem.provider().newDirectoryStream(root, null);
        for (Path entry : paths) {
            Path relative = root.relativize(entry);
            if (relative.equals(path)) {
                return entry;
            }
        }
        return null;
    }
}
