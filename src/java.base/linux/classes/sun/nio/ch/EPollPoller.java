/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static sun.nio.ch.EPoll.*;

/**
 * Poller implementation based on the epoll facility.
 */

class EPollPoller extends Poller {
    private static final int ENOENT = 2;

    private final int epfd;
    private final int event;
    private final int maxEvents;
    private final long address;

    EPollPoller(boolean subPoller, boolean read) throws IOException {
        this.epfd = EPoll.create();
        this.event = (read) ? EPOLLIN : EPOLLOUT;
        this.maxEvents = (subPoller) ? 64 : 512;
        this.address = EPoll.allocatePollArray(maxEvents);
    }

    @Override
    int fdVal() {
        return epfd;
    }

    @Override
    void implRegister(int fdVal) throws IOException {
        // re-arm
        int err = EPoll.ctl(epfd, EPOLL_CTL_MOD, fdVal, (event | EPOLLONESHOT));
        if (err == ENOENT)
            err = EPoll.ctl(epfd, EPOLL_CTL_ADD, fdVal, (event | EPOLLONESHOT));
        if (err != 0)
            throw new IOException("epoll_ctl failed: " + err);
    }

    @Override
    void implDeregister(int fdVal, boolean polled) {
        // event is disabled if already polled
        if (!polled) {
            EPoll.ctl(epfd, EPOLL_CTL_DEL, fdVal, 0);
        }
    }

    @Override
    int poll(int timeout) throws IOException {
        int n = EPoll.wait(epfd, address, maxEvents, timeout);
        int i = 0;
        while (i < n) {
            long eventAddress = EPoll.getEvent(address, i);
            int fdVal = EPoll.getDescriptor(eventAddress);
            polled(fdVal);
            i++;
        }
        return n;
    }
}

