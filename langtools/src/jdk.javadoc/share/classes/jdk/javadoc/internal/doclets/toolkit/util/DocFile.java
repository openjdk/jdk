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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;

import jdk.javadoc.internal.doclets.toolkit.Configuration;

/**
 * Abstraction for handling files, which may be specified directly
 * (e.g. via a path on the command line) or relative to a Location.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 */
public abstract class DocFile {

    /** Create a DocFile for a directory. */
    public static DocFile createFileForDirectory(Configuration configuration, String file) {
        return DocFileFactory.getFactory(configuration).createFileForDirectory(file);
    }

    /** Create a DocFile for a file that will be opened for reading. */
    public static DocFile createFileForInput(Configuration configuration, String file) {
        return DocFileFactory.getFactory(configuration).createFileForInput(file);
    }

    /** Create a DocFile for a file that will be opened for writing. */
    public static DocFile createFileForOutput(Configuration configuration, DocPath path) {
        return DocFileFactory.getFactory(configuration).createFileForOutput(path);
    }

    private final Configuration configuration;

    /**
     * The location for this file. Maybe null if the file was created without
     * a location or path.
     */
    protected final Location location;

    /**
     * The path relative to the (output) location. Maybe null if the file was
     * created without a location or path.
     */
    protected final DocPath path;

    /**
     * List the directories and files found in subdirectories along the
     * elements of the given location.
     * @param configuration the doclet configuration
     * @param location currently, only {@link StandardLocation#SOURCE_PATH} is supported.
     * @param path the subdirectory of the directories of the location for which to
     *  list files
     */
    public static Iterable<DocFile> list(Configuration configuration, Location location, DocPath path) {
        return DocFileFactory.getFactory(configuration).list(location, path);
    }

    /** Create a DocFile without a location or path */
    protected DocFile(Configuration configuration) {
        this.configuration = configuration;
        this.location = null;
        this.path = null;
    }

    /** Create a DocFile for a given location and relative path. */
    protected DocFile(Configuration configuration, Location location, DocPath path) {
        this.configuration = configuration;
        this.location = location;
        this.path = path;
    }

    /** Open an input stream for the file. */
    public abstract InputStream openInputStream() throws IOException;

    /**
     * Open an output stream for the file.
     * The file must have been created with a location of
     * {@link DocumentationTool.Location#DOCUMENTATION_OUTPUT}
     * and a corresponding relative path.
     */
    public abstract OutputStream openOutputStream() throws IOException, UnsupportedEncodingException;

    /**
     * Open an writer for the file, using the encoding (if any) given in the
     * doclet configuration.
     * The file must have been created with a location of
     * {@link DocumentationTool.Location#DOCUMENTATION_OUTPUT} and a corresponding relative path.
     */
    public abstract Writer openWriter() throws IOException, UnsupportedEncodingException;

    /**
     * Copy the contents of another file directly to this file.
     */
    public void copyFile(DocFile fromFile) throws IOException {
        try (OutputStream output = openOutputStream();
             InputStream input = fromFile.openInputStream()) {
            byte[] bytearr = new byte[1024];
            int len;
            while ((len = input.read(bytearr)) != -1) {
                output.write(bytearr, 0, len);
            }
        }
        catch (FileNotFoundException | SecurityException exc) {
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
        if (exists() && !overwrite)
            return;

        try {
            InputStream in = Configuration.class.getResourceAsStream(resource.getPath());
            if (in == null)
                return;

            try (OutputStream out = openOutputStream()) {
                if (!replaceNewLine) {
                    byte[] buf = new byte[2048];
                    int n;
                    while ((n = in.read(buf)) > 0)
                        out.write(buf, 0, n);
                } else {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                         BufferedWriter writer = new BufferedWriter(configuration.docencoding == null
                                                                    ? new OutputStreamWriter(out)
                                                                    : new OutputStreamWriter(out, configuration.docencoding))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            writer.write(line);
                            writer.write(DocletConstants.NL);
                        }
                    }
                }
            } finally {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new DocletAbortException(e);
        }
    }

    /** Return true if the file can be read. */
    public abstract boolean canRead();

    /** Return true if the file can be written. */
    public abstract boolean canWrite();

    /** Return true if the file exists. */
    public abstract boolean exists();

    /** Return the base name (last component) of the file name. */
    public abstract String getName();

    /** Return the file system path for this file. */
    public abstract String getPath();

    /** Return true if file has an absolute path name. */
    public abstract boolean isAbsolute();

    /** Return true if file identifies a directory. */
    public abstract boolean isDirectory();

    /** Return true if file identifies a file. */
    public abstract boolean isFile();

    /** Return true if this file is the same as another. */
    public abstract boolean isSameFile(DocFile other);

    /** If the file is a directory, list its contents. */
    public abstract Iterable<DocFile> list() throws IOException;

    /** Create the file as a directory, including any parent directories. */
    public abstract boolean mkdirs();

    /**
     * Derive a new file by resolving a relative path against this file.
     * The new file will inherit the configuration and location of this file
     * If this file has a path set, the new file will have a corresponding
     * new path.
     */
    public abstract DocFile resolve(DocPath p);

    /**
     * Derive a new file by resolving a relative path against this file.
     * The new file will inherit the configuration and location of this file
     * If this file has a path set, the new file will have a corresponding
     * new path.
     */
    public abstract DocFile resolve(String p);

    /**
     * Resolve a relative file against the given output location.
     * @param locn Currently, only
     * {@link DocumentationTool.Location#DOCUMENTATION_OUTPUT} is supported.
     */
    public abstract DocFile resolveAgainst(Location locn);
}
