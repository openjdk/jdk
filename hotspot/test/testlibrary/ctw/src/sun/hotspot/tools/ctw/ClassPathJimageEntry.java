/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jimage.ImageReader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * Handler for jimage-files containing classes to compile.
 */
public class ClassPathJimageEntry extends PathHandler {
    public ClassPathJimageEntry(Path root, Executor executor) {
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
        CompileTheWorld.OUT.println("# jimage: " + root);
        if (!Files.exists(root)) {
            return;
        }
        try {
            ImageReader reader = ImageReader.open(root);
            Arrays.stream(reader.getEntryNames())
                    .filter(name -> name.endsWith(".class"))
                    .filter(name -> !name.endsWith("module-info.class"))
                    .map(Utils::fileNameToClassName)
                    .forEach(this::processClass);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
