/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.channels.SeekableByteChannel;
import java.util.Set;
import java.io.IOException;

/**
 * A {@code DirectoryStream} that defines operations on files that are located
 * relative to an open directory. A {@code SecureDirectoryStream} is intended
 * for use by sophisticated or security sensitive applications requiring to
 * traverse file trees or otherwise operate on directories in a race-free manner.
 * Race conditions can arise when a sequence of file operations cannot be
 * carried out in isolation. Each of the file operations defined by this
 * interface specify a relative path. All access to the file is relative
 * to the open directory irrespective of if the directory is moved or replaced
 * by an attacker while the directory is open. A {@code SecureDirectoryStream}
 * may also be used as a virtual <em>working directory</em>.
 *
 * <p> A {@code SecureDirectoryStream} requires corresponding support from the
 * underlying operating system. Where an implementation supports this features
 * then the {@code DirectoryStream} returned by the {@link Files#newDirectoryStream
 * newDirectoryStream} method will be a {@code SecureDirectoryStream} and must
 * be cast to that type in order to invoke the methods defined by this interface.
 *
 * @param <T> The type of element returned by the iterator
 *
 * @since   1.7
 */

public interface SecureDirectoryStream<T>
    extends DirectoryStream<T>
{
    /**
     * Opens the directory identified by the given path, returning a {@code
     * SecureDirectoryStream} to iterate over the entries in the directory.
     *
     * <p> This method works in exactly the manner specified by the {@link
     * Files#newDirectoryStream(Path) newDirectoryStream} method for the case that
     * the {@code path} parameter is an {@link Path#isAbsolute absolute} path.
     * When the parameter is a relative path then the directory to open is
     * relative to this open directory. The {@link
     * LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} option may be used to
     * ensure that this method fails if the file is a symbolic link.
     *
     * <p> The new directory stream, once created, is not dependent upon the
     * directory stream used to create it. Closing this directory stream has no
     * effect upon newly created directory stream.
     *
     * @param   path
     *          the path to the directory to open
     * @param   options
     *          options indicating how symbolic links are handled
     *
     * @return  a new and open {@code SecureDirectoryStream} object
     *
     * @throws  ClosedDirectoryStreamException
     *          if the directory stream is closed
     * @throws  NotDirectoryException
     *          if the file could not otherwise be opened because it is not
     *          a directory <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     */
    SecureDirectoryStream<T> newDirectoryStream(T path, LinkOption... options)
        throws IOException;

    /**
     * Opens or creates a file in this directory, returning a seekable byte
     * channel to access the file.
     *
     * <p> This method works in exactly the manner specified by the {@link
     * Files#newByteChannel Files.newByteChannel} method for the
     * case that the {@code path} parameter is an {@link Path#isAbsolute absolute}
     * path. When the parameter is a relative path then the file to open or
     * create is relative to this open directory. In addition to the options
     * defined by the {@code Files.newByteChannel} method, the {@link
     * LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} option may be used to
     * ensure that this method fails if the file is a symbolic link.
     *
     * <p> The channel, once created, is not dependent upon the directory stream
     * used to create it. Closing this directory stream has no effect upon the
     * channel.
     *
     * @param   path
     *          the path of the file to open or create
     * @param   options
     *          options specifying how the file is opened
     * @param   attrs
     *          an optional list of attributes to set atomically when creating
     *          the file
     *
     * @return  the seekable byte channel
     *
     * @throws  ClosedDirectoryStreamException
     *          if the directory stream is closed
     * @throws  IllegalArgumentException
     *          if the set contains an invalid combination of options
     * @throws  UnsupportedOperationException
     *          if an unsupported open option is specified or the array contains
     *          attributes that cannot be set atomically when creating the file
     * @throws  FileAlreadyExistsException
     *          if a file of that name already exists and the {@link
     *          StandardOpenOption#CREATE_NEW CREATE_NEW} option is specified
     *          <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     */
    SeekableByteChannel newByteChannel(T path,
                                       Set<? extends OpenOption> options,
                                       FileAttribute<?>... attrs)
        throws IOException;

    /**
     * Creates a new and empty file, failing if the file already exists.
     *
     * <p>This method works in exactly the manner specified by
     * {@linkplain Files#createFile
     * Files.createFile}. If the {@code path} parameter is an {@linkplain
     * Path#isAbsolute absolute} path then it locates the file to create. If
     * the parameter is a relative path then it is located relative to this
     * open directory.
     *
     * <p> The {@code attrs} parameter is optional with effects as specified
     * for {@linkplain Files#createFile Files.createFile}.
     *
     * @param   path
     *          the path of the file to create
     * @param   attrs
     *          an optional list of file attributes to set atomically when
     *          creating the file
     *
     * @return  the file
     *
     * @throws  ClosedDirectoryStreamException
     *          if the directory stream is closed
     * @throws  UnsupportedOperationException
     *          if the array contains an attribute that cannot be set atomically
     *          when creating the directory
     * @throws  FileAlreadyExistsException
     *          if a file could not otherwise be created because a file of
     *          that name already exists <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs or the parent directory does not exist
     *
     * @since 26
     */
    T createFile(T path, FileAttribute<?>... attrs)
        throws IOException;

    /**
     * Creates a new directory, failing if a file of that name already exists.
     *
     * <p>This method works in a similar manner to {@linkplain
     * Files#createDirectory Files.createDirectory}. If the {@code path}
     * parameter is an {@linkplain Path#isAbsolute absolute} path then it
     * locates the directory to create. If the parameter is a relative path
     * then it is located relative to this open directory.
     *
     * <p> The {@code attrs} parameter is optional with effects as specified
     * for {@linkplain Files#createDirectory Files.createDirectory}.
     *
     * @param   dir
     *          the path of the directory to create
     * @param   attrs
     *          an optional list of file attributes to set atomically when
     *          creating the directory
     *
     * @return  the directory
     *
     * @throws  ClosedDirectoryStreamException
     *          if the directory stream is closed
     * @throws  UnsupportedOperationException
     *          if the array contains an attribute that cannot be set atomically
     *          when creating the directory
     * @throws  FileAlreadyExistsException
     *          if a directory could not otherwise be created because a file of
     *          that name already exists <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs or the parent directory does not exist
     *
     * @since 26
     */
    T createDirectory(T dir, FileAttribute<?>... attrs)
        throws IOException;

    /**
     * Creates a new link (directory entry) for an existing file <i>(optional
     * operation)</i>.
     *
     * <p>This method works in a similar manner to {@linkplain Files#createLink
     * Files.createLink}.  If the {@code link} parameter is an {@link
     * Path#isAbsolute absolute} path then it locates the link file. If the
     * {@code link} parameter is a relative path then it is located relative to
     * this open directory. If the {@code existing} parameter is an absolute
     * path then it locates the target file (the {@code targetdir} parameter is
     * ignored). If the {@code existing} parameter is a relative path it is
     * located relative to the open directory identified by the {@code
     * targetdir} parameter, unless {@code targetdir} is {@code null}, in which
     * case it is located relative to the current working directory. By default,
     * symbolic links are followed. If the option
     * {@linkplain LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} is present then
     * symbolic links are not followed.
     *
     * @param   link
     *          the link (directory entry) to create
     * @param   targetdir
     *          the destination directory
     * @param   existing
     *          a path to an existing file
     * @param   options
     *          options indicating how symbolic links are handled
     *
     * @return  the path to the link (directory entry)
     *
     * @throws  ClosedDirectoryStreamException
     *          if the directory stream is closed
     * @throws  UnsupportedOperationException
     *          if the implementation does not support adding an existing file
     *          to a directory
     * @throws  FileAlreadyExistsException
     *          if the entry could not otherwise be created because a file of
     *          that name already exists <i>(optional specific exception)</i>
     * @throws  NoSuchFileException
     *          if the file specified by the combination of {@code targetdir}
     *          and {@code existing} does not exist
     * @throws  IOException
     *          if an I/O error occurs
     *
     * @since 26
     */
    T createLink(T link, SecureDirectoryStream<T> targetdir, T existing,
                 LinkOption... options)
        throws IOException;

    /**
     * Creates a symbolic link to a target <i>(optional operation)</i>.
     *
     * <p>This method works in a similar manner to {@linkplain Files#createSymbolicLink
     * Files.createSymbolicLink}.  If the {@code link} parameter is an {@link
     * Path#isAbsolute absolute} path then it locates the link file. If the
     * {@code link} parameter is a relative path then it is located relative to
     * this open directory. The {@code target} parameter is the target of the
     * link and behaves as specified for {@linkplain Files#createSymbolicLink
     * Files.createSymbolicLink}.
     *
     * @param   link
     *          the path of the symbolic link to create
     * @param   target
     *          the target of the symbolic link
     * @param   attrs
     *          the array of attributes to set atomically when creating the
     *          symbolic link
     *
     * @return  the path to the symbolic link
     *
     * @throws  ClosedDirectoryStreamException
     *          if the directory stream is closed
     * @throws  UnsupportedOperationException
     *          if the implementation does not support symbolic links or the
     *          array contains an attribute that cannot be set atomically when
     *          creating the symbolic link
     * @throws  FileAlreadyExistsException
     *          if a file with the name already exists <i>(optional specific
     *          exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     */
    T createSymbolicLink(T link, T target, FileAttribute<?>... attrs)
        throws IOException;

    /**
     * Reads the target of a symbolic link <i>(optional operation)</i>.
     *
     * <p>This method works in a similar manner to {@linkplain Files#readSymbolicLink
     * Files.readSymbolicLink}.  If the {@code link} parameter is an {@link
     * Path#isAbsolute absolute} path then it locates the link file. If the
     * {@code link} parameter is a relative path then it is located relative to
     * this open directory.
     *
     * @param   link
     *          the path to the symbolic link
     *
     * @return  a {@code Path} object representing the target of the link
     *
     * @throws  ClosedDirectoryStreamException
     *          if the directory stream is closed
     * @throws  UnsupportedOperationException
     *          if the implementation does not support symbolic links
     * @throws  NotLinkException
     *          if the target could otherwise not be read because the file
     *          is not a symbolic link <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     */
    T readSymbolicLink(T link) throws IOException;

    /**
     * Deletes a file.
     *
     * <p> Unlike the {@link Files#delete delete()} method, this method does
     * not first examine the file to determine if the file is a directory.
     * Whether a directory is deleted by this method is system dependent and
     * therefore not specified. If the file is a symbolic link, then the link
     * itself, not the final target of the link, is deleted. When the
     * parameter is a relative path then the file to delete is relative to
     * this open directory.
     *
     * @param   path
     *          the path of the file to delete
     *
     * @throws  ClosedDirectoryStreamException
     *          if the directory stream is closed
     * @throws  NoSuchFileException
     *          if the file does not exist <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     */
    void deleteFile(T path) throws IOException;

    /**
     * Deletes a directory.
     *
     * <p> Unlike the {@link Files#delete delete()} method, this method
     * does not first examine the file to determine if the file is a directory.
     * Whether non-directories are deleted by this method is system dependent and
     * therefore not specified. When the parameter is a relative path then the
     * directory to delete is relative to this open directory.
     *
     * @param   path
     *          the path of the directory to delete
     *
     * @throws  ClosedDirectoryStreamException
     *          if the directory stream is closed
     * @throws  NoSuchFileException
     *          if the directory does not exist <i>(optional specific exception)</i>
     * @throws  DirectoryNotEmptyException
     *          if the directory could not otherwise be deleted because it is
     *          not empty <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     */
    void deleteDirectory(T path) throws IOException;

    /**
     * Move a file from this directory to another directory.
     *
     * <p> This method works in a similar manner to {@link Files#move Files.move}
     * when the {@link StandardCopyOption#ATOMIC_MOVE ATOMIC_MOVE} option
     * is specified. That is, this method moves a file as an atomic file system
     * operation. If the {@code srcpath} parameter is an {@link Path#isAbsolute
     * absolute} path then it locates the source file. If the parameter is a
     * relative path then it is located relative to this open directory. If
     * the {@code targetpath} parameter is absolute then it locates the target
     * file (the {@code targetdir} parameter is ignored). If the parameter is
     * a relative path it is located relative to the open directory identified
     * by the {@code targetdir} parameter. In all cases, if the target file
     * exists then it is implementation specific if it is replaced or this
     * method fails.
     *
     * @param   srcpath
     *          the name of the file to move
     * @param   targetdir
     *          the destination directory
     * @param   targetpath
     *          the name to give the file in the destination directory
     *
     * @throws  ClosedDirectoryStreamException
     *          if this or the target directory stream is closed
     * @throws  FileAlreadyExistsException
     *          if the file already exists in the target directory and cannot
     *          be replaced <i>(optional specific exception)</i>
     * @throws  AtomicMoveNotSupportedException
     *          if the file cannot be moved as an atomic file system operation
     * @throws  IOException
     *          if an I/O error occurs
     */
    void move(T srcpath, SecureDirectoryStream<T> targetdir, T targetpath)
        throws IOException;

    /**
     * Returns a new file attribute view to access the file attributes of this
     * directory.
     *
     * <p> The resulting file attribute view can be used to read or update the
     * attributes of this (open) directory. The {@code type} parameter specifies
     * the type of the attribute view and the method returns an instance of that
     * type if supported. Invoking this method to obtain a {@link
     * BasicFileAttributeView} always returns an instance of that class that is
     * bound to this open directory.
     *
     * <p> The state of resulting file attribute view is intimately connected
     * to this directory stream. Once the directory stream is {@link #close closed},
     * then all methods to read or update attributes will throw {@link
     * ClosedDirectoryStreamException ClosedDirectoryStreamException}.
     *
     * @param   <V>
     *          The {@code FileAttributeView} type
     * @param   type
     *          the {@code Class} object corresponding to the file attribute view
     *
     * @return  a new file attribute view of the specified type bound to
     *          this directory stream, or {@code null} if the attribute view
     *          type is not available
     */
    <V extends FileAttributeView> V getFileAttributeView(Class<V> type);

    /**
     * Returns a new file attribute view to access the file attributes of a file
     * in this directory.
     *
     * <p> The resulting file attribute view can be used to read or update the
     * attributes of file in this directory. The {@code type} parameter specifies
     * the type of the attribute view and the method returns an instance of that
     * type if supported. Invoking this method to obtain a {@link
     * BasicFileAttributeView} always returns an instance of that class that is
     * bound to the file in the directory.
     *
     * <p> The state of resulting file attribute view is intimately connected
     * to this directory stream. Once the directory stream {@link #close closed},
     * then all methods to read or update attributes will throw {@link
     * ClosedDirectoryStreamException ClosedDirectoryStreamException}. The
     * file is not required to exist at the time that the file attribute view
     * is created but methods to read or update attributes of the file will
     * fail when invoked and the file does not exist.
     *
     * @param   <V>
     *          The {@code FileAttributeView} type
     * @param   path
     *          the path of the file
     * @param   type
     *          the {@code Class} object corresponding to the file attribute view
     * @param   options
     *          options indicating how symbolic links are handled
     *
     * @return  a new file attribute view of the specified type bound to
     *          this directory stream, or {@code null} if the attribute view
     *          type is not available
     *
     */
    <V extends FileAttributeView> V getFileAttributeView(T path,
                                                         Class<V> type,
                                                         LinkOption... options);
}
