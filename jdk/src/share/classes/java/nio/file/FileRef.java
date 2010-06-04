/*
 * Copyright (c) 2007, 2009, Oracle and/or its affiliates. All rights reserved.
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

package java.nio.file;

import java.nio.file.attribute.*;
import java.util.Map;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * A reference to a file.
 *
 * <p> A {@code FileRef} is an object that locates a file and defines methods to
 * open the file for reading or writing. It also provides access to associated
 * metadata or file attributes.
 *
 * @since 1.7
 * @see java.nio.file.attribute.Attributes
 * @see java.io.File#toPath
 */

public interface FileRef {

    /**
     * Opens the file referenced by this object, returning an input stream to
     * read from the file. The stream will not be buffered, and is not required
     * to support the {@link InputStream#mark mark} or {@link InputStream#reset
     * reset} methods. The stream will be safe for access by multiple concurrent
     * threads. Reading commences at the beginning of the file.
     *
     * <p> The {@code options} parameter determines how the file is opened.
     * If no options are present then it is equivalent to opening the file with
     * the {@link StandardOpenOption#READ READ} option. In addition to the {@code
     * READ} option, an implementation may also support additional implementation
     * specific options.
     *
     * @return  an input stream to read bytes from the file
     *
     * @throws  IllegalArgumentException
     *          if an invalid combination of options is specified
     * @throws  UnsupportedOperationException
     *          if an unsupported option is specified
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the file.
     */
    InputStream newInputStream(OpenOption... options) throws IOException;

    /**
     * Opens or creates the file located by this object for writing, returning
     * an output stream to write bytes to the file.
     *
     * <p> The {@code options} parameter determines how the file is opened.
     * If no options are present then this method creates a new file for writing
     * or truncates an existing file. In addition to the {@link StandardOpenOption
     * standard} options, an implementation may also support additional
     * implementation specific options.
     *
     * <p> The resulting stream will not be buffered. The stream will be safe
     * for access by multiple concurrent threads.
     *
     * @param   options
     *          options specifying how the file is opened
     *
     * @return  a new output stream
     *
     * @throws  IllegalArgumentException
     *          if {@code options} contains an invalid combination of options
     * @throws  UnsupportedOperationException
     *          if an unsupported option is specified
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkWrite(String) checkWrite}
     *          method is invoked to check write access to the file.
     */
    OutputStream newOutputStream(OpenOption... options) throws IOException;

    /**
     * Returns a file attribute view of a given type.
     *
     * <p> A file attribute view provides a read-only or updatable view of a
     * set of file attributes. This method is intended to be used where the file
     * attribute view defines type-safe methods to read or update the file
     * attributes. The {@code type} parameter is the type of the attribute view
     * required and the method returns an instance of that type if supported.
     * The {@link BasicFileAttributeView} type supports access to the basic
     * attributes of a file. Invoking this method to select a file attribute
     * view of that type will always return an instance of that class.
     *
     * <p> The {@code options} array may be used to indicate how symbolic links
     * are handled by the resulting file attribute view for the case that the
     * file is a symbolic link. By default, symbolic links are followed. If the
     * option {@link LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} is present then
     * symbolic links are not followed. This option is ignored by implementations
     * that do not support symbolic links.
     *
     * @param   type
     *          the {@code Class} object corresponding to the file attribute view
     * @param   options
     *          options indicating how symbolic links are handled
     *
     * @return  a file attribute view of the specified type, or {@code null} if
     *          the attribute view type is not available
     *
     * @throws  UnsupportedOperationException
     *          If options contains an unsupported option. This exception is
     *          specified to allow the {@code LinkOption} enum be extended
     *          in future releases.
     *
     * @see Attributes#readBasicFileAttributes
     */
    <V extends FileAttributeView> V getFileAttributeView(Class<V> type,
                                                         LinkOption... options);

    /**
     * Sets the value of a file attribute.
     *
     * <p> The {@code attribute} parameter identifies the attribute to be set
     * and takes the form:
     * <blockquote>
     * [<i>view-name</i><b>:</b>]<i>attribute-name</i>
     * </blockquote>
     * where square brackets [...] delineate an optional component and the
     * character {@code ':'} stands for itself.
     *
     * <p> <i>view-name</i> is the {@link FileAttributeView#name name} of a {@link
     * FileAttributeView} that identifies a set of file attributes. If not
     * specified then it defaults to {@code "basic"}, the name of the file
     * attribute view that identifies the basic set of file attributes common to
     * many file systems. <i>attribute-name</i> is the name of the attribute
     * within the set.
     *
     * <p> <b>Usage Example:</b>
     * Suppose we want to set the DOS "hidden" attribute:
     * <pre>
     *    file.setAttribute("dos:hidden", true);
     * </pre>
     *
     * @param   attribute
     *          the attribute to set
     * @param   value
     *          the attribute value
     * @param   options
     *          options indicating how symbolic links are handled
     *
     * @throws  UnsupportedOperationException
     *          if the attribute view is not available or it does not support
     *          updating the attribute
     * @throws  IllegalArgumentException
     *          if the attribute value is of the correct type but has an
     *          inappropriate value
     * @throws  ClassCastException
     *          If the attribute value is not of the expected type or is a
     *          collection containing elements that are not of the expected
     *          type
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, its {@link SecurityManager#checkWrite(String) checkWrite}
     *          method denies write access to the file. If this method is invoked
     *          to set security sensitive attributes then the security manager
     *          may be invoked to check for additional permissions.
     */
    void setAttribute(String attribute, Object value, LinkOption... options)
        throws IOException;

    /**
     * Reads the value of a file attribute.
     *
     * <p> The {@code attribute} parameter identifies the attribute to be read
     * and takes the form:
     * <blockquote>
     * [<i>view-name</i><b>:</b>]<i>attribute-name</i>
     * </blockquote>
     * where square brackets [...] delineate an optional component and the
     * character {@code ':'} stands for itself.
     *
     * <p> <i>view-name</i> is the {@link FileAttributeView#name name} of a {@link
     * FileAttributeView} that identifies a set of file attributes. If not
     * specified then it defaults to {@code "basic"}, the name of the file
     * attribute view that identifies the basic set of file attributes common to
     * many file systems. <i>attribute-name</i> is the name of the attribute.
     *
     * <p> The {@code options} array may be used to indicate how symbolic links
     * are handled for the case that the file is a symbolic link. By default,
     * symbolic links are followed and the file attribute of the final target
     * of the link is read. If the option {@link LinkOption#NOFOLLOW_LINKS
     * NOFOLLOW_LINKS} is present then symbolic links are not followed and so
     * the method returns the file attribute of the symbolic link.
     *
     * <p> <b>Usage Example:</b>
     * Suppose we require the user ID of the file owner on a system that
     * supports a "{@code unix}" view:
     * <pre>
     *    int uid = (Integer)file.getAttribute("unix:uid");
     * </pre>
     *
     * @param   attribute
     *          the attribute to read
     * @param   options
     *          options indicating how symbolic links are handled
     * @return  the attribute value or {@code null} if the attribute view
     *          is not available or it does not support reading the attribute
     *
     *          reading the attribute
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, its {@link SecurityManager#checkRead(String) checkRead}
     *          method denies read access to the file. If this method is invoked
     *          to read security sensitive attributes then the security manager
     *          may be invoked to check for additional permissions.
     */
    Object getAttribute(String attribute, LinkOption... options) throws IOException;

    /**
     * Reads a set of file attributes as a bulk operation.
     *
     * <p> The {@code attributes} parameter identifies the attributes to be read
     * and takes the form:
     * <blockquote>
     * [<i>view-name</i><b>:</b>]<i>attribute-list</i>
     * </blockquote>
     * where square brackets [...] delineate an optional component and the
     * character {@code ':'} stands for itself.
     *
     * <p> <i>view-name</i> is the {@link FileAttributeView#name name} of a {@link
     * FileAttributeView} that identifies a set of file attributes. If not
     * specified then it defaults to {@code "basic"}, the name of the file
     * attribute view that identifies the basic set of file attributes common to
     * many file systems.
     *
     * <p> The <i>attribute-list</i> component is a comma separated list of
     * zero or more names of attributes to read. If the list contains the value
     * {@code "*"} then all attributes are read. Attributes that are not supported
     * are ignored and will not be present in the returned map. It is
     * implementation specific if all attributes are read as an atomic operation
     * with respect to other file system operations.
     *
     * <p> The following examples demonstrate possible values for the {@code
     * attributes} parameter:
     *
     * <blockquote>
     * <table border="0">
     * <tr>
     *   <td> {@code "*"} </td>
     *   <td> Read all {@link BasicFileAttributes basic-file-attributes}. </td>
     * </tr>
     * <tr>
     *   <td> {@code "size,lastModifiedTime,lastAccessTime"} </td>
     *   <td> Reads the file size, last modified time, and last access time
     *     attributes. </td>
     * </tr>
     * <tr>
     *   <td> {@code "posix:*"} </td>
     *   <td> Read all {@link PosixFileAttributes POSIX-file-attributes}.. </td>
     * </tr>
     * <tr>
     *   <td> {@code "posix:permissions,owner,size"} </td>
     *   <td> Reads the POSX file permissions, owner, and file size. </td>
     * </tr>
     * </table>
     * </blockquote>
     *
     * <p> The {@code options} array may be used to indicate how symbolic links
     * are handled for the case that the file is a symbolic link. By default,
     * symbolic links are followed and the file attribute of the final target
     * of the link is read. If the option {@link LinkOption#NOFOLLOW_LINKS
     * NOFOLLOW_LINKS} is present then symbolic links are not followed and so
     * the method returns the file attribute of the symbolic link.
     *
     * @param   attributes
     *          The attributes to read
     * @param   options
     *          Options indicating how symbolic links are handled
     *
     * @return  A map of the attributes returned; may be empty. The map's keys
     *          are the attribute names, its values are the attribute values
     *
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, its {@link SecurityManager#checkRead(String) checkRead}
     *          method denies read access to the file. If this method is invoked
     *          to read security sensitive attributes then the security manager
     *          may be invoke to check for additional permissions.
     */
    Map<String,?> readAttributes(String attributes, LinkOption... options)
        throws IOException;
}
