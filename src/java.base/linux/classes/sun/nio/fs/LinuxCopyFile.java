/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.util.*;
import static sun.nio.fs.LinuxNativeDispatcher.*;
import static sun.nio.fs.UnixConstants.*;

/**
 * Linux implementation of UnixCopyFile
 */

class LinuxCopyFile extends UnixCopyFile {
    LinuxCopyFile() {
        super();
    }

    @Override
    protected void bufferedCopy(int dst, int src, long address,
                                int size, long addressToPollForCancel)
        throws UnixException
    {
        int advice = POSIX_FADV_SEQUENTIAL | // sequential data access
                     POSIX_FADV_NOREUSE    | // will access only once
                     POSIX_FADV_WILLNEED;    // will access in near future
        posix_fadvise(src, 0, 0, advice);

        super.bufferedCopy(dst, src, address, size, addressToPollForCancel);
    }

    @Override
    protected int directCopy(int dst, int src, long addressToPollForCancel)
        throws UnixException
    {
        int advice = POSIX_FADV_SEQUENTIAL | // sequential data access
                     POSIX_FADV_NOREUSE    | // will access only once
                     POSIX_FADV_WILLNEED;    // will access in near future
        posix_fadvise(src, 0, 0, advice);

        return directCopy0(dst, src, addressToPollForCancel);
    }

    // -- native methods --

    /**
     * Copies data between file descriptors {@code src} and {@code dst} using
     * a platform-specific function or system call possibly having kernel
     * support.
     *
     * @param dst destination file descriptor
     * @param src source file descriptor
     * @param addressToPollForCancel address to check for cancellation
     *        (a non-zero value written to this address indicates cancel)
     *
     * @return 0 on success, UNAVAILABLE if the platform function would block,
     *         UNSUPPORTED_CASE if the call does not work with the given
     *         parameters, or UNSUPPORTED if direct copying is not supported
     *         on this platform
     */
    private static native int directCopy0(int dst, int src,
                                          long addressToPollForCancel)
        throws UnixException;
}
