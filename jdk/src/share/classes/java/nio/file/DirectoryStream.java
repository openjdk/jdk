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

import java.util.Iterator;
import java.io.Closeable;
import java.io.IOException;

/**
 * An object to iterate over the entries in a directory. A directory stream
 * allows for convenient use of the for-each construct:
 * <pre>
 *   Path dir = ...
 *   DirectoryStream&lt;Path&gt; stream = dir.newDirectoryStream();
 *   try {
 *       for (Path entry: stream) {
 *         ..
 *       }
 *   } finally {
 *       stream.close();
 *   }
 * </pre>
 *
 * <p><b> A {@code DirectoryStream} is not a general-purpose {@code Iterable}.
 * While this interface extends {@code Iterable}, the {@code iterator} method
 * may only be invoked once to obtain the iterator; a second, or subsequent,
 * call to the {@code iterator} method throws {@code IllegalStateException}. </b>
 *
 * <p> A {@code DirectoryStream} is opened upon creation and is closed by
 * invoking the {@link #close close} method. Closing the directory stream
 * releases any resources associated with the stream. Once a directory stream
 * is closed, all further method invocations on the iterator throw {@link
 * java.util.ConcurrentModificationException} with cause {@link
 * ClosedDirectoryStreamException}.
 *
 * <p> A directory stream is not required to be <i>asynchronously closeable</i>.
 * If a thread is blocked on the directory stream's iterator reading from the
 * directory, and another thread invokes the {@code close} method, then the
 * second thread may block until the read operation is complete.
 *
 * <p> The {@link Iterator#hasNext() hasNext} and {@link Iterator#next() next}
 * methods can encounter an I/O error when iterating over the directory in which
 * case {@code ConcurrentModificationException} is thrown with cause
 * {@link java.io.IOException}. The {@code hasNext} method is guaranteed to
 * read-ahead by at least one element. This means that if the {@code hasNext}
 * method returns {@code true} and is followed by a call to the {@code next}
 * method then it is guaranteed not to fail with a {@code
 * ConcurrentModificationException}.
 *
 * <p> The elements returned by the iterator are in no specific order. Some file
 * systems maintain special links to the directory itself and the directory's
 * parent directory. Entries representing these links are not returned by the
 * iterator.
 *
 * <p> The iterator's {@link Iterator#remove() remove} method removes the
 * directory entry for the last element returned by the iterator, as if by
 * invoking the {@link Path#delete delete} method. If an I/O error or
 * security exception occurs then {@code ConcurrentModificationException} is
 * thrown with the cause.
 *
 * <p> The iterator is <i>weakly consistent</i>. It is thread safe but does not
 * freeze the directory while iterating, so it may (or may not) reflect updates
 * to the directory that occur after the {@code DirectoryStream} is created.
 *
 * @param   <T>     The type of element returned by the iterator
 *
 * @since 1.7
 *
 * @see Path#newDirectoryStream
 */

public interface DirectoryStream<T>
    extends Closeable, Iterable<T>
{
    /**
     * An interface that is implemented by objects that decide if a directory
     * entry should be accepted or filtered. A {@code Filter} is passed as the
     * parameter to the {@link Path#newDirectoryStream(DirectoryStream.Filter)
     * newDirectoryStream} method when opening a directory to iterate over the
     * entries in the directory.
     *
     * @param   <T>     the type of the directory entry
     *
     * @since 1.7
     */
    public static interface Filter<T> {
        /**
         * Decides if the given directory entry should be accepted or filtered.
         *
         * @param   entry
         *          the directory entry to be tested
         *
         * @return  {@code true} if the directory entry should be accepted
         *
         * @throws  IOException
         *          If an I/O error occurs
         */
        boolean accept(T entry) throws IOException;
    }

    /**
     * Returns the iterator associated with this {@code DirectoryStream}.
     *
     * @return  the iterator associated with this {@code DirectoryStream}
     *
     * @throws  IllegalStateException
     *          if this directory stream is closed or the iterator has already
     *          been returned
     */
    @Override
    Iterator<T> iterator();
}
