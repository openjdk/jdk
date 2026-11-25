/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import static sun.nio.ch.WEPoll.*;

/**
 * Poller implementation based on wepoll.
 */
class WEPollPoller extends Poller {
    private static final int MAX_EVENTS_TO_POLL = 256;

    private final long handle;
    private final int event;
    private final long address;

    // pipe used for wakeup during shutdown
    private final PipeImpl pipe;
    private final int fd0;
    private final int fd1;

    WEPollPoller(boolean read) throws IOException {
        long handle =  WEPoll.create();
        long address = 0L;
        PipeImpl pipe = null;
        try {
            address = WEPoll.allocatePollArray(MAX_EVENTS_TO_POLL);

            // register one end of the pipe with wepoll to allow for wakeup
            pipe = new PipeImpl(DefaultSelectorProvider.get(), true, false);
            pipe.sink().configureBlocking(false);
            int err = WEPoll.ctl(handle, EPOLL_CTL_ADD, pipe.source().getFDVal(), EPOLLIN);
            if (err != 0) {
                throw new IOException("epoll_ctl failed: " + err);
            }
        } catch (Throwable e) {
            WEPoll.close(handle);
            if (address != 0L) WEPoll.freePollArray(address);
            if (pipe != null) {
                pipe.sink().close();
                pipe.source().close();
            }
            throw e;
        }

        this.event = (read) ? EPOLLIN : EPOLLOUT;
        this.handle = handle;
        this.address = address;
        this.pipe = pipe;
        this.fd0 = pipe.source().getFDVal();
        this.fd1 = pipe.sink().getFDVal();
    }

    @Override
    void close() throws IOException {
        WEPoll.close(handle);
        WEPoll.freePollArray(address);
        pipe.sink().close();
        pipe.source().close();
    }

    @Override
    void implStartPoll(int fdVal) throws IOException {
        int err = WEPoll.ctl(handle, EPOLL_CTL_ADD, fdVal, (event | EPOLLONESHOT));
        if (err != 0)
            throw new IOException("epoll_ctl failed: " + err);
    }

    @Override
    void implStopPoll(int fdVal, boolean polled) {
        WEPoll.ctl(handle, EPOLL_CTL_DEL, fdVal, 0);
    }

    @Override
    void wakeupPoller() throws IOException {
        IOUtil.write1(fd1, (byte) 0);
    }

    @Override
    int poll(int timeout) throws IOException {
        int n = WEPoll.wait(handle, address, MAX_EVENTS_TO_POLL, timeout);
        int polled = 0;
        int i = 0;
        while (i < n) {
            long event = WEPoll.getEvent(address, i);
            int fdVal = WEPoll.getDescriptor(event);
            if (fdVal != fd0) {
                polled(fdVal);
                polled++;
            }
            i++;
        }
        return polled;
    }
}

