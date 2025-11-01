/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.ffi.util;

import jdk.internal.ffi.generated.errno.errno_h;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

// Errno utility for macosx platform
public final class ErrnoUtils {
    private ErrnoUtils() {
    }

    private static final long ERRNO_STRING_HOLDER_ARRAY_SIZE = 256L;

    /**
     * Returns an IOException whose detail message combines the provided context with the
     * strerror_r description for the given errno.
     * On success, the message includes both the context and the errno string.
     * If strerror_r fails, the message only includes the context and errno value.
     * @param errno a non-negative POSIX errno value
     * @param context additional context to prefix the error message; must not be null
     * @return an IOException describing the errno
     */
    public static IOException ioExceptionFromErrno(int errno, String context) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(ERRNO_STRING_HOLDER_ARRAY_SIZE);
            if (errno_h.strerror_r(errno, buf, ERRNO_STRING_HOLDER_ARRAY_SIZE) == 0) {
                String errnoMsg = buf.getString(0, StandardCharsets.UTF_8);
                return new IOException(context + " " + errnoMsg);
            } else {
                // failed to convert errno to string - output errno value
                return new IOException(context + " Errno: " + errno);
            }
        }
    }
}
