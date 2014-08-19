/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.apple.eawt;

import com.apple.eawt.AppEvent.SystemSleepEvent;

/**
 * Implementors receive notification as the system is entering sleep, and after the system wakes.
 *
 * This notification is useful for disconnecting from network services prior to sleep, or re-establishing a connection if the network configuration has changed during sleep.
 *
 * @see Application#addAppEventListener(AppEventListener)
 *
 * @since Java for Mac OS X 10.6 Update 3
 * @since Java for Mac OS X 10.5 Update 8
 */
public interface SystemSleepListener extends AppEventListener {
    /**
     * Called when the system is about to sleep.
     * Note: This message may not be delivered prior to the actual system sleep, and may be processed after the corresponding wake has occurred.
     * @param e the system sleep event
     */
    public void systemAboutToSleep(final SystemSleepEvent e);

    /**
     * Called after the system has awoke from sleeping.
     * @param e the system sleep event
     */
    public void systemAwoke(final SystemSleepEvent e);
}
