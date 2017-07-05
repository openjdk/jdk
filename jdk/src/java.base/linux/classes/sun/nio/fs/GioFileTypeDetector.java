/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * File type detector that uses the GNOME I/O library to guess the
 * MIME type of a file.
 */

public class GioFileTypeDetector
    extends AbstractFileTypeDetector
{
    // true if GIO is available
    private final boolean gioAvailable;

    public GioFileTypeDetector() {
        gioAvailable = initializeGio();
    }

    @Override
    public String implProbeContentType(Path obj) throws IOException {
        if (!gioAvailable)
            return null;
        if (!(obj instanceof UnixPath))
            return null;

        UnixPath path = (UnixPath)obj;
        NativeBuffer buffer = NativeBuffers.asNativeBuffer(path.getByteArrayForSysCalls());
        try {
            // GIO may access file so need permission check
            path.checkRead();
            byte[] type = probeGio(buffer.address());
            return (type == null) ? null : Util.toString(type);
        } finally {
            buffer.release();
        }

    }

    // GIO
    private static native boolean initializeGio();
    //
    // The probeGIO() method is synchronized to avert potential problems
    // such as crashes due to a suspected lack of thread safety in GIO.
    //
    private static synchronized native byte[] probeGio(long pathAddress);

    static {
        AccessController.doPrivileged(new PrivilegedAction<>() {
            public Void run() {
                System.loadLibrary("nio");
                return null;
        }});
    }
}
