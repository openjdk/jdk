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
import java.io.IOError;

/**
 * A simple visitor of files with default behavior to visit all files and to
 * re-throw I/O errors.
 *
 * <p> Methods in this class may be overridden subject to their general contract.
 *
 * @param   <T>     The type of reference to the files
 *
 * @since 1.7
 */

public class SimpleFileVisitor<T> implements FileVisitor<T> {
    /**
     * Initializes a new instance of this class.
     */
    protected SimpleFileVisitor() {
    }

    /**
     * Throws NullPointerException if obj is null.
     */
    private static void checkNotNull(Object obj) {
        if (obj == null)
            throw new NullPointerException();
    }

    /**
     * Invoked for a directory before entries in the directory are visited.
     *
     * <p> Unless overridden, this method returns {@link FileVisitResult#CONTINUE
     * CONTINUE}.
     */
    @Override
    public FileVisitResult preVisitDirectory(T dir) {
        checkNotNull(dir);
        return FileVisitResult.CONTINUE;
    }

    /**
     * Invoked for a directory that could not be opened.
     *
     * <p> Unless overridden, this method throws {@link IOError} with the I/O
     * exception as cause.
     *
     * @throws  IOError
     *          with the I/O exception thrown when the attempt to open the
     *          directory failed
     */
    @Override
    public FileVisitResult preVisitDirectoryFailed(T dir, IOException exc) {
        checkNotNull(dir);
        checkNotNull(exc);
        throw new IOError(exc);
    }

    /**
     * Invoked for a file in a directory.
     *
     * <p> Unless overridden, this method returns {@link FileVisitResult#CONTINUE
     * CONTINUE}.
     */
    @Override
    public FileVisitResult visitFile(T file, BasicFileAttributes attrs) {
        checkNotNull(file);
        checkNotNull(attrs);
        return FileVisitResult.CONTINUE;
    }

    /**
     * Invoked for a file when its basic file attributes could not be read.
     *
     * <p> Unless overridden, this method throws {@link IOError} with the I/O
     * exception as cause.
     *
     * @throws  IOError
     *          with the I/O exception thrown when the attempt to read the file
     *          attributes failed
     */
    @Override
    public FileVisitResult visitFileFailed(T file, IOException exc) {
        checkNotNull(file);
        checkNotNull(exc);
        throw new IOError(exc);
    }

    /**
     * Invoked for a directory after entries in the directory, and all of their
     * descendants, have been visited.
     *
     * <p> Unless overridden, this method returns {@link FileVisitResult#CONTINUE
     * CONTINUE} if the directory iteration completes without an I/O exception;
     * otherwise this method throws {@link IOError} with the I/O exception as
     * cause.
     *
     * @throws  IOError
     *          if iteration of the directory completed prematurely due to an
     *          I/O error
     */
    @Override
    public FileVisitResult postVisitDirectory(T dir, IOException exc) {
        checkNotNull(dir);
        if (exc != null)
            throw new IOError(exc);
        return FileVisitResult.CONTINUE;
    }
}
