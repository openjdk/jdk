/*
 * Copyright (c) 1995, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * can either be a regular file or a zip file entry.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class ClassFile {
    /*
     * Non-null if this represents a regular file
     */
    private File file;

    /*
     * Non-null if this represents a zip file entry
     */
    private ZipFile zipFile;
    private ZipEntry zipEntry;

    /**
     * Constructor for instance representing a regular file
     */
    public ClassFile(File file) {
        this.file = file;
    }

    /**
     * Constructor for instance representing a zip file entry
     */
    public ClassFile(ZipFile zf, ZipEntry ze) {
        this.zipFile = zf;
        this.zipEntry = ze;
    }

    /**
     * Returns true if this is zip file entry
     */
    public boolean isZipped() {
        return zipFile != null;
    }

    /**
     * Returns input stream to either regular file or zip file entry
     */
    public InputStream getInputStream() throws IOException {
        if (file != null) {
            return new FileInputStream(file);
        } else {
            try {
                return zipFile.getInputStream(zipEntry);
            } catch (ZipException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    /**
     * Returns true if file exists.
     */
    public boolean exists() {
        return file != null ? file.exists() : true;
    }

    /**
     * Returns true if this is a directory.
     */
    public boolean isDirectory() {
        return file != null ? file.isDirectory() :
                              zipEntry.getName().endsWith("/");
    }

    /**
     * Return last modification time
     */
    public long lastModified() {
        return file != null ? file.lastModified() : zipEntry.getTime();
    }

    /**
     * Get file path. The path for a zip file entry will also include
     * the zip file name.
     */
    public String getPath() {
        if (file != null) {
            return file.getPath();
        } else {
            return zipFile.getName() + "(" + zipEntry.getName() + ")";
        }
    }

    /**
     * Get name of file entry excluding directory name
     */
    public String getName() {
        return file != null ? file.getName() : zipEntry.getName();
    }

//JCOV
    /**
     * Get absolute name of file entry
     */
    public String getAbsoluteName() {
        String absoluteName;
        if (file != null) {
            try {
                absoluteName = file.getCanonicalPath();
            } catch (IOException e) {
                absoluteName = file.getAbsolutePath();
            }
        } else {
            absoluteName = zipFile.getName() + "(" + zipEntry.getName() + ")";
        }
        return absoluteName;
    }
// end JCOV

    /**
     * Get length of file
     */
    public long length() {
        return file != null ? file.length() : zipEntry.getSize();
    }

    public String toString() {
        return (file != null) ? file.toString() : zipEntry.toString();
    }
}
