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

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * This class is used to represent a file loaded from the class path, and
 * is a regular file.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
final
class FileClassFile extends ClassFile {
    private final File file;

    /**
     * Constructor for instance representing a regular file
     */
    public FileClassFile(File file) {
        this.file = file;
    }

    @Override
    public boolean isZipped() {
        return false;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public long lastModified() {
        return file.lastModified();
    }

    @Override
    public String getPath() {
        return file.getPath();
    }

    @Override
    public String getName() {
        return file.getName();
    }

//JCOV
    @Override
    public String getAbsoluteName() {
        String absoluteName;
        try {
            absoluteName = file.getCanonicalPath();
        } catch (IOException e) {
            absoluteName = file.getAbsolutePath();
        }
        return absoluteName;
    }
// end JCOV

    @Override
    public long length() {
        return file.length();
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
