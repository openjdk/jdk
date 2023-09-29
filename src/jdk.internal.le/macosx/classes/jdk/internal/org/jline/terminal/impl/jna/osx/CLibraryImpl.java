/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.org.jline.terminal.impl.jna.osx;

import jdk.internal.org.jline.terminal.impl.jna.LastErrorException;

public final class CLibraryImpl implements CLibrary {

    static {
        System.loadLibrary("le");
        initIDs();
    }

    private static native void initIDs();

    @Override
    public native void tcgetattr(int fd, termios termios) throws LastErrorException;

    @Override
    public native void tcsetattr(int fd, int cmd, termios termios) throws LastErrorException;

    @Override
    public void ioctl(int fd, NativeLong cmd, winsize data) throws LastErrorException {
        if (cmd.longValue() == CLibrary.TIOCGWINSZ || cmd.longValue() == CLibrary.TIOCSWINSZ) {
            ioctl0(fd, cmd.longValue(), data);
        } else {
            throw new UnsupportedOperationException("Command: " + cmd + ", not supported.");
        }
    }

    private native void ioctl0(int fd, long cmd, winsize data) throws LastErrorException;

    @Override
    public native int isatty(int fd);

    @Override
    public native void ttyname_r(int fd, byte[] buf, int len) throws LastErrorException;

    @Override
    public void openpty(int[] master, int[] slave, byte[] name, termios t, winsize s) throws LastErrorException {
        throw new UnsupportedOperationException();
    }

}
