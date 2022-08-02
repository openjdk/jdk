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

/**
 * Implements a {@code clone) method for use by {@code UnixCopyFile} on AIX.
 */
final class CloneFile {
    private CloneFile() { }

    /**
     * Clones the file whose path name is {@code src} to that whose path
     * name is {@code dst} using a platform-specific system call.
     *
     * @implSpec
     * The implementation in this class always returns
     * {@code IOStatus.UNSUPPORTED}.
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
        return IOStatus.UNSUPPORTED;
    }
}
