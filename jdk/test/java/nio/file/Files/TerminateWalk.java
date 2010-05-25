/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.IOException;
import java.util.*;

/**
 * Unit test for Files.walkFileTree to test TERMINATE return value
 */

public class TerminateWalk {

    static final Random rand = new Random();
    static boolean terminated;

    static FileVisitResult maybeTerminate() {
        if (terminated)
            throw new RuntimeException("FileVisitor invoked after termination");
        if (rand.nextInt(10) == 0) {
            terminated = true;
            return FileVisitResult.TERMINATE;
        } else {
            return FileVisitResult.CONTINUE;
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args[0]);

        Files.walkFileTree(dir, new FileVisitor<Path>() {
            public FileVisitResult preVisitDirectory(Path dir) {
                return maybeTerminate();
            }
            public FileVisitResult preVisitDirectoryFailed(Path dir, IOException exc) {
                return maybeTerminate();
            }
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                return maybeTerminate();
            }
            public FileVisitResult postVisitDirectory(Path dir, IOException x) {
                return maybeTerminate();
            }
            public FileVisitResult visitFileFailed(Path file, IOException x) {
                return maybeTerminate();
            }
        });
    }
}
