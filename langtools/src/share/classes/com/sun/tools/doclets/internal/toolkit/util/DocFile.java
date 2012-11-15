/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;

import com.sun.tools.doclets.internal.toolkit.Configuration;

/**
 * Abstraction for handling files, which may be specified directly
 * (e.g. via a path on the command line) or relative to a Location.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @since 8
 */
public class DocFile {

    /**
     * The doclet configuration.
     * Provides access to options such as docencoding, output directory, etc.
     */
    private final Configuration configuration;

    /**
     * The location for this file. Maybe null if the file was created without
     * a location or path.
     */
    private final Location location;

    /**
     * The path relative to the (output) location. Maybe null if the file was
     * created without a location or path.
     */
    private final DocPath path;

    /**
     * The file object itself.
     * This is temporary, until we create different subtypes of DocFile.
     */
    private final File file;

    /** Create a DocFile for a directory. */
    public static DocFile createFileForDirectory(Configuration configuration, String file) {
        return new DocFile(configuration, new File(file));
    }

    /** Create a DocFile for a file that will be opened for reading. */
    public static DocFile createFileForInput(Configuration configuration, String file) {
        return new DocFile(configuration, new File(file));
    }

    /** Create a DocFile for a file that will be opened for writing. */
    public static DocFile createFileForOutput(Configuration configuration, DocPath path) {
        return new DocFile(configuration, StandardLocation.CLASS_OUTPUT, path);
    }

    /**
     * List the directories and files found in subdirectories along the
     * elements of the given location.
     * @param configuration the doclet configuration
     * @param location currently, only {@link StandardLocation#SOURCE_PATH} is supported.
     * @param path the subdirectory of the directories of the location for which to
     *  list files
     */
    public static Iterable<DocFile> list(Configuration configuration, Location location, DocPath path) {
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
                    files.add(new DocFile(configuration, f));
            }
        }
        return files;
    }

    /** Create a DocFile for a given file. */
    private DocFile(Configuration configuration, File file) {
        this.configuration = configuration;
        this.location = null;
        this.path = null;
        this.file = file;
    }

    /** Create a DocFile for a given location and relative path. */
    private DocFile(Configuration configuration, Location location, DocPath path) {
        this.configuration = configuration;
        this.location = location;
        this.path = path;
        this.file = path.resolveAgainst(configuration.destDirName);
    }

    /** Open an input stream for the file. */
    public InputStream openInputStream() throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    /**
     * Open an output stream for the file.
     * The file must have been created with a location of
     * {@link StandardLocation#CLASS_OUTPUT} and a corresponding relative path.
     */
    public OutputStream openOutputStream() throws IOException, UnsupportedEncodingException {
        if (location != StandardLocation.CLASS_OUTPUT)
            throw new IllegalStateException();

        createDirectoryForFile(file);
        return new BufferedOutputStream(new FileOutputStream(file));
    }

    /**
     * Open an writer for the file, using the encoding (if any) given in the
     * doclet configuration.
     * The file must have been created with a location of
     * {@link StandardLocation#CLASS_OUTPUT} and a corresponding relative path.
     */
    public Writer openWriter() throws IOException, UnsupportedEncodingException {
        if (location != StandardLocation.CLASS_OUTPUT)
            throw new IllegalStateException();

        createDirectoryForFile(file);
        FileOutputStream fos = new FileOutputStream(file);
        if (configuration.docencoding == null) {
            return new BufferedWriter(new OutputStreamWriter(fos));
        } else {
            return new BufferedWriter(new OutputStreamWriter(fos, configuration.docencoding));
        }
    }

    /**
     * Copy the contents of another file directly to this file.
     */
    public void copyFile(DocFile fromFile) throws IOException {
        if (location != StandardLocation.CLASS_OUTPUT)
            throw new IllegalStateException();

        createDirectoryForFile(file);

        InputStream input = fromFile.openInputStream();
        OutputStream output = openOutputStream();
        try {
            byte[] bytearr = new byte[1024];
            int len;
            while ((len = input.read(bytearr)) != -1) {
                output.write(bytearr, 0, len);
            }
        } catch (FileNotFoundException exc) {
        } catch (SecurityException exc) {
        } finally {
            input.close();
            output.close();
        }
    }

    /**
     * Copy the contents of a resource file to this file.
     * @param resource the path of the resource, relative to the package of this class
     * @param overwrite whether or not to overwrite the file if it already exists
     * @param replaceNewLine if false, the file is copied as a binary file;
     *     if true, the file is written line by line, using the platform line
     *     separator
     */
    public void copyResource(DocPath resource, boolean overwrite, boolean replaceNewLine) {
        if (location != StandardLocation.CLASS_OUTPUT)
            throw new IllegalStateException();

        if (file.exists() && !overwrite)
            return;

        createDirectoryForFile(file);

        try {
            InputStream in = Configuration.class.getResourceAsStream(resource.getPath());
            if (in == null)
                return;

            OutputStream out = new FileOutputStream(file);
            try {
                if (!replaceNewLine) {
                    byte[] buf = new byte[2048];
                    int n;
                    while((n = in.read(buf))>0) out.write(buf,0,n);
                } else {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    BufferedWriter writer;
                    if (configuration.docencoding == null) {
                        writer = new BufferedWriter(new OutputStreamWriter(out));
                    } else {
                        writer = new BufferedWriter(new OutputStreamWriter(out,
                                configuration.docencoding));
                    }
                    try {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            writer.write(line);
                            writer.write(DocletConstants.NL);
                        }
                    } finally {
                        reader.close();
                        writer.close();
                    }
                }
            } finally {
                in.close();
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new DocletAbortException();
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
    boolean isAbsolute() {
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
        try {
            return file.exists()
                    && file.getCanonicalFile().equals(other.file.getCanonicalFile());
        } catch (IOException e) {
            return false;
        }
    }

    /** If the file is a directory, list its contents. */
    public Iterable<DocFile> list() {
        List<DocFile> files = new ArrayList<DocFile>();
        for (File f: file.listFiles()) {
            files.add(new DocFile(configuration, f));
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
            return new DocFile(configuration, new File(file, p));
        } else {
            return new DocFile(configuration, location, path.resolve(p));
        }
    }

    /**
     * Resolve a relative file against the given output location.
     * @param locn Currently, only SOURCE_OUTPUT is supported.
     */
    public DocFile resolveAgainst(StandardLocation locn) {
        if (locn != StandardLocation.CLASS_OUTPUT)
            throw new IllegalArgumentException();
        return new DocFile(configuration,
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
        throw new DocletAbortException();
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
