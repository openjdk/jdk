/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * Invokes Files.walkFileTree to traverse a file tree and prints
 * each of the directories and files. The -follow option causes symbolic
 * links to be followed and the -printCycles option will print links
 * where the target of the link is an ancestor directory.
 */

public class PrintFileTree {

    public static void main(String[] args) throws Exception {
        boolean followLinks = false;
        boolean printCycles = false;
        int i = 0;
        while (i < (args.length-1)) {
            switch (args[i]) {
                case "-follow"      : followLinks = true; break;
                case "-printCycles" : printCycles = true;  break;
                default:
                    throw new RuntimeException(args[i] + " not recognized");
            }
            i++;
        }
        Path dir = Paths.get(args[i]);

        Set<FileVisitOption> options = new HashSet<FileVisitOption>();
        if (followLinks)
            options.add(FileVisitOption.FOLLOW_LINKS);

        final boolean reportCycles = printCycles;
        Files.walkFileTree(dir, options, Integer.MAX_VALUE, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                System.out.println(dir);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isDirectory() || reportCycles)
                    System.out.println(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                throws IOException
            {
                if (exc != null)
                    throw exc;
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc)
                throws IOException
            {
                if (reportCycles && (exc instanceof FileSystemLoopException)) {
                    System.out.println(file);
                    return FileVisitResult.CONTINUE;
                }
                throw exc;
            }
        });
    }
}
