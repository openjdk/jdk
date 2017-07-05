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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.io.IOException;

import static sun.nio.fs.WindowsNativeDispatcher.*;
import static sun.nio.fs.WindowsConstants.*;

/**
 * Windows implementation of DirectoryStream
 */

class WindowsDirectoryStream
    implements DirectoryStream<Path>
{
    private final WindowsPath dir;
    private final DirectoryStream.Filter<? super Path> filter;

    // handle to directory
    private final long handle;
    // first entry in the directory
    private final String firstName;

    // buffer for WIN32_FIND_DATA structure that receives information about file
    private final NativeBuffer findDataBuffer;

    private final Object closeLock = new Object();

    // need closeLock to access these
    private boolean isOpen = true;
    private Iterator<Path> iterator;


    WindowsDirectoryStream(WindowsPath dir, DirectoryStream.Filter<? super Path> filter)
        throws IOException
    {
        this.dir = dir;
        this.filter = filter;

        try {
            // Need to append * or \* to match entries in directory.
            String search = dir.getPathForWin32Calls();
            char last = search.charAt(search.length() -1);
            if (last == ':' || last == '\\') {
                search += "*";
            } else {
                search += "\\*";
            }

            FirstFile first = FindFirstFile(search);
            this.handle = first.handle();
            this.firstName = first.name();
            this.findDataBuffer = WindowsFileAttributes.getBufferForFindData();
        } catch (WindowsException x) {
            if (x.lastError() == ERROR_DIRECTORY) {
                throw new NotDirectoryException(dir.getPathForExceptionMessage());
            }
            x.rethrowAsIOException(dir);

            // keep compiler happy
            throw new AssertionError();
        }
    }

    @Override
    public void close()
        throws IOException
    {
        synchronized (closeLock) {
            if (!isOpen)
                return;
            isOpen = false;
        }
        findDataBuffer.release();
        try {
            FindClose(handle);
        } catch (WindowsException x) {
            x.rethrowAsIOException(dir);
        }
    }

    @Override
    public Iterator<Path> iterator() {
        if (!isOpen) {
            throw new IllegalStateException("Directory stream is closed");
        }
        synchronized (this) {
            if (iterator != null)
                throw new IllegalStateException("Iterator already obtained");
            iterator = new WindowsDirectoryIterator(firstName);
            return iterator;
        }
    }

    private static void throwAsConcurrentModificationException(Throwable t) {
        ConcurrentModificationException cme = new ConcurrentModificationException();
        cme.initCause(t);
        throw cme;
    }

    private class WindowsDirectoryIterator implements Iterator<Path> {
        private boolean atEof;
        private String first;
        private Path nextEntry;
        private Path prevEntry;

        WindowsDirectoryIterator(String first) {
            atEof = false;
            this.first = first;
        }

        // applies filter and also ignores "." and ".."
        private Path acceptEntry(String s, BasicFileAttributes attrs) {
            if (s.equals(".") || s.equals(".."))
                return null;
            if (dir.needsSlashWhenResolving()) {
                StringBuilder sb = new StringBuilder(dir.toString());
                sb.append('\\');
                sb.append(s);
                s = sb.toString();
            } else {
                s = dir + s;
            }
            Path entry = WindowsPath
                .createFromNormalizedPath(dir.getFileSystem(), s, attrs);
            try {
                if (filter.accept(entry))
                    return entry;
            } catch (IOException ioe) {
                throwAsConcurrentModificationException(ioe);
            }
            return null;
        }

        // reads next directory entry
        private Path readNextEntry() {
            // handle first element returned by search
            if (first != null) {
                nextEntry = acceptEntry(first, null);
                first = null;
                if (nextEntry != null)
                    return nextEntry;
            }

            for (;;) {
                String name = null;
                WindowsFileAttributes attrs;

                // synchronize on closeLock to prevent close while reading
                synchronized (closeLock) {
                    if (!isOpen)
                        throwAsConcurrentModificationException(new
                            IllegalStateException("Directory stream is closed"));
                    try {
                        name = FindNextFile(handle, findDataBuffer.address());
                        if (name == null) {
                            // NO_MORE_FILES
                            return null;
                        }
                    } catch (WindowsException x) {
                        try {
                            x.rethrowAsIOException(dir);
                        } catch (IOException ioe) {
                            throwAsConcurrentModificationException(ioe);
                        }
                    }

                    // grab the attributes from the WIN32_FIND_DATA structure
                    // (needs to be done while holding closeLock because close
                    // will release the buffer)
                    attrs = WindowsFileAttributes
                        .fromFindData(findDataBuffer.address());
                }

                // return entry if accepted by filter
                Path entry = acceptEntry(name, attrs);
                if (entry != null)
                    return entry;
            }
        }

        @Override
        public synchronized boolean hasNext() {
            if (nextEntry == null && !atEof) {
                nextEntry = readNextEntry();
                atEof = (nextEntry == null);
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
            if (!isOpen) {
                throw new IllegalStateException("Directory stream is closed");
            }
            Path entry;
            synchronized (this) {
                if (prevEntry == null)
                    throw new IllegalStateException("no last element");
                entry = prevEntry;
                prevEntry = null;
            }
            try {
                entry.delete();
            } catch (IOException ioe) {
                throwAsConcurrentModificationException(ioe);
            } catch (SecurityException se) {
                throwAsConcurrentModificationException(se);
            }
        }
    }
}
