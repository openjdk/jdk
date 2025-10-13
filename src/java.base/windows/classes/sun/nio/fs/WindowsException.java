/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static sun.nio.fs.WindowsConstants.*;

/**
 * Internal exception thrown when a Win32 call fails.
 */

class WindowsException extends Exception {
    static final long serialVersionUID = 2765039493083748820L;

    private int lastError;
    private String msg;

    WindowsException(int lastError) {
        this.lastError = lastError;
        this.msg = null;
    }

    WindowsException(String msg) {
        this.lastError = 0;
        this.msg = msg;
    }

    int lastError() {
        return lastError;
    }

    String errorString() {
        if (msg == null) {
            msg = WindowsNativeDispatcher.FormatMessage(lastError);
            if (msg == null) {
                msg = "Unknown error: 0x" + Integer.toHexString(lastError);
            }
        }
        return msg;
    }

    @Override
    public String getMessage() {
        return errorString();
    }

    @Override
    public Throwable fillInStackTrace() {
        // This is an internal exception; the stack trace is irrelevant.
        return this;
    }

    private IOException translateToIOException(String file, String other) {
        return switch (lastError()) {
            // not created with last error
            case 0 -> new IOException(errorString());

            // handle specific cases
            case ERROR_FILE_NOT_FOUND, ERROR_PATH_NOT_FOUND
                -> new NoSuchFileException(file, other, null);
            case ERROR_FILE_EXISTS, ERROR_ALREADY_EXISTS
                -> new FileAlreadyExistsException(file, other, null);
            case ERROR_ACCESS_DENIED, ERROR_NETWORK_ACCESS_DENIED, ERROR_PRIVILEGE_NOT_HELD
                -> new AccessDeniedException(file, other, null);

            // fallback to the more general exception
            default -> new FileSystemException(file, other, errorString());
        };
    }

    void rethrowAsIOException(String file) throws IOException {
        IOException x = translateToIOException(file, null);
        throw x;
    }

    void rethrowAsIOException(WindowsPath file, WindowsPath other) throws IOException {
        String a = (file == null) ? null : file.getPathForExceptionMessage();
        String b = (other == null) ? null : other.getPathForExceptionMessage();
        IOException x = translateToIOException(a, b);
        throw x;
    }

    void rethrowAsIOException(WindowsPath file) throws IOException {
        rethrowAsIOException(file, null);
    }

    IOException asIOException(WindowsPath file) {
        return translateToIOException(file.getPathForExceptionMessage(), null);
    }

}
