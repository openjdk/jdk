/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.ref.Cleaner.Cleanable;
import jdk.internal.ref.CleanerFactory;
import static sun.nio.ch.KQueue.*;

/**
 * Poller implementation based on the kqueue facility.
 */
class KQueuePoller extends Poller {
    private final int kqfd;
    private final int filter;
    private final int maxEvents;
    private final long address;

    // file descriptors used for wakeup during shutdown
    private final int fd0;
    private final int fd1;

    // close action, and cleaner if this is subpoller
    private final Runnable closer;
    private final Cleanable cleaner;

    KQueuePoller(Poller.Mode mode, boolean subPoller, boolean read) throws IOException {
        boolean wakeable = (mode == Mode.POLLER_PER_CARRIER) && subPoller;
        int maxEvents = (subPoller) ? 16 : 64;

        int kqfd = KQueue.create();
        long address = 0L;
        int fd0 = -1;
        int fd1 = -1;
        try {
            address = KQueue.allocatePollArray(maxEvents);

            // register one end of the pipe with kqueue to allow for wakeup
            if (wakeable) {
                long fds = IOUtil.makePipe(false);
                fd0 = (int) (fds >>> 32);
                fd1 = (int) fds;
                KQueue.register(kqfd, fd0, EVFILT_READ, EV_ADD);
            }
        } catch (Throwable e) {
            FileDispatcherImpl.closeIntFD(kqfd);
            if (address != 0L) KQueue.freePollArray(address);
            if (fd0 >= 0) FileDispatcherImpl.closeIntFD(fd0);
            if (fd1 >= 0) FileDispatcherImpl.closeIntFD(fd1);
            throw e;
        }

        this.kqfd = kqfd;
        this.filter = (read) ? EVFILT_READ : EVFILT_WRITE;
        this.maxEvents = maxEvents;
        this.address = address;
        this.fd0 = fd0;
        this.fd1 = fd1;

        // create action to close kqueue, register cleaner when wakeable
        this.closer = closer(kqfd, address, fd0, fd1);
        if (wakeable) {
            this.cleaner = CleanerFactory.cleaner().register(this, closer);
        } else {
            this.cleaner = null;
        }
    }

    /**
     * Returns an action to close the kqueue and release other resources.
     */
    private static Runnable closer(int kqfd, long address, int fd0, int fd1) {
        return () -> {
            try {
                FileDispatcherImpl.closeIntFD(kqfd);
                KQueue.freePollArray(address);
                if (fd0 >= 0) FileDispatcherImpl.closeIntFD(fd0);
                if (fd1 >= 0) FileDispatcherImpl.closeIntFD(fd1);
            } catch (IOException _) { }
        };
    }

    @Override
    void close() {
        if (cleaner != null) {
            cleaner.clean();
        } else {
            closer.run();
        }
    }

    @Override
    int fdVal() {
        return kqfd;
    }

    @Override
    void implStartPoll(int fdVal) throws IOException {
        int err = KQueue.register(kqfd, fdVal, filter, (EV_ADD|EV_ONESHOT));
        if (err != 0)
            throw new IOException("kevent failed: " + err);
    }

    @Override
    void implStopPoll(int fdVal, boolean polled) {
        // event was deleted if already polled
        if (!polled) {
            KQueue.register(kqfd, fdVal, filter, EV_DELETE);
        }
    }

    @Override
    void wakeupPoller() throws IOException {
        if (fd1 < 0) {
            throw new UnsupportedOperationException();
        }
        IOUtil.write1(fd1, (byte)0);
    }

    @Override
    int poll(int timeout) throws IOException {
        int n = KQueue.poll(kqfd, address, maxEvents, timeout);
        int polled = 0;
        int i = 0;
        while (i < n) {
            long keventAddress = KQueue.getEvent(address, i);
            int fdVal = KQueue.getDescriptor(keventAddress);
            if (fdVal != fd0) {
                polled(fdVal);
                polled++;
            }
            i++;
        }
        return polled;
    }
}
