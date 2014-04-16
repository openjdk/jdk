/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Linux specific system calls.
 */

class LinuxNativeDispatcher extends UnixNativeDispatcher {
    private LinuxNativeDispatcher() { }

   /**
    * FILE *setmntent(const char *filename, const char *type);
    */
    static long setmntent(byte[] filename, byte[] type) throws UnixException {
        NativeBuffer pathBuffer = NativeBuffers.asNativeBuffer(filename);
        NativeBuffer typeBuffer = NativeBuffers.asNativeBuffer(type);
        try {
            return setmntent0(pathBuffer.address(), typeBuffer.address());
        } finally {
            typeBuffer.release();
            pathBuffer.release();
        }
    }
    private static native long setmntent0(long pathAddress, long typeAddress)
        throws UnixException;

    /**
     * int getmntent(FILE *fp, struct mnttab *mp, int len);
     */
    static native int getmntent(long fp, UnixMountEntry entry)
        throws UnixException;

    /**
     * int endmntent(FILE* filep);
     */
    static native void endmntent(long stream) throws UnixException;

    /**
     * ssize_t fgetxattr(int filedes, const char *name, void *value, size_t size);
     */
    static int fgetxattr(int filedes, byte[] name, long valueAddress,
                         int valueLen) throws UnixException
    {
        NativeBuffer buffer = NativeBuffers.asNativeBuffer(name);
        try {
            return fgetxattr0(filedes, buffer.address(), valueAddress, valueLen);
        } finally {
            buffer.release();
        }
    }

    private static native int fgetxattr0(int filedes, long nameAddress,
        long valueAddress, int valueLen) throws UnixException;

    /**
     *  fsetxattr(int filedes, const char *name, const void *value, size_t size, int flags);
     */
    static void fsetxattr(int filedes, byte[] name, long valueAddress,
        int valueLen) throws UnixException
    {
        NativeBuffer buffer = NativeBuffers.asNativeBuffer(name);
        try {
            fsetxattr0(filedes, buffer.address(), valueAddress, valueLen);
        } finally {
            buffer.release();
        }
    }

    private static native void fsetxattr0(int filedes, long nameAddress,
        long valueAddress, int valueLen) throws UnixException;

    /**
     * fremovexattr(int filedes, const char *name);
     */
    static void fremovexattr(int filedes, byte[] name) throws UnixException {
        NativeBuffer buffer = NativeBuffers.asNativeBuffer(name);
        try {
            fremovexattr0(filedes, buffer.address());
        } finally {
            buffer.release();
        }
    }

    private static native void fremovexattr0(int filedes, long nameAddress)
        throws UnixException;

    /**
     * size_t flistxattr(int filedes, const char *list, size_t size)
     */
    static native int flistxattr(int filedes, long listAddress, int size)
        throws UnixException;

    // initialize
    private static native void init();

    static {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                System.loadLibrary("nio");
                return null;
        }});
        init();
    }
}
