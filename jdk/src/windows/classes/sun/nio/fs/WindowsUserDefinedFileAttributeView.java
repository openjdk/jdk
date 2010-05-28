/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.fs;

import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.io.IOException;
import java.util.*;
import sun.misc.Unsafe;

import static sun.nio.fs.WindowsNativeDispatcher.*;
import static sun.nio.fs.WindowsConstants.*;

/**
 * Windows emulation of NamedAttributeView using Alternative Data Streams
 */

class WindowsUserDefinedFileAttributeView
    extends AbstractUserDefinedFileAttributeView
{
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    // syntax to address named streams
    private String join(String file, String name) {
        if (name == null)
            throw new NullPointerException("'name' is null");
        return file + ":" + name;
    }
    private String join(WindowsPath file, String name) throws WindowsException {
        return join(file.getPathForWin32Calls(), name);
    }

    private final WindowsPath file;
    private final boolean followLinks;

    WindowsUserDefinedFileAttributeView(WindowsPath file, boolean followLinks) {
        this.file = file;
        this.followLinks = followLinks;
    }

    // enumerates the file streams using FindFirstStream/FindNextStream APIs.
    private List<String> listUsingStreamEnumeration() throws IOException {
        List<String> list = new ArrayList<String>();
        try {
            FirstStream first = FindFirstStream(file.getPathForWin32Calls());
            if (first != null) {
                long handle = first.handle();
                try {
                    // first stream is always ::$DATA for files
                    String name = first.name();
                    if (!name.equals("::$DATA")) {
                        String[] segs = name.split(":");
                        list.add(segs[1]);
                    }
                    while ((name = FindNextStream(handle)) != null) {
                        String[] segs = name.split(":");
                        list.add(segs[1]);
                    }
                } finally {
                    FindClose(handle);
                }
            }
        } catch (WindowsException x) {
            x.rethrowAsIOException(file);
        }
        return Collections.unmodifiableList(list);
    }

    // enumerates the file streams by reading the stream headers using
    // BackupRead
    private List<String> listUsingBackupRead() throws IOException {
        long handle = -1L;
        try {
            int flags = FILE_FLAG_BACKUP_SEMANTICS;
            if (!followLinks && file.getFileSystem().supportsLinks())
                flags |= FILE_FLAG_OPEN_REPARSE_POINT;

            handle = CreateFile(file.getPathForWin32Calls(),
                                GENERIC_READ,
                                FILE_SHARE_READ, // no write as we depend on file size
                                OPEN_EXISTING,
                                flags);
        } catch (WindowsException x) {
            x.rethrowAsIOException(file);
        }

        // buffer to read stream header and stream name.
        final int BUFFER_SIZE = 4096;
        NativeBuffer buffer = null;

        // result with names of alternative data streams
        final List<String> list = new ArrayList<String>();

        try {
            buffer = NativeBuffers.getNativeBuffer(BUFFER_SIZE);
            long address = buffer.address();

            /**
             * typedef struct _WIN32_STREAM_ID {
             *     DWORD dwStreamId;
             *     DWORD dwStreamAttributes;
             *     LARGE_INTEGER Size;
             *     DWORD dwStreamNameSize;
             *     WCHAR cStreamName[ANYSIZE_ARRAY];
             * } WIN32_STREAM_ID;
             */
            final int SIZEOF_STREAM_HEADER      = 20;
            final int OFFSETOF_STREAM_ID        = 0;
            final int OFFSETOF_STREAM_SIZE      = 8;
            final int OFFSETOF_STREAM_NAME_SIZE = 16;

            long context = 0L;
            try {
                for (;;) {
                    // read stream header
                    BackupResult result = BackupRead(handle, address,
                       SIZEOF_STREAM_HEADER, false, context);
                    context = result.context();
                    if (result.bytesTransferred() == 0)
                        break;

                    int streamId = unsafe.getInt(address + OFFSETOF_STREAM_ID);
                    long streamSize = unsafe.getLong(address + OFFSETOF_STREAM_SIZE);
                    int nameSize = unsafe.getInt(address + OFFSETOF_STREAM_NAME_SIZE);

                    // read stream name
                    if (nameSize > 0) {
                        result = BackupRead(handle, address, nameSize, false, context);
                        if (result.bytesTransferred() != nameSize)
                            break;
                    }

                    // check for alternative data stream
                    if (streamId == BACKUP_ALTERNATE_DATA) {
                        char[] nameAsArray = new char[nameSize/2];
                        unsafe.copyMemory(null, address, nameAsArray,
                            Unsafe.ARRAY_CHAR_BASE_OFFSET, nameSize);

                        String[] segs = new String(nameAsArray).split(":");
                        if (segs.length == 3)
                            list.add(segs[1]);
                    }

                    // sparse blocks not currently handled as documentation
                    // is not sufficient on how the spase block can be skipped.
                    if (streamId == BACKUP_SPARSE_BLOCK) {
                        throw new IOException("Spare blocks not handled");
                    }

                    // seek to end of stream
                    if (streamSize > 0L) {
                        BackupSeek(handle, streamSize, context);
                    }
                }
            } catch (WindowsException x) {
                // failed to read or seek
                throw new IOException(x.errorString());
            } finally {
                // release context
                if (context != 0L) {
                   try {
                       BackupRead(handle, 0L, 0, true, context);
                   } catch (WindowsException ignore) { }
                }
            }
        } finally {
            if (buffer != null)
                buffer.release();
            CloseHandle(handle);
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public List<String> list() throws IOException  {
        if (System.getSecurityManager() != null)
            checkAccess(file.getPathForPermissionCheck(), true, false);
        // use stream APIs on Windwos Server 2003 and newer
        if (file.getFileSystem().supportsStreamEnumeration()) {
            return listUsingStreamEnumeration();
        } else {
            return listUsingBackupRead();
        }
    }

    @Override
    public int size(String name) throws IOException  {
        if (System.getSecurityManager() != null)
            checkAccess(file.getPathForPermissionCheck(), true, false);

        // wrap with channel
        FileChannel fc = null;
        try {
            Set<OpenOption> opts = new HashSet<OpenOption>();
            opts.add(READ);
            if (!followLinks)
                opts.add(WindowsChannelFactory.OPEN_REPARSE_POINT);
            fc = WindowsChannelFactory
                .newFileChannel(join(file, name), null, opts, 0L);
        } catch (WindowsException x) {
            x.rethrowAsIOException(join(file.getPathForPermissionCheck(), name));
        }
        try {
            long size = fc.size();
            if (size > Integer.MAX_VALUE)
                throw new ArithmeticException("Stream too large");
            return (int)size;
        } finally {
            fc.close();
        }
    }

    @Override
    public int read(String name, ByteBuffer dst) throws IOException {
        if (System.getSecurityManager() != null)
            checkAccess(file.getPathForPermissionCheck(), true, false);

        // wrap with channel
        FileChannel fc = null;
        try {
            Set<OpenOption> opts = new HashSet<OpenOption>();
            opts.add(READ);
            if (!followLinks)
                opts.add(WindowsChannelFactory.OPEN_REPARSE_POINT);
            fc = WindowsChannelFactory
                .newFileChannel(join(file, name), null, opts, 0L);
        } catch (WindowsException x) {
            x.rethrowAsIOException(join(file.getPathForPermissionCheck(), name));
        }

        // read to EOF (nothing we can do if I/O error occurs)
        try {
            if (fc.size() > dst.remaining())
                throw new IOException("Stream too large");
            int total = 0;
            while (dst.hasRemaining()) {
                int n = fc.read(dst);
                if (n < 0)
                    break;
                total += n;
            }
            return total;
        } finally {
            fc.close();
        }
    }

    @Override
    public int write(String name, ByteBuffer src) throws IOException {
        if (System.getSecurityManager() != null)
            checkAccess(file.getPathForPermissionCheck(), false, true);

        /**
         * Creating a named stream will cause the unnamed stream to be created
         * if it doesn't already exist. To avoid this we open the unnamed stream
         * for reading and hope it isn't deleted/moved while we create or
         * replace the named stream. Opening the file without sharing options
         * may cause sharing violations with other programs that are accessing
         * the unnamed stream.
         */
        long handle = -1L;
        try {
            int flags = FILE_FLAG_BACKUP_SEMANTICS;
            if (!followLinks)
                flags |= FILE_FLAG_OPEN_REPARSE_POINT;

            handle = CreateFile(file.getPathForWin32Calls(),
                                GENERIC_READ,
                                (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE),
                                OPEN_EXISTING,
                                flags);
        } catch (WindowsException x) {
            x.rethrowAsIOException(file);
        }
        try {
            Set<OpenOption> opts = new HashSet<OpenOption>();
            if (!followLinks)
                opts.add(WindowsChannelFactory.OPEN_REPARSE_POINT);
            opts.add(CREATE);
            opts.add(WRITE);
            opts.add(StandardOpenOption.TRUNCATE_EXISTING);
            FileChannel named = null;
            try {
                named = WindowsChannelFactory
                    .newFileChannel(join(file, name), null, opts, 0L);
            } catch (WindowsException x) {
                x.rethrowAsIOException(join(file.getPathForPermissionCheck(), name));
            }
            // write value (nothing we can do if I/O error occurs)
            try {
                int rem = src.remaining();
                while (src.hasRemaining()) {
                    named.write(src);
                }
                return rem;
            } finally {
                named.close();
            }
        } finally {
            CloseHandle(handle);
        }
    }

    @Override
    public void delete(String name) throws IOException {
        if (System.getSecurityManager() != null)
            checkAccess(file.getPathForPermissionCheck(), false, true);

        String path = WindowsLinkSupport.getFinalPath(file, followLinks);
        String toDelete = join(path, name);
        try {
            DeleteFile(toDelete);
        } catch (WindowsException x) {
            x.rethrowAsIOException(toDelete);
        }
    }
}
