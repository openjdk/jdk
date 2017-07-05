/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jimage;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import jdk.tools.jlink.internal.ImageFileCreator;
import jdk.tools.jlink.internal.Archive;
import jdk.tools.jlink.internal.ImagePluginStack;
import jdk.tools.jlink.internal.DirArchive;
/**
 *
 * Support for extracted image.
 */
public final class ExtractedImage {

    private Set<Archive> archives = new HashSet<>();
    private final ImagePluginStack plugins;

    ExtractedImage(Path dirPath, ImagePluginStack plugins, PrintWriter log,
            boolean verbose) throws IOException {
        if (!Files.isDirectory(dirPath)) {
            throw new IOException("Not a directory");
        }
        Consumer<String> cons = (String t) -> {
            if (verbose) {
                log.println(t);
            }
        };
        this.plugins = plugins;
        Files.walk(dirPath, 1).forEach((p) -> {
            if (!dirPath.equals(p)) {
                if (Files.isDirectory(p)) {
                    Archive a = new DirArchive(p, cons);
                    archives.add(a);
                }
            }
        });
        archives = Collections.unmodifiableSet(archives);
    }

    void recreateJImage(Path path) throws IOException {
        ImageFileCreator.recreateJimage(path, archives, plugins);
    }

    private static String getPathName(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }
}
