/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WriteablePath {
    private final Path path;
    private final Path real;

    public WriteablePath(Path path) throws IOException {
        // verify that the path is writeable
        if (Files.exists(path) && !Files.isWritable(path)) {
            // throw same type of exception as FileOutputStream
            // constructor, if file can't be opened.
            throw new FileNotFoundException("Could not write to file: " + path.toAbsolutePath());
        }
        // will throw if non-writeable
        BufferedWriter fw = Files.newBufferedWriter(path);
        fw.close();
        this.path = path;
        this.real = path.toRealPath();
    }

    public Path getPath() {
        return path;
    }

    public Path getReal() {
        return real;
    }

    public String getRealPathText() {
        return real.toString();
    }
}
