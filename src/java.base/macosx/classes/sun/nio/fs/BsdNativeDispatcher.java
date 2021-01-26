/*
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Bsd specific system calls.
 */

class BsdNativeDispatcher extends UnixNativeDispatcher {
    protected BsdNativeDispatcher() { }

   /**
    * struct fsstat_iter *getfsstat();
    */
    static native long getfsstat() throws UnixException;

   /**
    * int fsstatEntry(struct fsstat_iter * iter, UnixMountEntry entry);
    */
    static native int fsstatEntry(long iter, UnixMountEntry entry)
        throws UnixException;

   /**
    * void endfsstat(struct fsstat_iter * iter);
    */
    static native void endfsstat(long iter) throws UnixException;

    /**
     * int statfs(const char *path, struct statfs *buf);
     * returns buf->f_mntonname (directory on which mounted)
     */
    static byte[] getmntonname(UnixPath path) throws UnixException {
        NativeBuffer pathBuffer = copyToNativeBuffer(path);
        try {
            return getmntonname0(pathBuffer.address());
        } finally {
            pathBuffer.release();
        }
    }
    static native byte[] getmntonname0(long pathAddress) throws UnixException;

    /**
     * ssize_t fgetxattr(int fd, const char *name, void *value, size_t size,
     *  u_int32_t position, int options);
     */
    static int fgetxattr(int fd, byte[] name, long valueAddress,
                         int valueLen) throws UnixException
    {
        NativeBuffer buffer = NativeBuffers.asNativeBuffer(name);
        try {
            return fgetxattr0(fd, buffer.address(), valueAddress, valueLen, 0L, 0);
        } finally {
            buffer.release();
        }
    }

    private static native int fgetxattr0(int fd, long nameAddress,
        long valueAddress, int valueLen, long position, int options) throws UnixException;

    /**
     * int fsetxattr(int fd, const char *name, void *value, size_t size,
     *  u_int32_t position, int options);
     */
    static void fsetxattr(int fd, byte[] name, long valueAddress,
                          int valueLen) throws UnixException
    {
        NativeBuffer buffer = NativeBuffers.asNativeBuffer(name);
        try {
            fsetxattr0(fd, buffer.address(), valueAddress, valueLen, 0L, 0);
        } finally {
            buffer.release();
        }
    }

    private static native void fsetxattr0(int fd, long nameAddress,
        long valueAddress, int valueLen, long position, int options) throws UnixException;

    /**
     * int fremovexattr(int fd, const char *name, int options);
     */
    static void fremovexattr(int fd, byte[] name) throws UnixException {
        NativeBuffer buffer = NativeBuffers.asNativeBuffer(name);
        try {
            fremovexattr0(fd, buffer.address(), 0);
        } finally {
            buffer.release();
        }
    }

    private static native void fremovexattr0(int fd, long nameAddress, int options)
        throws UnixException;

    /**
     * ssize_t flistxattr(int fd, char *namebuf, size_t size, int options);
     */
    static int flistxattr(int fd, long nameBufAddress, int size) throws UnixException {
        return flistxattr0(fd, nameBufAddress, size, 0);
    }

    private static native int flistxattr0(int fd, long nameBufAddress, int size,
        int options) throws UnixException;

    // initialize field IDs
    private static native void initIDs();

    static {
         initIDs();
    }
}
