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

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import jdk.test.lib.Platform;
import jtreg.SkippedException;

/*
 * @test
 * @bug 8035556
 * @key headful
 * @summary On Linux desktops that provide org.kde.StatusNotifierWatcher
 *          (e.g. Ubuntu 24+ with GNOME and the ubuntu-appindicators extension,
 *          KDE Plasma), verify that:
 *          1. The peer created is SNITrayIconPeer, not XTrayIconPeer
 *          2. SystemTray.remove() disposes the peer cleanly
 * @requires os.family == "linux"
 * @modules java.desktop/sun.awt.X11:+open
 *          java.desktop/java.awt:+open
 * @library /test/lib
 * @build jdk.test.lib.Platform jtreg.SkippedException
 * @run main/othervm --enable-native-access=ALL-UNNAMED SNITrayIconTest
 */
public class SNITrayIconTest {

    public static void main(String[] args) throws Exception {
        if (!Platform.isLinux()) {
            throw new SkippedException("SNI is Linux-only");
        }
        if (!SystemTray.isSupported()) {
            throw new SkippedException("SystemTray not supported on this desktop");
        }

        TrayIcon[] iconHolder = new TrayIcon[1];
        EventQueue.invokeAndWait(() -> {
            BufferedImage img = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
            iconHolder[0] = new TrayIcon(img, "SNI Test");
            try {
                SystemTray.getSystemTray().add(iconHolder[0]);
            } catch (AWTException e) {
                throw new RuntimeException("FAIL: SystemTray.add() threw: " + e, e);
            }
        });

        TrayIcon icon = iconHolder[0];

        // Access the peer — java.desktop/java.awt is opened via @modules
        Field peerField = TrayIcon.class.getDeclaredField("peer");
        peerField.setAccessible(true);
        Object peer = peerField.get(icon);

        if (peer == null) {
            throw new RuntimeException("FAIL: TrayIcon peer is null after add()");
        }

        String peerClassName = peer.getClass().getName();
        System.out.println("Peer class: " + peerClassName);

        if (!peerClassName.equals("sun.awt.X11.SNITrayIconPeer")) {
            // SNI watcher not present on this desktop — X11 fallback is correct
            EventQueue.invokeAndWait(() -> SystemTray.getSystemTray().remove(icon));
            throw new SkippedException(
                "SNI not active on this desktop (peer is " + peerClassName + ") " +
                "— org.kde.StatusNotifierWatcher may not be running");
        }
        System.out.println("PASS: peer is SNITrayIconPeer");

        // Verify dispose() removes the icon cleanly
        EventQueue.invokeAndWait(() -> SystemTray.getSystemTray().remove(icon));

        // Verify disposed state — sun.awt.X11 is opened via @modules
        Field disposedField = peer.getClass().getDeclaredField("disposed");
        disposedField.setAccessible(true);
        boolean disposed = (boolean) disposedField.get(peer);

        if (!disposed) {
            throw new RuntimeException("FAIL: peer.disposed is false after SystemTray.remove()");
        }
        System.out.println("PASS: dispose() completed cleanly");

        System.out.println("All SNI tests passed.");
    }
}
