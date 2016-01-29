/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.tools.DocumentationTool;
import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import jdk.javadoc.internal.doclets.toolkit.Configuration;

/**
 * Implementation of DocFileFactory using a {@link StandardJavaFileManager}.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 */
class StandardDocFileFactory extends DocFileFactory {
    private final StandardJavaFileManager fileManager;
    private File destDir;

    public StandardDocFileFactory(Configuration configuration) {
        super(configuration);
        fileManager = (StandardJavaFileManager) configuration.getFileManager();
    }

    private File getDestDir() {
        if (destDir == null) {
            if (!configuration.destDirName.isEmpty()
                    || !fileManager.hasLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT)) {
                try {
                    String dirName = configuration.destDirName.isEmpty() ? "." : configuration.destDirName;
                    File dir = new File(dirName);
                    fileManager.setLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT, Arrays.asList(dir));
                } catch (IOException e) {
                    throw new DocletAbortException(e);
                }
            }

            destDir = fileManager.getLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT).iterator().next();
        }
        return destDir;
    }

    public DocFile createFileForDirectory(String file) {
        return new StandardDocFile(new File(file));
    }

    public DocFile createFileForInput(String file) {
        return new StandardDocFile(new File(file));
    }

    public DocFile createFileForOutput(DocPath path) {
        return new StandardDocFile(DocumentationTool.Location.DOCUMENTATION_OUTPUT, path);
    }

    @Override
    Iterable<DocFile> list(Location location, DocPath path) {
        if (location != StandardLocation.SOURCE_PATH)
            throw new IllegalArgumentException();

        Set<DocFile> files = new LinkedHashSet<>();
        Location l = fileManager.hasLocation(StandardLocation.SOURCE_PATH)
                ? StandardLocation.SOURCE_PATH : StandardLocation.CLASS_PATH;
        for (File f: fileManager.getLocation(l)) {
            if (f.isDirectory()) {
                f = new File(f, path.getPath());
                if (f.exists())
                    files.add(new StandardDocFile(f));
            }
        }
        return files;
    }

    private static File newFile(File dir, String path) {
        return (dir == null) ? new File(path) : new File(dir, path);
    }

    class StandardDocFile extends DocFile {
        private File file;


        /** Create a StandardDocFile for a given file. */
        private StandardDocFile(File file) {
            super(configuration);
            this.file = file;
        }

        /** Create a StandardDocFile for a given location and relative path. */
        private StandardDocFile(Location location, DocPath path) {
            super(configuration, location, path);
            if (location != DocumentationTool.Location.DOCUMENTATION_OUTPUT) {
                throw new AssertionError("invalid location output");
            }
            this.file = newFile(getDestDir(), path.getPath());
        }

        /** Open an input stream for the file. */
        public InputStream openInputStream() throws IOException {
            JavaFileObject fo = getJavaFileObjectForInput(file);
            return new BufferedInputStream(fo.openInputStream());
        }

        /**
         * Open an output stream for the file.
         * The file must have been created with a location of
         * {@link DocumentationTool.Location#DOCUMENTATION_OUTPUT} and a corresponding relative path.
         */
        public OutputStream openOutputStream() throws IOException, UnsupportedEncodingException {
            if (location != DocumentationTool.Location.DOCUMENTATION_OUTPUT)
                throw new IllegalStateException();

            OutputStream out = getFileObjectForOutput(path).openOutputStream();
            return new BufferedOutputStream(out);
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

            OutputStream out = getFileObjectForOutput(path).openOutputStream();
            if (configuration.docencoding == null) {
                return new BufferedWriter(new OutputStreamWriter(out));
            } else {
                return new BufferedWriter(new OutputStreamWriter(out, configuration.docencoding));
            }
        }

        /** Return true if the file can be read. */
        public boolean canRead() {
            return file.canRead();
        }

        /** Return true if the file can be written. */
        public boolean canWrite() {
            return file.canWrite();
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
            if (!(other instanceof StandardDocFile))
                return false;

            try {
                return file.exists()
                        && file.getCanonicalFile().equals(((StandardDocFile) other).file.getCanonicalFile());
            } catch (IOException e) {
                return false;
            }
        }

        /** If the file is a directory, list its contents. */
        public Iterable<DocFile> list() {
            List<DocFile> files = new ArrayList<>();
            for (File f: file.listFiles()) {
                files.add(new StandardDocFile(f));
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
                return new StandardDocFile(new File(file, p));
            } else {
                return new StandardDocFile(location, path.resolve(p));
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
            return new StandardDocFile(newFile(getDestDir(), file.getPath()));
        }

        /** Return a string to identify the contents of this object,
         * for debugging purposes.
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("StandardDocFile[");
            if (location != null)
                sb.append("locn:").append(location).append(",");
            if (path != null)
                sb.append("path:").append(path.getPath()).append(",");
            sb.append("file:").append(file);
            sb.append("]");
            return sb.toString();
        }

        private JavaFileObject getJavaFileObjectForInput(File file) {
            return fileManager.getJavaFileObjects(file).iterator().next();
        }

        private FileObject getFileObjectForOutput(DocPath path) throws IOException {
            // break the path into a package-part and the rest, by finding
            // the position of the last '/' before an invalid character for a
            // package name, such as the "." before an extension or the "-"
            // in filenames like package-summary.html, doc-files or src-html.
            String p = path.getPath();
            int lastSep = -1;
            for (int i = 0; i < p.length(); i++) {
                char ch = p.charAt(i);
                if (ch == '/') {
                    lastSep = i;
                } else if (i == lastSep + 1 && !Character.isJavaIdentifierStart(ch)
                        || !Character.isJavaIdentifierPart(ch)) {
                    break;
                }
            }
            String pkg = (lastSep == -1) ? "" : p.substring(0, lastSep);
            String rest = p.substring(lastSep + 1);
            return fileManager.getFileForOutput(location, pkg, rest, null);
        }
    }
}
