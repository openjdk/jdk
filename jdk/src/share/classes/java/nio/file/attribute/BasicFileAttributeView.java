/*
 * Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.nio.file.attribute;

import java.util.concurrent.TimeUnit;
import java.io.IOException;

/**
 * A file attribute view that provides a view of a <em>basic set</em> of file
 * attributes common to many file systems. The basic set of file attributes
 * consist of <em>mandatory</em> and <em>optional</em> file attributes as
 * defined by the {@link BasicFileAttributes} interface.

 * <p> The file attributes are retrieved from the file system as a <em>bulk
 * operation</em> by invoking the {@link #readAttributes() readAttributes} method.
 * This class also defines the {@link #setTimes setTimes} method to update the
 * file's time attributes.
 *
 * <p> Where dynamic access to file attributes is required, the attributes
 * supported by this attribute view have the following names and types:
 * <blockquote>
 *  <table border="1" cellpadding="8">
 *   <tr>
 *     <th> Name </th>
 *     <th> Type </th>
 *   </tr>
 *  <tr>
 *     <td> "lastModifiedTime" </td>
 *     <td> {@link Long} </td>
 *   </tr>
 *   <tr>
 *     <td> "lastAccessTime" </td>
 *     <td> {@link Long} </td>
 *   </tr>
 *   <tr>
 *     <td> "creationTime" </td>
 *     <td> {@link Long} </td>
 *   </tr>
 *  <tr>
 *     <td> "resolution" </td>
 *     <td> {@link java.util.concurrent.TimeUnit} </td>
 *   </tr>
 *   <tr>
 *     <td> "size" </td>
 *     <td> {@link Long} </td>
 *   </tr>
 *   <tr>
 *     <td> "isRegularFile" </td>
 *     <td> {@link Boolean} </td>
 *   </tr>
 *   <tr>
 *     <td> "isDirectory" </td>
 *     <td> {@link Boolean} </td>
 *   </tr>
 *   <tr>
 *     <td> "isSymbolicLink" </td>
 *     <td> {@link Boolean} </td>
 *   </tr>
 *   <tr>
 *     <td> "isOther" </td>
 *     <td> {@link Boolean} </td>
 *   </tr>
 *   <tr>
 *     <td> "linkCount" </td>
 *     <td> {@link Integer} </td>
 *   </tr>
 *   <tr>
 *     <td> "fileKey" </td>
 *     <td> {@link Object} </td>
 *   </tr>
 * </table>
 * </blockquote>
 *
 * <p> The {@link #getAttribute getAttribute} or {@link
 * #readAttributes(String,String[]) readAttributes(String,String[])} methods may
 * be used to read any of these attributes as if by invoking the {@link
 * #readAttributes() readAttributes()} method.
 *
 * <p> The {@link #setAttribute setAttribute} method may be used to update the
 * file's last modified time, last access time or create time attributes as if
 * by invoking the {@link #setTimes setTimes} method. In that case, the time
 * value is interpreted in {@link TimeUnit#MILLISECONDS milliseconds} and
 * converted to the precision supported by the file system.
 *
 * @since 1.7
 * @see Attributes
 */

public interface BasicFileAttributeView
    extends FileAttributeView
{
    /**
     * Returns the name of the attribute view. Attribute views of this type
     * have the name {@code "basic"}.
     */
    @Override
    String name();

    /**
     * Reads the basic file attributes as a bulk operation.
     *
     * <p> It is implementation specific if all file attributes are read as an
     * atomic operation with respect to other file system operations.
     *
     * @return  The file attributes
     *
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, a security manager is
     *          installed, its {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the file
     */
    BasicFileAttributes readAttributes() throws IOException;

    /**
     * Updates any or all of the file's last modified time, last access time,
     * and create time attributes.
     *
     * <p> This method updates the file's timestamp attributes. The values are
     * measured since the epoch (00:00:00 GMT, January 1, 1970) and converted to
     * the precision supported by the file system. Converting from finer to
     * coarser granularities result in precision loss. If a value is larger
     * than the maximum supported by the file system then the corresponding
     * timestamp is set to its maximum value.
     *
     * <p> If any of the {@code lastModifiedTime}, {@code lastAccessTime},
     * or {@code createTime} parameters has the value {@code null} then the
     * corresponding timestamp is not changed. An implementation may require to
     * read the existing values of the file attributes when only some, but not
     * all, of the timestamp attributes are updated. Consequently, this method
     * may not be an atomic operation with respect to other file system
     * operations. If all of the {@code lastModifiedTime}, {@code
     * lastAccessTime} and {@code createTime} parameters are {@code null} then
     * this method has no effect.
     *
     * @param   lastModifiedTime
     *          The new last modified time, or {@code -1L} to update it to
     *          the current time, or {@code null} to not change the attribute
     * @param   lastAccessTime
     *          The last access time, or {@code -1L} to update it to
     *          the current time, or {@code null} to not change the attribute.
     * @param   createTime
     *          The file's create time, or {@code -1L} to update it to
     *          the current time, or {@code null} to not change the attribute
     * @param   unit
     *          A {@code TimeUnit} determining how to interpret the time values
     *
     * @throws  IllegalArgumentException
     *          If any of the parameters is a negative value other than {@code
     *          -1L}
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, a security manager is
     *          installed, its  {@link SecurityManager#checkWrite(String) checkWrite}
     *          method is invoked to check write access to the file
     */
    void setTimes(Long lastModifiedTime,
                  Long lastAccessTime,
                  Long createTime,
                  TimeUnit unit) throws IOException;
}
