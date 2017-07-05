/*
 * Copyright (c) 2001, 2004, Oracle and/or its affiliates. All rights reserved.
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

import sun.misc.*;


/**
 * Manipulates a native array of pollfd structs on Solaris:
 *
 * typedef struct pollfd {
 *    int fd;
 *    short events;
 *    short revents;
 * } pollfd_t;
 *
 * @author Mike McCloskey
 * @since 1.4
 */

class PollArrayWrapper extends AbstractPollArrayWrapper {

    static final short POLLCONN = POLLOUT;

    // File descriptor to write for interrupt
    int interruptFD;

    PollArrayWrapper(int newSize) {
        newSize = (newSize + 1) * SIZE_POLLFD;
        pollArray = new AllocatedNativeObject(newSize, false);
        pollArrayAddress = pollArray.address();
        totalChannels = 1;
    }

    void initInterrupt(int fd0, int fd1) {
        interruptFD = fd1;
        putDescriptor(0, fd0);
        putEventOps(0, POLLIN);
        putReventOps(0, 0);
    }

    void release(int i) {
        return;
    }

    void free() {
        pollArray.free();
    }

    /**
     * Prepare another pollfd struct for use.
     */
    void addEntry(SelChImpl sc) {
        putDescriptor(totalChannels, IOUtil.fdVal(sc.getFD()));
        putEventOps(totalChannels, 0);
        putReventOps(totalChannels, 0);
        totalChannels++;
    }

    /**
     * Writes the pollfd entry from the source wrapper at the source index
     * over the entry in the target wrapper at the target index. The source
     * array remains unchanged unless the source array and the target are
     * the same array.
     */
    static void replaceEntry(PollArrayWrapper source, int sindex,
                      PollArrayWrapper target, int tindex) {
        target.putDescriptor(tindex, source.getDescriptor(sindex));
        target.putEventOps(tindex, source.getEventOps(sindex));
        target.putReventOps(tindex, source.getReventOps(sindex));
    }

    /**
     * Grows the pollfd array to a size that will accommodate newSize
     * pollfd entries. This method does no checking of the newSize
     * to determine if it is in fact bigger than the old size: it
     * always reallocates an array of the new size.
     */
    void grow(int newSize) {
        // create new array
        PollArrayWrapper temp = new PollArrayWrapper(newSize);

        // Copy over existing entries
        for (int i=0; i<totalChannels; i++)
            replaceEntry(this, i, temp, i);

        // Swap new array into pollArray field
        pollArray.free();
        pollArray = temp.pollArray;
        pollArrayAddress = pollArray.address();
    }

    int poll(int numfds, int offset, long timeout) {
        return poll0(pollArrayAddress + (offset * SIZE_POLLFD),
                     numfds, timeout);
    }

    public void interrupt() {
        interrupt(interruptFD);
    }

    private native int poll0(long pollAddress, int numfds, long timeout);

    private static native void interrupt(int fd);

}
