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

import java.io.IOException;
import sun.nio.ch.IOStatus;
import static sun.nio.fs.UnixConstants.*;
import static sun.nio.fs.UnixNativeDispatcher.*;

/**
 * Implements a {@code clone) method for use by {@code UnixCopyFile} on Linux.
 */
final class CloneFile {
    private CloneFile() { }

    private static UnixException catEx(UnixException x, UnixException y) {
        assert x != null || y != null;
        UnixException ue = y;
        if (x != null) {
            ue = x;
            if (y != null) {
                ue.addSuppressed(y);
            }
        }
        return ue;
    }

    /**
     * Clones the file whose path name is {@code src} to that whose path
     * name is {@code dst} using the {@code ioctl} system call with the
     * {@code FICLONE} request code.
     *
     * @param src the path of the source file
     * @param dst the path of the desintation file (clone)
     * @param followLinks whether to follow links
     *
     * @return 0 on success, IOStatus.UNSUPPORTED_CASE if the call does not work
     *         with the given parameters, or IOStatus.UNSUPPORTED if cloning is
     *         not supported on this platform
     */
    static int clone(UnixPath src, UnixPath dst, boolean followLinks)
        throws IOException {
        int srcFD = 0;
        try {
            srcFD = open(src, O_RDONLY, 0);
        } catch (UnixException x) {
            x.rethrowAsIOException(src);
            return IOStatus.THROWN;
        }

        int dstFD = 0;
        try {
            dstFD = open(dst, O_CREAT | O_WRONLY, 0666);
        } catch (UnixException x) {
            try {
                close(srcFD);
            } catch (UnixException y) {
                catEx(x, y).rethrowAsIOException(src, dst);
                return IOStatus.THROWN;
            }
            x.rethrowAsIOException(dst);
            return IOStatus.THROWN;
        }

        UnixException ioctlEx = null;
        int result;
        try {
            result = LinuxNativeDispatcher.ioctl_ficlone(dstFD, srcFD);
        } catch (UnixException x) {
            switch (x.errno()) {
                case EINVAL:
                    result = IOStatus.UNSUPPORTED;
                    break;
                case EPERM:
                    ioctlEx = x;
                    result = IOStatus.THROWN;
                    break;
                default:
                    result = IOStatus.UNSUPPORTED_CASE;
                    break;
            }
        }

        UnixException ue = ioctlEx;
        UnixPath s = null;
        UnixPath d = null;

        try {
            close(dstFD);
        } catch (UnixException x) {
            ue = catEx(ue, x);
            d = dst;
        }

        // delete dst to avoid later exception in Java layer
        if (result != 0) {
            try {
                unlink(dst);
            } catch (UnixException x) {
                ue = catEx(ue, x);
                d = dst;
            }
        }

        try {
            close(srcFD);
        } catch (UnixException x) {
            ue = catEx(ue, x);
            s = src;
        }

        if (ue != null) {
            if (ioctlEx != null)
                throw new IOException(ioctlEx.errorString(), ioctlEx);
            else if (s != null && d != null)
                ue.rethrowAsIOException(s, d);
            else
                ue.rethrowAsIOException(s != null ? s : d);
            return IOStatus.THROWN;
        }

        return result;
    }
}
