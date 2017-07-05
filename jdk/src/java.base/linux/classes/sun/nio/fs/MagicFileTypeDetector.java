/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * File type detector that uses the libmagic to guess the MIME type of a file.
 */

class MagicFileTypeDetector extends AbstractFileTypeDetector {

    private static final String UNKNOWN_MIME_TYPE = "application/octet-stream";

    // true if libmagic is available and successfully loaded
    private final boolean libmagicAvailable;

    public MagicFileTypeDetector() {
        libmagicAvailable = initialize0();
    }

    @Override
    protected String implProbeContentType(Path obj) throws IOException {
        if (!libmagicAvailable || !(obj instanceof UnixPath))
            return null;

        UnixPath path = (UnixPath) obj;
        path.checkRead();

        NativeBuffer buffer = NativeBuffers.asNativeBuffer(path.getByteArrayForSysCalls());
        try {
            byte[] type = probe0(buffer.address());
            String mimeType = (type == null) ? null : new String(type);
            return UNKNOWN_MIME_TYPE.equals(mimeType) ? null : mimeType;
        } finally {
            buffer.release();
        }
    }

    private static native boolean initialize0();

    private static native byte[] probe0(long pathAddress);

    static {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                System.loadLibrary("nio");
                return null;
            }
        });
    }
}
