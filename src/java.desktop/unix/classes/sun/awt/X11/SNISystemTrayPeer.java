/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.X11;

import java.awt.Dimension;
import java.awt.SystemTray;
import java.awt.peer.SystemTrayPeer;

/**
 * {@link java.awt.peer.SystemTrayPeer} implementation for the SNI
 * (StatusNotifierItem) D-Bus protocol.
 * Used when {@code org.kde.StatusNotifierWatcher} is present on the session bus
 * (Ubuntu 24+, KDE Plasma, GNOME with the AppIndicator extension).
 *
 * <p>This peer is responsible only for reporting tray availability and the
 * standard icon size. Each individual {@link SNITrayIconPeer} manages its own
 * D-Bus service and connection independently.
 *
 * See JDK-8035556.
 */
public final class SNISystemTrayPeer implements SystemTrayPeer {

    /** Standard SNI icon size recommended by the freedesktop specification. */
    static final int ICON_SIZE = 22;

    private static volatile SNISystemTrayPeer peerInstance;

    private final boolean available;

    SNISystemTrayPeer(SystemTray target) {
        peerInstance = this;
        available = isWatcherPresent();
    }

    static SNISystemTrayPeer getPeerInstance() {
        return peerInstance;
    }

    boolean isAvailable() {
        return available;
    }

    void dispose() {
        peerInstance = null;
    }

    @Override
    public Dimension getTrayIconSize() {
        return new Dimension(ICON_SIZE, ICON_SIZE);
    }

    /**
     * Probe the session bus for {@code org.kde.StatusNotifierWatcher}.
     * Returns {@code true} if and only if the watcher service currently
     * owns its well-known bus name.
     *
     * <p>This performs a synchronous {@code GetNameOwner} round-trip on
     * the session bus with a short timeout (see
     * {@code WATCHER_PROBE_TIMEOUT_MS} in {@link SNITrayIconPeer}). Called
     * from the EDT during {@code SystemTray} initialization, so the probe
     * must complete quickly enough to keep the UI responsive.
     */
    private static boolean isWatcherPresent() {
        return SNITrayIconPeer.isWatcherAvailable();
    }
}
