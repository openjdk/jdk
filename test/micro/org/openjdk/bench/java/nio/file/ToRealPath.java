/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.nio.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class ToRealPath {

    static Random RND = new Random(17_000_126);

    static final String NAME = "RealPath";
    static final int LEN = NAME.length();

    Path root;
    Path[] files;

    @Setup
    public void init() throws IOException {
        // root the test files at CWD/NAME
        root = Path.of(System.getProperty("user.dir")).resolve(NAME);

        // populate files array
        StringBuilder sb = new StringBuilder();
        files = new Path[100];
        for (int i = 0; i < files.length; i++) {
            // create directories up to a depth of 9, inclusive
            sb.setLength(0);
            int depth = RND.nextInt(10);
            for (int j = 0; j < depth; j++) {
                sb.append("dir");
                sb.append(j);
                sb.append(File.separatorChar);
            }
            Path dir = root.resolve(sb.toString());
            Files.createDirectories(dir);

            // set the file prefix with random case conversion
            String prefix;
            if (RND.nextBoolean()) {
                sb.setLength(0);
                for (int k = 0; k < LEN; k++) {
                    char c = NAME.charAt(k);
                    sb.append(RND.nextBoolean()
                              ? Character.toLowerCase(c)
                              : Character.toUpperCase(c));
                }
                prefix = sb.append(i).toString();
            } else {
                prefix = NAME + i;
            }

            // create the file
            Path tmpFile = Files.createTempFile(dir, prefix, ".tmp");

            // set the array path to a version with a lower case name
            String tmpName = tmpFile.getFileName().toString().toLowerCase();
            files[i] = tmpFile.getParent().resolve(tmpName);
        }
    }

    @TearDown
    public void cleanup() throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file,
                                                 BasicFileAttributes attrs)
                    throws IOException
                {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir,
                                                          IOException e)
                    throws IOException
                {
                    if (e == null) {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        // directory iteration failed
                        throw e;
                    }
                }
            });
    }

    @Benchmark
    public Path noFollowLinks() throws IOException {
        int i = RND.nextInt(0, files.length);
        return files[i].toRealPath(LinkOption.NOFOLLOW_LINKS);
    }
}
