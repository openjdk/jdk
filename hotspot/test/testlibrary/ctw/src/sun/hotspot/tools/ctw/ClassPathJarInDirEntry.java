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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * Handler for dirs containing jar-files with classes to compile.
 *
 * @author igor.ignatyev@oracle.com
 */
public class ClassPathJarInDirEntry extends PathHandler {

    public ClassPathJarInDirEntry(Path root, Executor executor) {
        super(root, executor);
    }

    @Override
    public void process() {
        System.out.println("# jar_in_dir: " + root);
        if (!Files.exists(root)) {
            return;
        }
        try (DirectoryStream<Path> ds
                = Files.newDirectoryStream(root, "*.jar")) {
            for (Path p : ds) {
                new ClassPathJarEntry(p, executor).process();
                if (isFinished()) {
                    return;
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}

