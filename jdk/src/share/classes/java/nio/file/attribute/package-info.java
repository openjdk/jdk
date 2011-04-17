/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Interfaces and classes providing access to file and file system attributes.
 *
 * <blockquote><table cellspacing=1 cellpadding=0 summary="Attribute views">
 * <tr><th><p align="left">Attribute views</p></th><th><p align="left">Description</p></th></tr>
 * <tr><td valign=top><tt><i>{@link java.nio.file.attribute.AttributeView}</i></tt></td>
 *     <td>Can read or update non-opaque values associated with objects in a file system</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;<i>{@link java.nio.file.attribute.FileAttributeView}</i></tt></td>
 *     <td>Can read or update file attributes</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.file.attribute.BasicFileAttributeView}&nbsp;&nbsp;</i></tt></td>
 *     <td>Can read or update a basic set of file attributes</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.file.attribute.PosixFileAttributeView}&nbsp;&nbsp;</i></tt></td>
 *     <td>Can read or update POSIX defined file attributes</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.file.attribute.DosFileAttributeView}&nbsp;&nbsp;</i></tt></td>
 *     <td>Can read or update FAT file attributes</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp<i>{@link java.nio.file.attribute.FileOwnerAttributeView}&nbsp;&nbsp;</i></tt></td>
 *     <td>Can read or update the owner of a file</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.file.attribute.AclFileAttributeView}&nbsp;&nbsp;</i></tt></td>
 *     <td>Can read or update Access Control Lists</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.file.attribute.UserDefinedFileAttributeView}&nbsp;&nbsp;</i></tt></td>
 *     <td>Can read or update user-defined file attributes</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;<i>{@link java.nio.file.attribute.FileStoreAttributeView}</i></tt></td>
 *     <td>Can read or update file system attributes</td></tr>
 * </table></blockquote>
 *
 * <p> An attribute view provides a read-only or updatable view of the non-opaque
 * values, or <em>metadata</em>, associated with objects in a file system.
 * The {@link java.nio.file.attribute.FileAttributeView} interface is
 * extended by several other interfaces that that views to specific sets of file
 * attributes. {@code FileAttributeViews} are selected by invoking the {@link
 * java.nio.file.Files#getFileAttributeView} method with a
 * <em>type-token</em> to identify the required view. Views can also be identified
 * by name. The {@link java.nio.file.attribute.FileStoreAttributeView} interface
 * provides access to file store attributes. A {@code FileStoreAttributeView} of
 * a given type is obtained by invoking the {@link
 * java.nio.file.FileStore#getFileStoreAttributeView} method.
 *
 * <p> The {@link java.nio.file.attribute.BasicFileAttributeView}
 * class defines methods to read and update a <em>basic</em> set of file
 * attributes that are common to many file systems.
 *
 * <p> The {@link java.nio.file.attribute.PosixFileAttributeView}
 * interface extends {@code BasicFileAttributeView} by defining methods
 * to access the file attributes commonly used by file systems and operating systems
 * that implement the Portable Operating System Interface (POSIX) family of
 * standards.
 *
 * <p> The {@link java.nio.file.attribute.DosFileAttributeView}
 * class extends {@code BasicFileAttributeView} by defining methods to
 * access the legacy "DOS" file attributes supported on file systems such as File
 * Allocation Tabl (FAT), commonly used in consumer devices.
 *
 * <p> The {@link java.nio.file.attribute.AclFileAttributeView}
 * class defines methods to read and write the Access Control List (ACL)
 * file attribute. The ACL model used by this file attribute view is based
 * on the model defined by <a href="http://www.ietf.org/rfc/rfc3530.txt">
 * <i>RFC&nbsp;3530: Network File System (NFS) version 4 Protocol</i></a>.
 *
 * <p> In addition to attribute views, this package also defines classes and
 * interfaces that are used when accessing attributes:
 *
 * <ul>
 *
 *   <p><li> The {@link java.nio.file.attribute.UserPrincipal} and
 *   {@link java.nio.file.attribute.GroupPrincipal} interfaces represent an
 *   identity or group identity. </li>
 *
 *   <p><li> The {@link java.nio.file.attribute.UserPrincipalLookupService}
 *   interface defines methods to lookup user or group principals. </li>
 *
 *   <p><li> The {@link java.nio.file.attribute.FileAttribute} interface
 *   represents the value of an attribute for cases where the attribute value is
 *   required to be set atomically when creating an object in the file system. </li>
 *
 * </ul>
 *
 *
 * <p> Unless otherwise noted, passing a <tt>null</tt> argument to a constructor
 * or method in any class or interface in this package will cause a {@link
 * java.lang.NullPointerException NullPointerException} to be thrown.
 *
 * @since 1.7
 */

package java.nio.file.attribute;
