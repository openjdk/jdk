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
import java.util.zip.*;

/**
 * This class is used to represent a file loaded from the class path, and
 * is a zip file entry.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
final
class ZipClassFile extends ClassFile {
    private final ZipFile zipFile;
    private final ZipEntry zipEntry;

    /**
     * Constructor for instance representing a zip file entry
     */
    public ZipClassFile(ZipFile zf, ZipEntry ze) {
        this.zipFile = zf;
        this.zipEntry = ze;
    }

    @Override
    public boolean isZipped() {
        return true;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            return zipFile.getInputStream(zipEntry);
        } catch (ZipException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return zipEntry.getName().endsWith("/");
    }

    @Override
    public long lastModified() {
        return zipEntry.getTime();
    }

    @Override
    public String getPath() {
        return zipFile.getName() + "(" + zipEntry.getName() + ")";
    }

    @Override
    public String getName() {
        return zipEntry.getName();
    }

//JCOV
    @Override
    public String getAbsoluteName() {
        return zipFile.getName() + "(" + zipEntry.getName() + ")";
    }
// end JCOV

    @Override
    public long length() {
        return zipEntry.getSize();
    }

    @Override
    public String toString() {
        return zipEntry.toString();
    }
}
