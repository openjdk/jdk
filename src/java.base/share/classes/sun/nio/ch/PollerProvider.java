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

/**
 * Provider class for Poller implementations.
 */
abstract class PollerProvider {
    private final Poller.Mode mode;

    PollerProvider(Poller.Mode mode) {
        this.mode = mode;
    }

    final Poller.Mode pollerMode() {
        return mode;
    }

    /**
     * Creates a PollerProvider that uses its preferred/default poller mode.
     */
    static PollerProvider createProvider() {
        return new DefaultPollerProvider();
    }

    /**
     * Creates a PollerProvider that uses the given poller mode.
     */
    static PollerProvider createProvider(Poller.Mode mode) {
        return new DefaultPollerProvider(mode);
    }

    /**
     * Default number of read pollers. The count must be a power of 2.
     * @implSpec The default implementation returns 1.
     */
    int defaultReadPollers() {
        return 1;
    }

    /**
     * Default number of write pollers. The count must be a power of 2.
     * @implSpec The default implementation returns 1.
     */
    int defaultWritePollers() {
        return 1;
    }

    /**
     * Maps a file descriptor to an index from 0 to {@code toIndex}.
     * @implSpec The default implementation is good for Unix file descriptors.
     */
    int fdValToIndex(int fdVal, int toIndex) {
        return fdVal & (toIndex - 1);
    }

    /**
     * Creates a Poller for POLLIN polling.
     * @param subPoller true to create a sub-poller
     */
    abstract Poller readPoller(boolean subPoller) throws IOException;

    /**
     * Creates a Poller for POLLOUT polling.
     * @param subPoller true to create a sub-poller
     */
    abstract Poller writePoller(boolean subPoller) throws IOException;
}
