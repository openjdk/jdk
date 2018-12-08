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

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SearchPath {
    private final List<Path> searchPaths = new ArrayList<>();
    private final FileSupport fileSupport;

    public SearchPath() {
        this(new FileSupport());
    }

    public SearchPath(FileSupport fileSupport) {
        this.fileSupport = fileSupport;
    }

    public Path find(FileSystem fileSystem, Path entry, String... defaults) {
        if (isAbsolute(entry)) {
            if (exists(entry)) {
                return entry;
            }
            return null;
        }

        if (exists(entry)) {
            return entry;
        }

        for (String searchPath : defaults) {
            Path newPath = fileSystem.getPath(searchPath, entry.toString());
            if (exists(newPath)) {
                return newPath;
            }
        }

        for (Path searchPath : searchPaths) {
            Path newPath = fileSystem.getPath(searchPath.toString(), entry.toString());
            if (exists(newPath)) {
                return newPath;
            }
        }

        return null;
    }

    private boolean isAbsolute(Path entry) {
        return fileSupport.isAbsolute(entry);
    }

    private boolean exists(Path entry) {
        return fileSupport.exists(entry);
    }

    public void add(String... paths) {
        for (String name : paths) {
            Path path = Paths.get(name);
            searchPaths.add(path);
        }
    }
}
