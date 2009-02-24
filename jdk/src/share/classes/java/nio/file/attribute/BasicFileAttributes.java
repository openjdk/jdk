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

/**
 * Basic attributes associated with a file in a file system.
 *
 * <p> Basic file attributes are attributes that are common to many file systems
 * and consist of mandatory and optional file attributes as defined by this
 * interface.
 *
 * <p> <b>Usage Example:</b>
 * <pre>
 *    FileRef file = ...
 *    BasicFileAttributes attrs = Attributes.readBasicFileAttributes(file);
 * </pre>
 *
 * @since 1.7
 *
 * @see BasicFileAttributeView
 */

public interface BasicFileAttributes {

    /**
     * Returns the time of last modification.
     *
     * <p> The {@link #resolution() resolution} method returns the {@link TimeUnit}
     * to interpret the return value of this method.
     *
     * @return  a <code>long</code> value representing the time the file was
     *          last modified since the epoch (00:00:00 GMT, January 1, 1970),
     *          or {@code -1L} if the attribute is not supported.
     */
    long lastModifiedTime();

    /**
     * Returns the time of last access if supported.
     *
     * <p> The {@link #resolution() resolution} method returns the {@link TimeUnit}
     * to interpret the return value of this method.
     *
     * @return  a <code>long</code> value representing the time of last access
     *          since the epoch (00:00:00 GMT, January 1, 1970), or {@code -1L}
     *          if the attribute is not supported.
     */
    long lastAccessTime();

    /**
     * Returns the creation time if supported. The creation time is the time
     * that the file was created.
     *
     * <p> The {@link #resolution() resolution} method returns the {@link TimeUnit}
     * to interpret the return value of this method.
     *
     * @return  a <code>long</code> value representing the time the file was
     *          created since the epoch (00:00:00 GMT, January 1, 1970), or
     *          {@code -1L} if the attribute is not supported.
     */
    long creationTime();

    /**
     * Returns the {@link TimeUnit} required to interpret the time of last
     * modification, time of last access, and creation time.
     *
     * @return  the {@code TimeUnit} required to interpret the file time stamps
     */
    TimeUnit resolution();

    /**
     * Tells whether the file is a regular file with opaque content.
     */
    boolean isRegularFile();

    /**
     * Tells whether the file is a directory.
     */
    boolean isDirectory();

    /**
     * Tells whether the file is a symbolic-link.
     */
    boolean isSymbolicLink();

    /**
     * Tells whether the file is something other than a regular file, directory,
     * or link.
     */
    boolean isOther();

    /**
     * Returns the size of the file (in bytes). The size may differ from the
     * actual size on the file system due to compression, support for sparse
     * files, or other reasons. The size of files that are not {@link
     * #isRegularFile regular} files is implementation specific and
     * therefore unspecified.
     *
     * @return  the file size, in bytes
     */
    long size();

    /**
     * Returns the number of <em>links</em> to this file.
     *
     * <p> On file systems where the same file may be in several directories then
     * the link count is the number of directory entries for the file. The return
     * value is {@code 1} on file systems that only allow a file to have a
     * single name in a single directory.
     *
     * @see java.nio.file.Path#createLink
     */
    int linkCount();

    /**
     * Returns an object that uniquely identifies the given file, or {@code
     * null} if a file key is not available. On some platforms or file systems
     * it is possible to use an identifier, or a combination of identifiers to
     * uniquely identify a file. Such identifiers are important for operations
     * such as file tree traversal in file systems that support <a
     * href="../package-summary.html#links">symbolic links</a> or file systems
     * that allow a file to be an entry in more than one directory. On UNIX file
     * systems, for example, the <em>device ID</em> and <em>inode</em> are
     * commonly used for such purposes.
     *
     * <p> The file key returned by this method can only be guaranteed to be
     * unique if the file system and files remain static. Whether a file system
     * re-uses identifiers after a file is deleted is implementation dependent and
     * therefore unspecified.
     *
     * <p> File keys returned by this method can be compared for equality and are
     * suitable for use in collections. If the file system and files remain static,
     * and two files are the {@link java.nio.file.FileRef#isSameFile same} with
     * non-{@code null} file keys, then their file keys are equal.
     *
     * @see java.nio.file.Files#walkFileTree
     */
    Object fileKey();
}
