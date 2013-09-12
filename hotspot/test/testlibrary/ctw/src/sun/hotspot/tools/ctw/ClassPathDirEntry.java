/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.hotspot.tools.ctw;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.concurrent.Executor;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;

/**
 * * Handler for dirs containing classes to compile.
 * @author igor.ignatyev@oracle.com
 */
public class ClassPathDirEntry extends PathHandler {

    private final int rootLength = root.toString().length();

    public ClassPathDirEntry(Path root, Executor executor) {
        super(root, executor);
        try {
            URL url = root.toUri().toURL();
            setLoader(new URLClassLoader(new URL[]{url}));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void process() {
        System.out.println("# dir: " + root);
        if (!Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE, new CompileFileVisitor());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void processFile(Path file) {
        if (Utils.isClassFile(file.toString())) {
            processClass(pathToClassName(file));
        }
    }

    private String pathToClassName(Path file) {
        String fileString;
        if (root == file) {
            fileString = file.normalize().toString();
        } else {
            fileString = file.normalize().toString().substring(rootLength + 1);
        }
        return Utils.fileNameToClassName(fileString);
    }

    private class CompileFileVisitor extends SimpleFileVisitor<Path> {

        private final Set<Path> ready = new HashSet<>();

        @Override
        public FileVisitResult preVisitDirectory(Path dir,
                BasicFileAttributes attrs) throws IOException {
            if (ready.contains(dir)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            ready.add(dir);
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file,
                BasicFileAttributes attrs) throws IOException {
            if (!ready.contains(file)) {
                processFile(file);
            }
            return isFinished() ? FileVisitResult.TERMINATE
                    : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file,
                IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }
    }
}

