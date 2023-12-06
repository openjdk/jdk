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

/**
 * Provider class for Poller implementations.
 */
abstract class PollerProvider {
    private static final PollerProvider INSTANCE = new DefaultPollerProvider();

    PollerProvider() { }

    /**
     * Returns the system-wide PollerProvider.
     */
    static PollerProvider provider() {
        return INSTANCE;
    }

    /**
     * Returns the default poller mode.
     * @implSpec The default implementation uses system threads.
     */
    Poller.Mode defaultPollerMode() {
        return Poller.Mode.SYSTEM_THREADS;
    }

    /**
     * Default number of read pollers for the given mode. The count must be a power of 2.
     * @implSpec The default implementation returns 1.
     */
    int defaultReadPollers(Poller.Mode mode) {
        return 1;
    }

    /**
     * Default number of write pollers for the given mode. The count must be a power of 2.
     * @implSpec The default implementation returns 1.
     */
    int defaultWritePollers(Poller.Mode mode) {
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
     * Creates a Poller for read ops.
     * @param subPoller true to create a sub-poller
     */
    abstract Poller readPoller(boolean subPoller) throws IOException;

    /**
     * Creates a Poller for write ops.
     * @param subPoller true to create a sub-poller
     */
    abstract Poller writePoller(boolean subPoller) throws IOException;
}
