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

package java.nio.file.attribute;

import java.io.IOException;

/**
 * A file store attribute view that supports reading of space attributes.
 *
 * <p> Where dynamic access to file attributes is required, the attributes
 * supported by this attribute view have the following names and types:
 * <blockquote>
 * <table border="1" cellpadding="8">
 *   <tr>
 *     <th> Name </th>
 *     <th> Type </th>
 *   </tr>
 *  <tr>
 *     <td> "totalSpace" </td>
 *     <td> {@link Long} </td>
 *   </tr>
 *  <tr>
 *     <td> "usableSpace" </td>
 *     <td> {@link Long} </td>
 *   </tr>
 *  <tr>
 *     <td> "unallocatedSpace" </td>
 *     <td> {@link Long} </td>
 *   </tr>
 * </table>
 * </blockquote>
 * <p> The {@link java.nio.file.FileStore#getAttribute getAttribute} method may
 * be used to read any of these attributes.
 *
 * @since 1.7
 */

public interface FileStoreSpaceAttributeView
    extends FileStoreAttributeView
{
    /**
     * Returns the name of the attribute view. Attribute views of this type
     * have the name {@code "space"}.
     */
    @Override
    String name();

    /**
     * Reads the disk space attributes as a bulk operation.
     *
     * <p> It is file system specific if all attributes are read as an
     * atomic operation with respect to other file system operations.
     *
     * @return  The disk space attributes
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    FileStoreSpaceAttributes readAttributes() throws IOException;
}
