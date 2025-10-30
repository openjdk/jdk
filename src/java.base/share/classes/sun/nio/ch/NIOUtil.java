/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * File-descriptor based I/O utilities that are shared by NIO classes.
 */

public final class NIOUtil {

    private NIOUtil() { }                // No instantiation

    public static FileDescriptor newFD(int i) {
        FileDescriptor fd = new FileDescriptor();
        IOUtil.setfdVal(fd, i);
        return fd;
    }

    /**
     * Returns two file descriptors for a pipe encoded in a long.
     * The read end of the pipe is returned in the high 32 bits,
     * while the write end is returned in the low 32 bits.
     */
    static native long makePipe(boolean blocking) throws IOException;

    static native int write1(int fd, byte b) throws IOException;

    /**
     * Read and discard all bytes.
     */
    static native boolean drain(int fd) throws IOException;

    /**
     * Read and discard at most one byte
     * @return the number of bytes read or IOS_INTERRUPTED
     */
    static native int drain1(int fd) throws IOException;

    public static native void configureBlocking(FileDescriptor fd,
                                                boolean blocking)
        throws IOException;

    static native int fdLimit();

    /**
     * Used to trigger loading of native libraries
     */
    public static void load() { }

    static {
        jdk.internal.loader.BootLoader.loadLibrary("net");
        jdk.internal.loader.BootLoader.loadLibrary("nio");
    }

}
