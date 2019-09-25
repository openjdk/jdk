/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * A simplified Unix domain socket which can read and write bytes at a time
 * used for simulating external launchers which use UNIX sockets to talk
 * the VM.
 */

import java.io.IOException;

public class UnixDomainSocket {

    static {
        System.loadLibrary("InheritedChannel");
        init();
    }

    private final int fd;
    private volatile String name;

    public UnixDomainSocket() throws IOException {
        this.fd = create();
    }

    public void bind(String name) throws IOException {
        bind0(fd, name);
        this.name = name;
    }

    public UnixDomainSocket accept() throws IOException {
        int newsock = accept0(fd);
        return new UnixDomainSocket(newsock);
    }

    public UnixDomainSocket(int fd) {
        this.fd = fd;
    }

    public void connect(String dest) throws IOException {
        connect0(fd, dest);
    }

    public int read() throws IOException {
        return read0(fd);
    }

    public String name() {
        return name;
    }

    public void write(int w) throws IOException {
        write0(fd, w);
    }

    public void close() {
        close0(fd, name); // close0 will unlink name if non-null
    }

    public int fd() {
        return fd;
    }

    public String toString() {
        return "UnixDomainSocket: fd=" + Integer.toString(fd);
    }

    private static native int create() throws IOException;
    private static native void bind0(int fd, String name) throws IOException;
    private static native int accept0(int fd) throws IOException;
    private static native int connect0(int fd, String name) throws IOException;

    /* read and write bytes with UNIX domain sockets */

    private static native int read0(int fd) throws IOException;
    private static native void write0(int fd, int w) throws IOException;
    private static native void close0(int fd, String name);
    private static native void init();
    public static native UnixDomainSocket[] socketpair();
}

