/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.jdeps;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * ClassPath for Java SE and JDK
 */
class PlatformClassPath {
    private final static List<Archive> javaHomeArchives = init();

    static List<Archive> getArchives() {
        return javaHomeArchives;
    }

    private static List<Archive> init() {
        List<Archive> result = new ArrayList<>();
        Path home = Paths.get(System.getProperty("java.home"));
        try {
            if (home.endsWith("jre")) {
                // jar files in <javahome>/jre/lib
                result.addAll(addJarFiles(home.resolve("lib")));
            } else if (Files.exists(home.resolve("lib"))) {
                // either a JRE or a jdk build image
                Path classes = home.resolve("classes");
                if (Files.isDirectory(classes)) {
                    // jdk build outputdir
                    result.add(new JDKArchive(classes, ClassFileReader.newInstance(classes)));
                }
                // add other JAR files
                result.addAll(addJarFiles(home.resolve("lib")));
            } else {
                throw new RuntimeException("\"" + home + "\" not a JDK home");
            }
            return result;
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private static List<Archive> addJarFiles(final Path root) throws IOException {
        final List<Archive> result = new ArrayList<>();
        final Path ext = root.resolve("ext");
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException
            {
                if (dir.equals(root) || dir.equals(ext)) {
                    return FileVisitResult.CONTINUE;
                } else {
                    // skip other cobundled JAR files
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs)
                throws IOException
            {
                String fn = p.getFileName().toString();
                if (fn.endsWith(".jar")) {
                    // JDK may cobundle with JavaFX that doesn't belong to any profile
                    // Treat jfxrt.jar as regular Archive
                    result.add(fn.equals("jfxrt.jar")
                        ? new Archive(p, ClassFileReader.newInstance(p))
                        : new JDKArchive(p, ClassFileReader.newInstance(p)));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    /**
     * A JDK archive is part of the JDK containing the Java SE API
     * or implementation classes (i.e. JDK internal API)
     */
    static class JDKArchive extends Archive {
        JDKArchive(Path p, ClassFileReader reader) {
            super(p, reader);
        }
    }
}
