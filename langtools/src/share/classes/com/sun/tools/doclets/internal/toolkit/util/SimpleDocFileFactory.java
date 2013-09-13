/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.tools.DocumentationTool;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;

import com.sun.tools.doclets.internal.toolkit.Configuration;

/**
 * Implementation of DocFileFactory that just uses java.io.File API,
 * and does not use a JavaFileManager..
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @since 1.8
 */
class SimpleDocFileFactory extends DocFileFactory {

    public SimpleDocFileFactory(Configuration configuration) {
        super(configuration);
    }

    public DocFile createFileForDirectory(String file) {
        return new SimpleDocFile(new File(file));
    }

    public DocFile createFileForInput(String file) {
        return new SimpleDocFile(new File(file));
    }

    public DocFile createFileForOutput(DocPath path) {
        return new SimpleDocFile(DocumentationTool.Location.DOCUMENTATION_OUTPUT, path);
    }

    @Override
    Iterable<DocFile> list(Location location, DocPath path) {
        if (location != StandardLocation.SOURCE_PATH)
            throw new IllegalArgumentException();

        Set<DocFile> files = new LinkedHashSet<DocFile>();
        for (String s : configuration.sourcepath.split(File.pathSeparator)) {
            if (s.isEmpty())
                continue;
            File f = new File(s);
            if (f.isDirectory()) {
                f = new File(f, path.getPath());
                if (f.exists())
                    files.add(new SimpleDocFile(f));
            }
        }
        return files;
    }

    class SimpleDocFile extends DocFile {
        private File file;

        /** Create a DocFile for a given file. */
        private SimpleDocFile(File file) {
            super(configuration);
            this.file = file;
        }

        /** Create a DocFile for a given location and relative path. */
        private SimpleDocFile(Location location, DocPath path) {
            super(configuration, location, path);
            String destDirName = configuration.destDirName;
            this.file = destDirName.isEmpty() ? new File(path.getPath())
                    : new File(destDirName, path.getPath());
        }

        /** Open an input stream for the file. */
        public InputStream openInputStream() throws FileNotFoundException {
            return new BufferedInputStream(new FileInputStream(file));
        }

        /**
         * Open an output stream for the file.
         * The file must have been created with a location of
         * {@link DocumentationTool.Location#DOCUMENTATION_OUTPUT} and a corresponding relative path.
         */
        public OutputStream openOutputStream() throws IOException, UnsupportedEncodingException {
            if (location != DocumentationTool.Location.DOCUMENTATION_OUTPUT)
                throw new IllegalStateException();

            createDirectoryForFile(file);
            return new BufferedOutputStream(new FileOutputStream(file));
        }

        /**
         * Open an writer for the file, using the encoding (if any) given in the
         * doclet configuration.
         * The file must have been created with a location of
         * {@link DocumentationTool.Location#DOCUMENTATION_OUTPUT} and a corresponding relative path.
         */
        public Writer openWriter() throws IOException, UnsupportedEncodingException {
            if (location != DocumentationTool.Location.DOCUMENTATION_OUTPUT)
                throw new IllegalStateException();

            createDirectoryForFile(file);
            FileOutputStream fos = new FileOutputStream(file);
            if (configuration.docencoding == null) {
                return new BufferedWriter(new OutputStreamWriter(fos));
            } else {
                return new BufferedWriter(new OutputStreamWriter(fos, configuration.docencoding));
            }
        }

        /** Return true if the file can be read. */
        public boolean canRead() {
            return file.canRead();
        }

        /** Return true if the file can be written. */
        public boolean canWrite() {
            return file.canRead();
        }

        /** Return true if the file exists. */
        public boolean exists() {
            return file.exists();
        }

        /** Return the base name (last component) of the file name. */
        public String getName() {
            return file.getName();
        }

        /** Return the file system path for this file. */
        public String getPath() {
            return file.getPath();
        }

        /** Return true is file has an absolute path name. */
        public boolean isAbsolute() {
            return file.isAbsolute();
        }

        /** Return true is file identifies a directory. */
        public boolean isDirectory() {
            return file.isDirectory();
        }

        /** Return true is file identifies a file. */
        public boolean isFile() {
            return file.isFile();
        }

        /** Return true if this file is the same as another. */
        public boolean isSameFile(DocFile other) {
            if (!(other instanceof SimpleDocFile))
                return false;

            try {
                return file.exists()
                        && file.getCanonicalFile().equals(((SimpleDocFile)other).file.getCanonicalFile());
            } catch (IOException e) {
                return false;
            }
        }

        /** If the file is a directory, list its contents. */
        public Iterable<DocFile> list() {
            List<DocFile> files = new ArrayList<DocFile>();
            for (File f: file.listFiles()) {
                files.add(new SimpleDocFile(f));
            }
            return files;
        }

        /** Create the file as a directory, including any parent directories. */
        public boolean mkdirs() {
            return file.mkdirs();
        }

        /**
         * Derive a new file by resolving a relative path against this file.
         * The new file will inherit the configuration and location of this file
         * If this file has a path set, the new file will have a corresponding
         * new path.
         */
        public DocFile resolve(DocPath p) {
            return resolve(p.getPath());
        }

        /**
         * Derive a new file by resolving a relative path against this file.
         * The new file will inherit the configuration and location of this file
         * If this file has a path set, the new file will have a corresponding
         * new path.
         */
        public DocFile resolve(String p) {
            if (location == null && path == null) {
                return new SimpleDocFile(new File(file, p));
            } else {
                return new SimpleDocFile(location, path.resolve(p));
            }
        }

        /**
         * Resolve a relative file against the given output location.
         * @param locn Currently, only
         * {@link DocumentationTool.Location#DOCUMENTATION_OUTPUT} is supported.
         */
        public DocFile resolveAgainst(Location locn) {
            if (locn != DocumentationTool.Location.DOCUMENTATION_OUTPUT)
                throw new IllegalArgumentException();
            return new SimpleDocFile(
                    new File(configuration.destDirName, file.getPath()));
        }

        /**
         * Given a path string create all the directories in the path. For example,
         * if the path string is "java/applet", the method will create directory
         * "java" and then "java/applet" if they don't exist. The file separator
         * string "/" is platform dependent system property.
         *
         * @param path Directory path string.
         */
        private void createDirectoryForFile(File file) {
            File dir = file.getParentFile();
            if (dir == null || dir.exists() || dir.mkdirs())
                return;

            configuration.message.error(
                   "doclet.Unable_to_create_directory_0", dir.getPath());
            throw new DocletAbortException("can't create directory");
        }

        /** Return a string to identify the contents of this object,
         * for debugging purposes.
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("DocFile[");
            if (location != null)
                sb.append("locn:").append(location).append(",");
            if (path != null)
                sb.append("path:").append(path.getPath()).append(",");
            sb.append("file:").append(file);
            sb.append("]");
            return sb.toString();
        }

    }
}
