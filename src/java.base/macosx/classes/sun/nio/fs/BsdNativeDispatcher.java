/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
        try (NativeBuffer pathBuffer = copyToNativeBuffer(path)) {
            return getmntonname0(pathBuffer.address());
        }
    }
    static native byte[] getmntonname0(long pathAddress) throws UnixException;

    /**
     * int clonefile(const char * src, const char * dst, int flags);
     */
    static int clonefile(UnixPath src, UnixPath dst, int flags)
        throws UnixException
    {
        try (NativeBuffer srcBuffer = copyToNativeBuffer(src);
            NativeBuffer dstBuffer = copyToNativeBuffer(dst)) {
            return clonefile0(srcBuffer.address(), dstBuffer.address(), flags);
        }
    }
    private static native int clonefile0(long srcAddress, long dstAddress,
                                         int flags);

    /**
     * setattrlist(const char* path, struct attrlist* attrList, void* attrBuf,
     *             size_t attrBufSize, unsigned long options)
     */
    static void setattrlist(UnixPath path, int commonattr, long modTime,
                            long accTime, long createTime, long options)
        throws UnixException
    {
        try (NativeBuffer buffer = copyToNativeBuffer(path)) {
            setattrlist0(buffer.address(), commonattr, modTime, accTime,
                         createTime, options);
        }
    }
    private static native void setattrlist0(long pathAddress, int commonattr,
                                            long modTime, long accTime,
                                            long createTime, long options)
        throws UnixException;

    /**
     * fsetattrlist(int fd, struct attrlist* attrList, void* attrBuf,
     *              size_t attrBufSize, unsigned long options)
     */
    static void fsetattrlist(int fd, int commonattr, long modTime,
                             long accTime, long createTime, long options)
        throws UnixException
    {
        fsetattrlist0(fd, commonattr, modTime, accTime, createTime, options);
    }
    private static native void fsetattrlist0(int fd, int commonattr,
                                             long modTime, long accTime,
                                             long createTime, long options)
        throws UnixException;

    // initialize field IDs
    private static native void initIDs();

    static {
         initIDs();
    }
}
