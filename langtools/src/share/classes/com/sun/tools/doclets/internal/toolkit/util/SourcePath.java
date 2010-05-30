/*
 * Copyright (c) 1998, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

import java.io.File;

/**
 * This class is used to represent a source path which can contain only
 * directories no zip files. If a zip file is specified in the command line it
 * will not get reflected in the SourcePath.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Atul M Dambalkar
 */
public
    class SourcePath {
    private final char dirSeparator = File.pathSeparatorChar;

    /**
     * The original class path string
     */
    private String pathstr;

    /**
     * List of source path entries. Each entry is a directory.
     */
    private File[] sourcePath;


    /**
     * Build a source path from the specified path string on the command line.
     */
    public SourcePath(String pathstr) {
        init(pathstr);
    }

    /**
     * Build a default source path from the path strings specified by
     * the properties env.class.path.
     */
    public SourcePath() {
        init(System.getProperty("env.class.path"));
    }

    /**
     * Initialize the SourcePath File array, which will contain only the
     * directory names from the given path string.
     *
     * @param pathstr Path String.
     */
    private void init(String pathstr) {
        if (pathstr == null ||  pathstr.length() == 0) {
            pathstr = ".";
        }

        int noOfFileSep = 0;
        int index = 0;
        this.pathstr = pathstr; // Save original class path string

        // Count the number of path separators
        while ((index = pathstr.indexOf(dirSeparator, index)) != -1) {
            noOfFileSep++;
            index++;
        }
        // Build the source path
        File[] tempPath = new File[noOfFileSep + 1];
        int tempPathIndex = 0;
        int len = pathstr.length();
        int sepPos = 0;
        for (index = 0; index < len; index = sepPos + 1) {
            sepPos = pathstr.indexOf(dirSeparator, index);
            if (sepPos < 0) {
                sepPos = len;
            }
            File file = new File(pathstr.substring(index, sepPos));
            if (file.isDirectory()) {
                tempPath[tempPathIndex++] = file;
            } // if it is really a file, ignore it.
        }
        sourcePath = new File[tempPathIndex];
        System.arraycopy((Object)tempPath, 0, (Object)sourcePath,
                         0, tempPathIndex);
    }

    /**
     * Find the specified directory in the source path.
     *
     * @param name Name of the directory to be searched for in the source path.
     * @return File Return the directory if found else return null.
     */
    public File getDirectory(String name) {
        for (int i = 0; i < sourcePath.length; i++) {
            File directoryNeeded = new File(sourcePath[i], name);
            if (directoryNeeded.isDirectory()) {
                return directoryNeeded;
            }
        }
        return null;
    }

    /**
     * Return original source path string.
     */
    public String toString() {
        return pathstr;
    }
}
