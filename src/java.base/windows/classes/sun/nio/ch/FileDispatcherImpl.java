/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.CharBuffer;
import jdk.internal.access.JavaIOFileDescriptorAccess;
import jdk.internal.access.SharedSecrets;

class FileDispatcherImpl extends FileDispatcher {
    private static final int MAP_INVALID = -1;
    private static final int MAP_RO = 0;
    private static final int MAP_RW = 1;
    private static final int MAP_PV = 2;

    private static final long ALLOCATION_GRANULARITY;
    private static final int  MAX_DIRECT_TRANSFER_SIZE;

    // set to true if fast file transmission (TransmitFile) is enabled
    private static final boolean FAST_FILE_TRANSFER;

    private static final JavaIOFileDescriptorAccess fdAccess =
        SharedSecrets.getJavaIOFileDescriptorAccess();

    FileDispatcherImpl() { }

    @Override
    boolean needsPositionLock() {
        return true;
    }

    int read(FileDescriptor fd, long address, int len)
        throws IOException
    {
        return read0(fd, address, len);
    }

    int pread(FileDescriptor fd, long address, int len, long position)
        throws IOException
    {
        return pread0(fd, address, len, position);
    }

    long readv(FileDescriptor fd, long address, int len) throws IOException {
        return readv0(fd, address, len);
    }

    int write(FileDescriptor fd, long address, int len) throws IOException {
        return write0(fd, address, len, fdAccess.getAppend(fd));
    }

    int pwrite(FileDescriptor fd, long address, int len, long position)
        throws IOException
    {
        return pwrite0(fd, address, len, position);
    }

    long writev(FileDescriptor fd, long address, int len) throws IOException {
        return writev0(fd, address, len, fdAccess.getAppend(fd));
    }

    long seek(FileDescriptor fd, long offset) throws IOException {
        return seek0(fd, offset);
    }

    int force(FileDescriptor fd, boolean metaData) throws IOException {
        return force0(fd, metaData);
    }

    int truncate(FileDescriptor fd, long size) throws IOException {
        return truncate0(fd, size);
    }

    long size(FileDescriptor fd) throws IOException {
        return size0(fd);
    }

    int available(FileDescriptor fd) throws IOException {
        return available0(fd);
    }

    boolean isOther(FileDescriptor fd) throws IOException {
        return isOther0(fd);
    }

    int lock(FileDescriptor fd, boolean blocking, long pos, long size,
             boolean shared) throws IOException
    {
        return lock0(fd, blocking, pos, size, shared);
    }

    void release(FileDescriptor fd, long pos, long size) throws IOException {
        release0(fd, pos, size);
    }

    void close(FileDescriptor fd) throws IOException {
        fdAccess.close(fd);
    }

    FileDescriptor duplicateForMapping(FileDescriptor fd) throws IOException {
        // on Windows we need to keep a handle to the file
        FileDescriptor result = new FileDescriptor();
        long handle = duplicateHandle(fdAccess.getHandle(fd));
        fdAccess.setHandle(result, handle);
        fdAccess.registerCleanup(result);
        return result;
    }

    boolean canTransferToDirectly(java.nio.channels.SelectableChannel sc) {
        return FAST_FILE_TRANSFER && sc.isBlocking();
    }

    boolean transferToDirectlyNeedsPositionLock() {
        return true;
    }

    boolean canTransferToFromOverlappedMap() {
        return true;
    }


    long allocationGranularity() {
        return ALLOCATION_GRANULARITY;
    }

    long map(FileDescriptor fd, int prot, long position, long length,
             boolean isSync)
        throws IOException
    {
        return map0(fd, prot, position, length, isSync);
    }

    int unmap(long address, long length) {
        return unmap0(address, length);
    }

    int maxDirectTransferSize() {
        return MAX_DIRECT_TRANSFER_SIZE;
    }

    long transferTo(FileDescriptor src, long position, long count,
                    FileDescriptor dst, boolean append) {
        return transferTo0(src, position, count, dst, append);
    }

    long transferFrom(FileDescriptor src, FileDescriptor dst,
                      long position, long count, boolean append) {
        return IOStatus.UNSUPPORTED;
    }

    int setDirectIO(FileDescriptor fd, String path) {
        int result = -1;
        String filePath = path.substring(0, path.lastIndexOf(File.separator));
        CharBuffer buffer = CharBuffer.allocate(filePath.length());
        buffer.put(filePath);
        try {
            result = setDirect0(fd, buffer);
        } catch (IOException e) {
            throw new UnsupportedOperationException
                ("Error setting up DirectIO", e);
        }
        return result;
    }

    static boolean isFastFileTransferRequested() {
        String fileTransferProp =
                System.getProperty("jdk.nio.enableFastFileTransfer", "false");
        return fileTransferProp.isEmpty() ? true : Boolean.parseBoolean(fileTransferProp);
    }

    static {
        IOUtil.load();
        FAST_FILE_TRANSFER       = isFastFileTransferRequested();
        ALLOCATION_GRANULARITY   = allocationGranularity0();
        MAX_DIRECT_TRANSFER_SIZE = maxDirectTransferSize0();
    }

    //-- Native methods

    static native int read0(FileDescriptor fd, long address, int len)
        throws IOException;

    static native int pread0(FileDescriptor fd, long address, int len,
                             long position) throws IOException;

    static native long readv0(FileDescriptor fd, long address, int len)
        throws IOException;

    static native int write0(FileDescriptor fd, long address, int len, boolean append)
        throws IOException;

    static native int pwrite0(FileDescriptor fd, long address, int len,
                             long position) throws IOException;

    static native long writev0(FileDescriptor fd, long address, int len, boolean append)
        throws IOException;

    static native long seek0(FileDescriptor fd, long offset) throws IOException;

    static native int force0(FileDescriptor fd, boolean metaData)
        throws IOException;

    static native int truncate0(FileDescriptor fd, long size)
        throws IOException;

    static native long size0(FileDescriptor fd) throws IOException;

    static native int available0(FileDescriptor fd) throws IOException;

    static native boolean isOther0(FileDescriptor fd) throws IOException;

    static native int lock0(FileDescriptor fd, boolean blocking, long pos,
                            long size, boolean shared) throws IOException;

    static native void release0(FileDescriptor fd, long pos, long size)
        throws IOException;

    static native void close0(FileDescriptor fd) throws IOException;

    static native long duplicateHandle(long fd) throws IOException;

    static native long allocationGranularity0();

    static native long map0(FileDescriptor fd, int prot, long position,
                            long length, boolean isSync)
        throws IOException;

    static native int unmap0(long address, long length);

    static native int maxDirectTransferSize0();

    static native long transferTo0(FileDescriptor src, long position,
                                   long count, FileDescriptor dst,
                                   boolean append);

    static native int setDirect0(FileDescriptor fd, CharBuffer buffer) throws IOException;
}
