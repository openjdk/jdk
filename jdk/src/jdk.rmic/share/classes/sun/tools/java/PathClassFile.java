/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.java;

import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class is used to represent a file loaded from the class path, and
 * is represented by nio Path.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
final
class PathClassFile extends ClassFile {
    private final Path path;
    private final BasicFileAttributes attrs;

    /**
     * Constructor for instance representing a Path
     */
    public PathClassFile(Path path) {
        this.path = path;
        try {
            this.attrs = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException ioExp) {
            throw new UncheckedIOException(ioExp);
        }
    }

    @Override
    public boolean isZipped() {
        return false;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return attrs.isDirectory();
    }

    @Override
    public long lastModified() {
        return attrs.lastModifiedTime().toMillis();
    }

    @Override
    public String getPath() {
        return path.toUri().toString();
    }

    @Override
    public String getName() {
        return path.getFileName().toString();
    }

//JCOV
    @Override
    public String getAbsoluteName() {
        return path.toAbsolutePath().toUri().toString();
    }
// end JCOV

    @Override
    public long length() {
        return attrs.size();
    }

    @Override
    public String toString() {
        return path.toString();
    }
}
