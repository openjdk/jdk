/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jrtfs;

import java.nio.file.DirectoryStream;
import java.nio.file.ClosedDirectoryStreamException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.io.IOException;

final class JrtDirectoryStream implements DirectoryStream<Path> {
    private final JrtFileSystem jrtfs;
    private final byte[] path;
    // prefix to be used for children of this directory
    // so that child path are reported relatively (if needed)
    private final String childPrefix;
    private final DirectoryStream.Filter<? super Path> filter;
    private volatile boolean isClosed;
    private volatile Iterator<Path> itr;

    JrtDirectoryStream(JrtPath jrtPath,
                       DirectoryStream.Filter<? super java.nio.file.Path> filter)
        throws IOException
    {
        this.jrtfs = jrtPath.getFileSystem();
        this.path = jrtPath.getResolvedPath();
        // sanity check
        if (!jrtfs.isDirectory(path))
            throw new NotDirectoryException(jrtPath.toString());

        // absolute path and does not have funky chars in front like /./java.base
        if (jrtPath.isAbsolute() && (path.length == jrtPath.getPathLength())) {
            childPrefix = null;
        } else {
            // cases where directory content needs to modified with prefix
            // like ./java.base, /./java.base, java.base and so on.
            String dirName = jrtPath.toString();
            int idx = dirName.indexOf(JrtFileSystem.getString(path).substring(1));
            childPrefix = dirName.substring(0, idx);
        }
        this.filter = filter;
    }

    @Override
    public synchronized Iterator<Path> iterator() {
        if (isClosed)
            throw new ClosedDirectoryStreamException();
        if (itr != null)
            throw new IllegalStateException("Iterator has already been returned");

        try {
            itr = jrtfs.iteratorOf(path, childPrefix);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new Iterator<Path>() {
            /*
             * next Path value to return from this iterator.
             * null value means hasNext() not called yet
             * or last hasNext() returned false or resulted
             * in exception. If last hasNext() returned true,
             * then this field has non-null value.
             */
            private Path next;

            // get-and-clear and set-next by these methods
            private Path getAndClearNext() {
                assert next != null;
                Path result = this.next;
                this.next = null;
                return result;
            }

            private void setNext(Path path) {
                assert path != null;
                this.next = path;
            }

            // if hasNext() returns true, 'next' field has non-null Path
            @Override
            public synchronized boolean hasNext() {
                if (next != null) {
                    return true;
                }

                if (isClosed) {
                    return false;
                }

                if (filter == null) {
                    if (itr.hasNext()) {
                        setNext(itr.next());
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    while (itr.hasNext()) {
                        Path tmpPath = itr.next();
                        try {
                            if (filter.accept(tmpPath)) {
                                setNext(tmpPath);
                                return true;
                            }
                        } catch (IOException ioe) {
                            throw new DirectoryIteratorException(ioe);
                        }
                    }

                    return false;
                }
            }

            @Override
            public synchronized Path next() {
                if (next != null) {
                    return getAndClearNext();
                }

                if (isClosed) {
                    throw new NoSuchElementException();
                }

                if (next == null && itr.hasNext()) {
                    // missing hasNext() between next() calls.
                    if (hasNext()) {
                        return getAndClearNext();
                    }
                }

                throw new NoSuchElementException();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public synchronized void close() throws IOException {
        isClosed = true;
    }
}
