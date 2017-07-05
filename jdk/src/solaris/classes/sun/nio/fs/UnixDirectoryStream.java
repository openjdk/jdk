/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.fs;

import java.nio.file.*;
import java.util.Iterator;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.*;
import java.io.IOException;
import static sun.nio.fs.UnixNativeDispatcher.*;

/**
 * Unix implementation of java.nio.file.DirectoryStream
 */

class UnixDirectoryStream
    implements DirectoryStream<Path>
{
    // path to directory when originally opened
    private final UnixPath dir;

    // directory pointer (returned by opendir)
    private final long dp;

    // filter (may be null)
    private final DirectoryStream.Filter<? super Path> filter;

    // used to coorindate closing of directory stream
    private final ReentrantReadWriteLock streamLock =
        new ReentrantReadWriteLock(true);

    // indicates if directory stream is open (synchronize on closeLock)
    private volatile boolean isClosed;

    // directory iterator
    private Iterator<Path> iterator;

    /**
     * Initializes a new instance
     */
    UnixDirectoryStream(UnixPath dir, long dp, DirectoryStream.Filter<? super Path> filter) {
        this.dir = dir;
        this.dp = dp;
        this.filter = filter;
    }

    protected final UnixPath directory() {
        return dir;
    }

    protected final Lock readLock() {
        return streamLock.readLock();
    }

    protected final Lock writeLock() {
        return streamLock.writeLock();
    }

    protected final boolean isOpen() {
        return !isClosed;
    }

    protected final boolean closeImpl() throws IOException {
        if (!isClosed) {
            isClosed = true;
            try {
                closedir(dp);
            } catch (UnixException x) {
                throw new IOException(x.errorString());
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void close()
        throws IOException
    {
        writeLock().lock();
        try {
            closeImpl();
        } finally {
            writeLock().unlock();
        }
    }

    protected final Iterator<Path> iterator(DirectoryStream<Path> ds) {
        if (isClosed) {
            throw new IllegalStateException("Directory stream is closed");
        }
        synchronized (this) {
            if (iterator != null)
                throw new IllegalStateException("Iterator already obtained");
            iterator = new UnixDirectoryIterator(ds);
            return iterator;
        }
    }

    @Override
    public Iterator<Path> iterator() {
        return iterator(this);
    }

    /**
     * Iterator implementation
     */
    private class UnixDirectoryIterator implements Iterator<Path> {
        private final DirectoryStream<Path> stream;

        // true when at EOF
        private boolean atEof;

        // next entry to return
        private Path nextEntry;

        // previous entry returned by next method (needed by remove method)
        private Path prevEntry;

        UnixDirectoryIterator(DirectoryStream<Path> stream) {
            atEof = false;
            this.stream = stream;
        }

        // Return true if file name is "." or ".."
        private boolean isSelfOrParent(byte[] nameAsBytes) {
            if (nameAsBytes[0] == '.') {
                if ((nameAsBytes.length == 1) ||
                    (nameAsBytes.length == 2 && nameAsBytes[1] == '.')) {
                    return true;
                }
            }
            return false;
        }

        // Returns next entry (or null)
        private Path readNextEntry() {
            assert Thread.holdsLock(this);

            for (;;) {
                byte[] nameAsBytes = null;

                // prevent close while reading
                readLock().lock();
                try {
                    if (isClosed)
                        throwAsConcurrentModificationException(new
                            ClosedDirectoryStreamException());
                    try {
                        nameAsBytes = readdir(dp);
                    } catch (UnixException x) {
                        try {
                            x.rethrowAsIOException(dir);
                        } catch (IOException ioe) {
                            throwAsConcurrentModificationException(ioe);
                        }
                    }
                } finally {
                    readLock().unlock();
                }

                // EOF
                if (nameAsBytes == null) {
                    return null;
                }

                // ignore "." and ".."
                if (!isSelfOrParent(nameAsBytes)) {
                    Path entry = dir.resolve(nameAsBytes);

                    // return entry if no filter or filter accepts it
                    try {
                        if (filter == null || filter.accept(entry))
                            return entry;
                    } catch (IOException ioe) {
                        throwAsConcurrentModificationException(ioe);
                    }
                }
            }
        }

        @Override
        public synchronized boolean hasNext() {
            if (nextEntry == null && !atEof) {
                nextEntry = readNextEntry();

                // at EOF?
                if (nextEntry == null)
                    atEof = true;
            }
            return nextEntry != null;
        }

        @Override
        public synchronized Path next() {
            if (nextEntry == null) {
                if (!atEof) {
                    nextEntry = readNextEntry();
                }
                if (nextEntry == null) {
                    atEof = true;
                    throw new NoSuchElementException();
                }
            }
            prevEntry = nextEntry;
            nextEntry = null;
            return prevEntry;
        }

        @Override
        public void remove() {
            if (isClosed) {
                throwAsConcurrentModificationException(new
                    ClosedDirectoryStreamException());
            }
            Path entry;
            synchronized (this) {
                if (prevEntry == null)
                    throw new IllegalStateException("No previous entry to remove");
                entry = prevEntry;
                prevEntry = null;
            }

            // use (race-free) unlinkat if available
            try {
                if (stream instanceof UnixSecureDirectoryStream) {
                    ((UnixSecureDirectoryStream)stream)
                        .implDelete(entry.getName(), false, 0);
                } else {
                    entry.delete();
                }
            } catch (IOException ioe) {
                throwAsConcurrentModificationException(ioe);
            } catch (SecurityException se) {
                throwAsConcurrentModificationException(se);
            }
        }
    }

    private static void throwAsConcurrentModificationException(Throwable t) {
        ConcurrentModificationException cme = new ConcurrentModificationException();
        cme.initCause(t);
        throw cme;
    }

}
