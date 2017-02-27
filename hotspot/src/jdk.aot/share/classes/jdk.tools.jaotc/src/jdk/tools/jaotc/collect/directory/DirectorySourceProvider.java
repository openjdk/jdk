/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jaotc.collect.directory;

import jdk.tools.jaotc.collect.ClassSource;
import jdk.tools.jaotc.collect.FileSupport;
import jdk.tools.jaotc.collect.SearchPath;
import jdk.tools.jaotc.collect.SourceProvider;

import java.net.MalformedURLException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class DirectorySourceProvider implements SourceProvider {
    private final FileSupport fileSupport;
    private final FileSystem fileSystem;
    public final static String TYPE = "directory";

    public DirectorySourceProvider(FileSupport fileSupport) {
        this.fileSupport = fileSupport;
        fileSystem = FileSystems.getDefault();
    }

    @Override
    public ClassSource findSource(String name, SearchPath searchPath) {
        Path directoryPath = fileSystem.getPath(name);

        if (!fileSupport.exists(directoryPath)) {
            return null;
        }
        if (!fileSupport.isDirectory(directoryPath)) {
            return null;
        }

        try {
            ClassLoader classLoader = fileSupport.createClassLoader(directoryPath);
            return new DirectorySource(directoryPath, classLoader);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    @Override
    public boolean supports(String type) {
        return TYPE.equals(type);
    }
}
