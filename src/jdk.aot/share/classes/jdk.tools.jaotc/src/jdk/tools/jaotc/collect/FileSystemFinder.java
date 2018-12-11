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

import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * {@link FileVisitor} implementation to find class files recursively.
 */
public final class FileSystemFinder extends SimpleFileVisitor<Path> implements Iterable<Path> {
    private final ArrayList<Path> fileNames = new ArrayList<>();
    private final PathMatcher filter;

    public FileSystemFinder(Path combinedPath, PathMatcher filter) {
        this.filter = filter;
        try {
            Files.walkFileTree(combinedPath, this);
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Compares the glob pattern against the file name.
     */
    private void find(Path file) {
        Path name = file.getFileName();
        if (name != null && filter.matches(name)) {
            fileNames.add(file);
        }
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        find(file);
        return CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        find(dir);
        return CONTINUE;
    }

    @Override
    public Iterator<Path> iterator() {
        return fileNames.iterator();
    }
}
