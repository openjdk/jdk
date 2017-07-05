/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package javax.activation;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import com.sun.activation.registries.MimeTypeFile;

/**
 * The FileDataSource class implements a simple DataSource object
 * that encapsulates a file. It provides data typing services via
 * a FileTypeMap object. <p>
 *
 * <b>FileDataSource Typing Semantics</b><p>
 *
 * The FileDataSource class delegates data typing of files
 * to an object subclassed from the FileTypeMap class.
 * The <code>setFileTypeMap</code> method can be used to explicitly
 * set the FileTypeMap for an instance of FileDataSource. If no
 * FileTypeMap is set, the FileDataSource will call the FileTypeMap's
 * getDefaultFileTypeMap method to get the System's default FileTypeMap.
 *
 * @see javax.activation.DataSource
 * @see javax.activation.FileTypeMap
 * @see javax.activation.MimetypesFileTypeMap
 *
 * @since 1.6
 */
public class FileDataSource implements DataSource {

    // keep track of original 'ref' passed in, non-null
    // one indicated which was passed in:
    private File _file = null;
    private FileTypeMap typeMap = null;

    /**
     * Creates a FileDataSource from a File object. <i>Note:
     * The file will not actually be opened until a method is
     * called that requires the file to be opened.</i>
     *
     * @param file the file
     */
    public FileDataSource(File file) {
        _file = file;   // save the file Object...
    }

    /**
     * Creates a FileDataSource from
     * the specified path name. <i>Note:
     * The file will not actually be opened until a method is
     * called that requires the file to be opened.</i>
     *
     * @param name the system-dependent file name.
     */
    public FileDataSource(String name) {
        this(new File(name));   // use the file constructor
    }

    /**
     * This method will return an InputStream representing the
     * the data and will throw an IOException if it can
     * not do so. This method will return a new
     * instance of InputStream with each invocation.
     *
     * @return an InputStream
     */
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(_file);
    }

    /**
     * This method will return an OutputStream representing the
     * the data and will throw an IOException if it can
     * not do so. This method will return a new instance of
     * OutputStream with each invocation.
     *
     * @return an OutputStream
     */
    public OutputStream getOutputStream() throws IOException {
        return new FileOutputStream(_file);
    }

    /**
     * This method returns the MIME type of the data in the form of a
     * string. This method uses the currently installed FileTypeMap. If
     * there is no FileTypeMap explictly set, the FileDataSource will
     * call the <code>getDefaultFileTypeMap</code> method on
     * FileTypeMap to acquire a default FileTypeMap. <i>Note: By
     * default, the FileTypeMap used will be a MimetypesFileTypeMap.</i>
     *
     * @return the MIME Type
     * @see javax.activation.FileTypeMap#getDefaultFileTypeMap
     */
    public String getContentType() {
        // check to see if the type map is null?
        if (typeMap == null)
            return FileTypeMap.getDefaultFileTypeMap().getContentType(_file);
        else
            return typeMap.getContentType(_file);
    }

    /**
     * Return the <i>name</i> of this object. The FileDataSource
     * will return the file name of the object.
     *
     * @return the name of the object.
     * @see javax.activation.DataSource
     */
    public String getName() {
        return _file.getName();
    }

    /**
     * Return the File object that corresponds to this FileDataSource.
     * @return the File object for the file represented by this object.
     */
    public File getFile() {
        return _file;
    }

    /**
     * Set the FileTypeMap to use with this FileDataSource
     *
     * @param map The FileTypeMap for this object.
     */
    public void setFileTypeMap(FileTypeMap map) {
        typeMap = map;
    }
}
