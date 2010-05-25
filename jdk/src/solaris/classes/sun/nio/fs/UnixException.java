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
import java.io.IOException;

/**
 * Internal exception thrown by native methods when error detected.
 */

class UnixException extends Exception {
    static final long serialVersionUID = 7227016794320723218L;

    private int errno;
    private String msg;

    UnixException(int errno) {
        this.errno = errno;
        this.msg = null;
    }

    UnixException(String msg) {
        this.errno = 0;
        this.msg = msg;
    }

    int errno() {
        return errno;
    }

    void setError(int errno) {
        this.errno = errno;
        this.msg = null;
    }

    String errorString() {
        if (msg != null) {
            return msg;
        } else {
            return new String(UnixNativeDispatcher.strerror(errno()));
        }
    }

    @Override
    public String getMessage() {
        return errorString();
    }

    /**
     * Map well known errors to specific exceptions where possible; otherwise
     * return more general FileSystemException.
     */
    private IOException translateToIOException(String file, String other) {
        // created with message rather than errno
        if (msg != null)
            return new IOException(msg);

        // handle specific cases
        if (errno() == UnixConstants.EACCES)
            return new AccessDeniedException(file, other, null);
        if (errno() == UnixConstants.ENOENT)
            return new NoSuchFileException(file, other, null);
        if (errno() == UnixConstants.EEXIST)
            return new FileAlreadyExistsException(file, other, null);

        // fallback to the more general exception
        return new FileSystemException(file, other, errorString());
    }

    void rethrowAsIOException(String file) throws IOException {
        IOException x = translateToIOException(file, null);
        throw x;
    }

    void rethrowAsIOException(UnixPath file, UnixPath other) throws IOException {
        String a = (file == null) ? null : file.getPathForExecptionMessage();
        String b = (other == null) ? null : other.getPathForExecptionMessage();
        IOException x = translateToIOException(a, b);
        throw x;
    }

    void rethrowAsIOException(UnixPath file) throws IOException {
        rethrowAsIOException(file, null);
    }

    IOException asIOException(UnixPath file) {
        return translateToIOException(file.getPathForExecptionMessage(), null);
    }
}
