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
import java.util.*;
import java.util.jar.*;
import java.util.concurrent.Executor;

import java.io.*;
import java.nio.file.*;

/**
 * Handler for jar-files containing classes to compile.
 */
public class ClassPathJarEntry extends PathHandler {

    public ClassPathJarEntry(Path root, Executor executor) {
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
        CompileTheWorld.OUT.println("# jar: " + root);
        if (!Files.exists(root)) {
            return;
        }
        try {
            JarFile jarFile = new JarFile(root.toFile());
            JarEntry entry;
            for (Enumeration<JarEntry> e = jarFile.entries();
                    e.hasMoreElements(); ) {
                entry = e.nextElement();
                processJarEntry(entry);
                if (isFinished()) {
                    return;
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

     private void processJarEntry(JarEntry entry) {
        String filename = entry.getName();
        if (Utils.isClassFile(filename)) {
            processClass(Utils.fileNameToClassName(filename));
        }
    }
}

