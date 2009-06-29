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

import java.nio.file.attribute.BasicFileAttributes;
import java.io.IOException;

/**
 * A visitor of files. An implementation of this interface is provided to the
 * {@link Files#walkFileTree walkFileTree} utility method to visit each file
 * in a tree.
 *
 * <p> <b>Usage Examples:</b>
 * Suppose we want to delete a file tree. In that case, each directory should
 * be deleted after the entries in the directory are deleted.
 * <pre>
 *     Path start = ...
 *     Files.walkFileTree(start, new SimpleFileVisitor&lt;Path&gt;() {
 *         &#64;Override
 *         public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
 *             try {
 *                 file.delete();
 *             } catch (IOException exc) {
 *                 // failed to delete, do error handling here
 *             }
 *             return FileVisitResult.CONTINUE;
 *         }
 *         &#64;Override
 *         public FileVisitResult postVisitDirectory(Path dir, IOException e) {
 *             if (e == null) {
 *                 try {
 *                     dir.delete();
 *                 } catch (IOException exc) {
 *                     // failed to delete, do error handling here
 *                 }
 *             } else {
 *                 // directory iteration failed
 *             }
 *             return FileVisitResult.CONTINUE;
 *         }
 *     });
 * </pre>
 * <p> Furthermore, suppose we want to copy a file tree rooted at a source
 * directory to a target location. In that case, symbolic links should be
 * followed and the target directory should be created before the entries in
 * the directory are copied.
 * <pre>
 *     final Path source = ...
 *     final Path target = ...
 *
 *     Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
 *         new SimpleFileVisitor&lt;Path&gt;() {
 *             &#64;Override
 *             public FileVisitResult preVisitDirectory(Path dir) {
 *                 try {
 *                     dir.copyTo(target.resolve(source.relativize(dir)));
 *                 } catch (FileAlreadyExistsException e) {
 *                      // ignore
 *                 } catch (IOException e) {
 *                     // copy failed, do error handling here
 *                     // skip rest of directory and descendants
 *                     return SKIP_SUBTREE;
 *                 }
 *                 return CONTINUE;
 *             }
 *             &#64;Override
 *             public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
 *                 try {
 *                     file.copyTo(target.resolve(source.relativize(file)));
 *                 } catch (IOException e) {
 *                     // copy failed, do error handling here
 *                 }
 *                 return CONTINUE;
 *             }
 *         });
 * </pre>
 *
 * @since 1.7
 */

public interface FileVisitor<T> {

    /**
     * Invoked for a directory before entries in the directory are visited.
     *
     * <p> If this method returns {@link FileVisitResult#CONTINUE CONTINUE},
     * then entries in the directory are visited. If this method returns {@link
     * FileVisitResult#SKIP_SUBTREE SKIP_SUBTREE} or {@link
     * FileVisitResult#SKIP_SIBLINGS SKIP_SIBLINGS} then entries in the
     * directory (and any descendants) will not be visited.
     *
     * @param   dir
     *          a reference to the directory
     *
     * @return  the visit result
     */
    FileVisitResult preVisitDirectory(T dir);

    /**
     * Invoked for a directory that could not be opened.
     *
     * @param   dir
     *          a reference to the directory
     * @param   exc
     *          the I/O exception thrown from the attempt to open the directory
     *
     * @return  the visit result
     */
    FileVisitResult preVisitDirectoryFailed(T dir, IOException exc);

    /**
     * Invoked for a file in a directory.
     *
     * @param   file
     *          a reference to the file
     * @param   attrs
     *          the file's basic attributes
     *
     * @return  the visit result
     */
    FileVisitResult visitFile(T file, BasicFileAttributes attrs);

    /**
     * Invoked for a file when its basic file attributes could not be read.
     *
     * @param   file
     *          a reference to the file
     * @param   exc
     *          the I/O exception thrown from the attempt to read the file
     *          attributes
     *
     * @return  the visit result
     */
    FileVisitResult visitFileFailed(T file, IOException exc);

    /**
     * Invoked for a directory after entries in the directory, and all of their
     * descendants, have been visited. This method is also invoked when iteration
     * of the directory completes prematurely (by a {@link #visitFile visitFile}
     * method returning {@link FileVisitResult#SKIP_SIBLINGS SKIP_SIBLINGS},
     * or an I/O error when iterating over the directory).
     *
     * @param   dir
     *          a reference to the directory
     * @param   exc
     *          {@code null} if the iteration of the directory completes without
     *          an error; otherwise the I/O exception that caused the iteration
     *          of the directory to complete prematurely
     *
     * @return  the visit result
     */
    FileVisitResult postVisitDirectory(T dir, IOException exc);
}
