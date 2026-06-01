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

import jtreg.SkippedException;

/*
 * @test
 * @bug 8035556
 * @key headful
 * @summary Verifies that TrayIcon falls back to XTrayIconPeer when
 *          org.kde.StatusNotifierWatcher is not available on the session bus,
 *          and that SystemTray.add() does not throw in that case.
 * @requires os.family == "linux"
 * @modules java.desktop/sun.awt.X11:+open
 *          java.desktop/java.awt:+open
 * @library /test/lib
 * @build jtreg.SkippedException
 * @run main/othervm --enable-native-access=ALL-UNNAMED SNIFallbackTest
 */
public class SNIFallbackTest {

    public static void main(String[] args) throws Exception {
        if (!SystemTray.isSupported()) {
            throw new SkippedException("SystemTray not supported on this desktop");
        }

        TrayIcon[] iconHolder = new TrayIcon[1];
        EventQueue.invokeAndWait(() -> {
            BufferedImage img = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
            iconHolder[0] = new TrayIcon(img, "SNI Fallback Test");
            try {
                SystemTray.getSystemTray().add(iconHolder[0]);
            } catch (AWTException e) {
                throw new RuntimeException(
                    "FAIL: SystemTray.add() threw unexpectedly: " + e, e);
            }
        });

        TrayIcon icon = iconHolder[0];
        Field peerField = TrayIcon.class.getDeclaredField("peer");
        peerField.setAccessible(true);
        Object peer = peerField.get(icon);
        String peerClassName = peer.getClass().getName();

        EventQueue.invokeAndWait(() -> SystemTray.getSystemTray().remove(icon));

        if (peerClassName.equals("sun.awt.X11.SNITrayIconPeer")) {
            throw new SkippedException(
                "SNI watcher is active on this desktop — X11 fallback not exercised here");
        }
        if (!peerClassName.equals("sun.awt.X11.XTrayIconPeer")) {
            throw new RuntimeException("FAIL: unexpected peer class: " + peerClassName);
        }

        System.out.println("PASS: X11 fallback peer created when SNI watcher is absent: " + peerClassName);
    }
}
