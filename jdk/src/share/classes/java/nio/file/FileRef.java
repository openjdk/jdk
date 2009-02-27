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

package java.nio.file;

import java.nio.file.attribute.*;
import java.nio.channels.SeekableByteChannel;
import java.io.IOException;

/**
 * A reference to a file.
 *
 * <p> A {@code FileRef} is an object that locates a file and defines methods to
 * access the file. The means by which the file is located depends on the
 * implementation. In many cases, a file is located by a {@link Path} but it may
 * be located by other means such as a file-system identifier.
 *
 * <p> This interface defines the following operations:
 * <ul>
 *   <li><p> The {@link #newByteChannel newByteChannel} method
 *     may be used to open a file and obtain a byte channel for reading or
 *     writing. </p></li>
 *   <li><p> The {@link #delete delete} method may be used to delete a file.
 *     </p></li>
 *   <li><p> The {@link #checkAccess checkAccess} method may be used to check
 *     the existence or accessibility of a file. </p></li>
 *   <li><p> The {@link #isSameFile isSameFile} method may be used to test if
 *     two file references locate the same file. </p></li>
 *   <li><p> The {@link #getFileStore getFileStore} method may be used to
 *     obtain the {@link FileStore} representing the storage where a file is
 *     located. </p></li>
 * </ul>
 *
 * <p> Access to associated metadata or file attributes requires an appropriate
 * {@link FileAttributeView FileAttributeView}. The {@link
 * #getFileAttributeView(Class,LinkOption[]) getFileAttributeView(Class,LinkOption[])}
 * method may be used to obtain a file attribute view that defines type-safe
 * methods to read or update file attributes. The {@link
 * #getFileAttributeView(String,LinkOption[]) getFileAttributeView(String,LinkOption[])}
 * method may be used to obtain a file attribute view where dynamic access to
 * file attributes where required.
 *
 * <p> A {@code FileRef} is immutable and safe for use by multiple concurrent
 * threads.
 *
 * @since 1.7
 */

public interface FileRef {

    /**
     * Opens the file referenced by this object, returning a seekable byte
     * channel to access the file.
     *
     * <p> The {@code options} parameter determines how the file is opened.
     * The {@link StandardOpenOption#READ READ} and {@link StandardOpenOption#WRITE
     * WRITE} options determine if the file should be opened for reading and/or
     * writing. If neither option (or the {@link StandardOpenOption#APPEND APPEND}
     * option) is contained in the array then the file is opened for reading.
     * By default reading or writing commences at the beginning of the file.
     *
     * <p> In the addition to {@code READ} and {@code WRITE}, the following
     * options may be present:
     *
     * <table border=1 cellpadding=5 summary="">
     * <tr> <th>Option</th> <th>Description</th> </tr>
     * <tr>
     *   <td> {@link StandardOpenOption#APPEND APPEND} </td>
     *   <td> If this option is present then the file is opened for writing and
     *     each invocation of the channel's {@code write} method first advances
     *     the position to the end of the file and then writes the requested
     *     data. Whether the advancement of the position and the writing of the
     *     data are done in a single atomic operation is system-dependent and
     *     therefore unspecified. This option may not be used in conjunction
     *     with the {@code READ} or {@code TRUNCATE_EXISTING} options. </td>
     * </tr>
     * <tr>
     *   <td> {@link StandardOpenOption#TRUNCATE_EXISTING TRUNCATE_EXISTING} </td>
     *   <td> If this option is present then the existing file is truncated to
     *   a size of 0 bytes. This option is ignored when the file is opened only
     *   for reading. </td>
     * </tr>
     * <tr>
     *   <td> {@link StandardOpenOption#SYNC SYNC} </td>
     *   <td> Requires that every update to the file's content or metadata be
     *   written synchronously to the underlying storage device. (see <a
     *   href="package-summary.html#integrity"> Synchronized I/O file
     *   integrity</a>). </td>
     * </tr>
     * <tr>
     *   <td> {@link StandardOpenOption#DSYNC DSYNC} </td>
     *   <td> Requires that every update to the file's content be written
     *   synchronously to the underlying storage device. (see <a
     *   href="package-summary.html#integrity"> Synchronized I/O file
     *   integrity</a>). </td>
     * </tr>
     * </table>
     *
     * <p> An implementation of this interface may support additional options
     * defined by the {@link StandardOpenOption} enumeration type or other
     * implementation specific options.
     *
     * <p> The {@link java.nio.channels.Channels} utility classes defines methods
     * to construct input and output streams where inter-operation with the
     * {@link java.io} package is required.
     *
     * @param   options
     *          Options specifying how the file is opened
     *
     * @return  a new seekable byte channel
     *
     * @throws  IllegalArgumentException
     *          If an invalid combination of options is specified
     * @throws  UnsupportedOperationException
     *          If an unsupported open option is specified
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the path if the file is
     *          opened for reading. The {@link SecurityManager#checkWrite(String)
     *          checkWrite} method is invoked to check write access to the path
     *          if the file is opened for writing.
     */
    SeekableByteChannel newByteChannel(OpenOption... options)
        throws IOException;

    /**
     * Returns the {@link FileStore} representing the file store where the file
     * referenced by this object is stored.
     *
     * <p> Once a reference to the {@code FileStore} is obtained it is
     * implementation specific if operations on the returned {@code FileStore},
     * or {@link FileStoreAttributeView} objects obtained from it, continue
     * to depend on the existence of the file. In particular the behavior is not
     * defined for the case that the file is deleted or moved to a different
     * file store.
     *
     * @return  The file store where the file is stored
     *
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the file, and in
     *          addition it checks {@link RuntimePermission}<tt>
     *          ("getFileStoreAttributes")</tt>
     */
    FileStore getFileStore() throws IOException;

    /**
     * Checks the existence and optionally the accessibility of the file
     * referenced by this object.
     *
     * <p> This method checks the existence of a file and that this Java virtual
     * machine has appropriate privileges that would allow it access the file
     * according to all of access modes specified in the {@code modes} parameter
     * as follows:
     *
     * <table border=1 cellpadding=5 summary="">
     * <tr> <th>Value</th> <th>Description</th> </tr>
     * <tr>
     *   <td> {@link AccessMode#READ READ} </td>
     *   <td> Checks that the file exists and that the Java virtual machine has
     *     permission to read the file. </td>
     * </tr>
     * <tr>
     *   <td> {@link AccessMode#WRITE WRITE} </td>
     *   <td> Checks that the file exists and that the Java virtual machine has
     *     permission to write to the file, </td>
     * </tr>
     * <tr>
     *   <td> {@link AccessMode#EXECUTE EXECUTE} </td>
     *   <td> Checks that the file exists and that the Java virtual machine has
     *     permission to {@link Runtime#exec execute} the file. The semantics
     *     may differ when checking access to a directory. For example, on UNIX
     *     systems, checking for {@code EXECUTE} access checks that the Java
     *     virtual machine has permission to search the directory in order to
     *     access file or subdirectories. </td>
     * </tr>
     * </table>
     *
     * <p> If the {@code modes} parameter is of length zero, then the existence
     * of the file is checked.
     *
     * <p> This method follows symbolic links if the file referenced by this
     * object is a symbolic link. Depending on the implementation, this method
     * may require to read file permissions, access control lists, or other
     * file attributes in order to check the effective access to the file. To
     * determine the effective access to a file may require access to several
     * attributes and so in some implementations this method may not be atomic
     * with respect to other file system operations. Furthermore, as the result
     * of this method is immediately outdated, there is no guarantee that a
     * subsequence access will succeed (or even that it will access the same
     * file). Care should be taken when using this method in security sensitive
     * applications.
     *
     * @param   modes
     *          The access modes to check; may have zero elements
     *
     * @throws  UnsupportedOperationException
     *          An implementation is required to support checking for
     *          {@code READ}, {@code WRITE}, and {@code EXECUTE} access. This
     *          exception is specified to allow for the {@code Access} enum to
     *          be extended in future releases.
     * @throws  NoSuchFileException
     *          If a file does not exist <i>(optional specific exception)</i>
     * @throws  AccessDeniedException
     *          The requested access would be denied or the access cannot be
     *          determined because the Java virtual machine has insufficient
     *          privileges or other reasons. <i>(optional specific exception)</i>
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          is invoked when checking read access to the file or only the
     *          existence of the file, the {@link SecurityManager#checkWrite(String)
     *          checkWrite} is invoked when checking write access to the file,
     *          and {@link SecurityManager#checkExec(String) checkExec} is invoked
     *          when checking execute access.
     */
    void checkAccess(AccessMode... modes) throws IOException;

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
     *          The {@code Class} object corresponding to the file attribute view
     * @param   options
     *          Options indicating how symbolic links are handled
     *
     * @return  A file attribute view of the specified type, or {@code null} if
     *          the attribute view type is not available
     *
     * @throws  UnsupportedOperationException
     *          If options contains an unsupported option. This exception is
     *          specified to allow the {@code LinkOption} enum be extended
     *          in future releases.
     *
     * @see Attributes#readBasicFileAttributes
     */
    <V extends FileAttributeView> V getFileAttributeView(Class<V> type, LinkOption... options);

    /**
     * Returns a file attribute view of the given name.
     *
     * <p> A file attribute view provides a read-only or updatable view of a
     * set of file attributes. This method is intended to be used where
     * <em>dynamic access</em> to the file attributes is required. The {@code
     * name} parameter specifies the {@link FileAttributeView#name name} of the
     * file attribute view and this method returns an instance of that view if
     * supported. The {@link BasicFileAttributeView} type supports access to the
     * basic attributes of a file and is name {@code "basic"}. Invoking this
     * method to select a file attribute view named {@code "basic"} will always
     * return an instance of that class.
     *
     * <p> The {@code options} array may be used to indicate how symbolic links
     * are handled by the resulting file attribute view for the case that the
     * file is a symbolic link. By default, symbolic links are followed. If the
     * option {@link LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} is present then
     * symbolic links are not followed. This option is ignored by implementations
     * that do not support symbolic links.
     *
     * @param   name
     *          The name of the file attribute view
     * @param   options
     *          Options indicating how symbolic links are handled
     *
     * @return  A file attribute view of the given name, or {@code null} if
     *          the attribute view is not available
     *
     * @throws  UnsupportedOperationException
     *          If options contains an unsupported option. This exception is
     *          specified to allow the {@code LinkOption} enum be extended
     *          in future releases.
     */
    FileAttributeView getFileAttributeView(String name, LinkOption... options);

    /**
     * Tests if the file referenced by this object is the same file referenced
     * by another object.
     *
     * <p> If this {@code FileRef} and the given {@code FileRef} are {@link
     * #equals(Object) equal} then this method returns {@code true} without checking
     * if the file exists. If the {@code FileRef} and the given {@code FileRef}
     * are associated with different providers, or the given {@code FileRef} is
     * {@code null} then this method returns {@code false}. Otherwise, this method
     * checks if both {@code FileRefs} locate the same file, and depending on the
     * implementation, may require to open or access both files.
     *
     * <p> If the file system and files remain static, then this method implements
     * an equivalence relation for non-null {@code FileRefs}.
     * <ul>
     * <li>It is <i>reflexive</i>: for a non-null {@code FileRef} {@code f},
     *     {@code f.isSameFile(f)} should return {@code true}.
     * <li>It is <i>symmetric</i>: for two non-null {@code FileRefs}
     *     {@code f} and {@code g}, {@code f.isSameFile(g)} will equal
     *     {@code g.isSameFile(f)}.
     * <li>It is <i>transitive</i>: for three {@code FileRefs}
     *     {@code f}, {@code g}, and {@code h}, if {@code f.isSameFile(g)} returns
     *     {@code true} and {@code g.isSameFile(h)} returns {@code true}, then
     *     {@code f.isSameFile(h)} will return return {@code true}.
     * </ul>
     *
     * @param   other
     *          The other file reference
     *
     * @return  {@code true} if, and only if, this object and the given object
     *          locate the same file
     *
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to both files.
     *
     * @see java.nio.file.attribute.BasicFileAttributes#fileKey
     */
    boolean isSameFile(FileRef other) throws IOException;

    /**
     * Deletes the file referenced by this object.
     *
     * <p> An implementation may require to examine the file to determine if the
     * file is a directory. Consequently this method may not be atomic with respect
     * to other file system operations.  If the file is a symbolic-link then the
     * link is deleted and not the final target of the link.
     *
     * <p> If the file is a directory then the directory must be empty. In some
     * implementations a directory has entries for special files or links that
     * are created when the directory is created. In such implementations a
     * directory is considered empty when only the special entries exist.
     *
     * <p> On some operating systems it may not be possible to remove a file when
     * it is open and in use by this Java virtual machine or other programs.
     *
     * @throws  NoSuchFileException
     *          If the file does not exist <i>(optional specific exception)</i>
     * @throws  DirectoryNotEmptyException
     *          If the file is a directory and could not otherwise be deleted
     *          because the directory is not empty <i>(optional specific
     *          exception)</i>
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkDelete(String)} method
     *          is invoked to check delete access to the file
     */
    void delete() throws IOException;

    /**
     * Tests this object for equality with another object.
     *
     * <p> If the given object is not a {@code FileRef} then this method
     * immediately returns {@code false}.
     *
     * <p> For two file references to be considered equal requires that they
     * are both the same type of {@code FileRef} and encapsulate components
     * to locate the same file. This method does not access the file system and
     * the file may not exist.
     *
     * <p> This method satisfies the general contract of the {@link
     * java.lang.Object#equals(Object) Object.equals} method. </p>
     *
     * @param   ob   The object to which this object is to be compared
     *
     * @return  {@code true} if, and only if, the given object is a {@code FileRef}
     *          that is identical to this {@code FileRef}
     *
     * @see #isSameFile
     */
    boolean equals(Object ob);

    /**
     * Returns the hash-code value for this object.
     *
     * <p> This method satisfies the general contract of the
     * {@link java.lang.Object#hashCode() Object.hashCode} method.
     */
    int hashCode();
}
