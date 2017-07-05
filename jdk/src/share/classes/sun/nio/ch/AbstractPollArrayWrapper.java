/*
 * Copyright 2001 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.nio.ch;

import sun.misc.*;


/**
 * Manipulates a native array of pollfd structs.
 *
 * @author Mike McCloskey
 * @since 1.4
 */

abstract class AbstractPollArrayWrapper {

    // Event masks
    static final short POLLIN       = 0x0001;
    static final short POLLOUT      = 0x0004;
    static final short POLLERR      = 0x0008;
    static final short POLLHUP      = 0x0010;
    static final short POLLNVAL     = 0x0020;
    static final short POLLREMOVE   = 0x0800;

    // Miscellaneous constants
    static final short SIZE_POLLFD   = 8;
    static final short FD_OFFSET     = 0;
    static final short EVENT_OFFSET  = 4;
    static final short REVENT_OFFSET = 6;

    // The poll fd array
    protected AllocatedNativeObject pollArray;

    // Number of valid entries in the pollArray
    protected int totalChannels = 0;

    // Base address of the native pollArray
    protected long pollArrayAddress;

    // Access methods for fd structures
    int getEventOps(int i) {
        int offset = SIZE_POLLFD * i + EVENT_OFFSET;
        return pollArray.getShort(offset);
    }

    int getReventOps(int i) {
        int offset = SIZE_POLLFD * i + REVENT_OFFSET;
        return pollArray.getShort(offset);
    }

    int getDescriptor(int i) {
        int offset = SIZE_POLLFD * i + FD_OFFSET;
        return pollArray.getInt(offset);
    }

    void putEventOps(int i, int event) {
        int offset = SIZE_POLLFD * i + EVENT_OFFSET;
        pollArray.putShort(offset, (short)event);
    }

    void putReventOps(int i, int revent) {
        int offset = SIZE_POLLFD * i + REVENT_OFFSET;
        pollArray.putShort(offset, (short)revent);
    }

    void putDescriptor(int i, int fd) {
        int offset = SIZE_POLLFD * i + FD_OFFSET;
        pollArray.putInt(offset, fd);
    }

}
