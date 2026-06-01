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
 * @summary Verifies SNITrayIconPeer property updates when SNI is active:
 *          unique service names for multiple icons, setToolTip reflection,
 *          updateImage populating icon data, and dispose idempotency.
 * @requires os.family == "linux"
 * @modules java.desktop/sun.awt.X11:+open
 *          java.desktop/java.awt:+open
 * @library /test/lib
 * @build jdk.test.lib.Platform jtreg.SkippedException
 * @run main/othervm --enable-native-access=ALL-UNNAMED SNITrayIconPropertiesTest
 */
public class SNITrayIconPropertiesTest {

    public static void main(String[] args) throws Exception {
        if (!Platform.isLinux()) {
            throw new SkippedException("SNI is Linux-only");
        }
        if (!SystemTray.isSupported()) {
            throw new SkippedException("SystemTray not supported on this desktop");
        }

        TrayIcon[] icons = new TrayIcon[2];
        EventQueue.invokeAndWait(() -> {
            BufferedImage img = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
            icons[0] = new TrayIcon(img, "SNI Props Test 1");
            icons[1] = new TrayIcon(img, "SNI Props Test 2");
            try {
                SystemTray tray = SystemTray.getSystemTray();
                tray.add(icons[0]);
                tray.add(icons[1]);
            } catch (AWTException e) {
                throw new RuntimeException("FAIL: SystemTray.add() threw: " + e, e);
            }
        });

        Field peerField = TrayIcon.class.getDeclaredField("peer");
        peerField.setAccessible(true);
        Object peer0 = peerField.get(icons[0]);
        Object peer1 = peerField.get(icons[1]);

        if (!peer0.getClass().getName().equals("sun.awt.X11.SNITrayIconPeer")) {
            EventQueue.invokeAndWait(() -> {
                SystemTray tray = SystemTray.getSystemTray();
                tray.remove(icons[0]);
                tray.remove(icons[1]);
            });
            throw new SkippedException(
                "SNI not active on this desktop (peer is " + peer0.getClass().getName() + ") " +
                "— org.kde.StatusNotifierWatcher may not be running");
        }

        try {
            testUniqueServiceNames(peer0, peer1);
            testSetToolTip(icons[0], peer0);
            testUpdateImage(icons[0], peer0);
            testDisposeIdempotency(icons[0], peer0);
        } finally {
            // icons[0] may already be removed by testDisposeIdempotency; remove is a no-op if so
            EventQueue.invokeAndWait(() -> {
                SystemTray tray = SystemTray.getSystemTray();
                tray.remove(icons[0]);
                tray.remove(icons[1]);
            });
        }

        System.out.println("All SNI property tests passed.");
    }

    private static void testUniqueServiceNames(Object peer0, Object peer1) throws Exception {
        Field f = peer0.getClass().getDeclaredField("serviceName");
        f.setAccessible(true);
        String name0 = (String) f.get(peer0);
        String name1 = (String) f.get(peer1);
        if (name0.equals(name1)) {
            throw new RuntimeException(
                "FAIL: two TrayIcons share the same SNI service name: " + name0);
        }
        System.out.println("PASS: unique service names: " + name0 + " / " + name1);
    }

    private static void testSetToolTip(TrayIcon icon, Object peer) throws Exception {
        Field f = peer.getClass().getDeclaredField("tooltip");
        f.setAccessible(true);

        EventQueue.invokeAndWait(() -> icon.setToolTip("hello-sni"));
        String tooltip = (String) f.get(peer);
        if (!"hello-sni".equals(tooltip)) {
            throw new RuntimeException(
                "FAIL: peer.tooltip expected 'hello-sni', got '" + tooltip + "'");
        }
        System.out.println("PASS: setToolTip() updated peer.tooltip");
    }

    private static void testUpdateImage(TrayIcon icon, Object peer) throws Exception {
        Field f = peer.getClass().getDeclaredField("iconData");
        f.setAccessible(true);

        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++)
                img.setRGB(x, y, 0xFF_FF0000);  // opaque red

        EventQueue.invokeAndWait(() -> icon.setImage(img));
        if (f.get(peer) == null) {
            throw new RuntimeException("FAIL: peer.iconData is null after setImage()");
        }
        System.out.println("PASS: updateImage() populated peer.iconData");
    }

    private static void testDisposeIdempotency(TrayIcon icon, Object peer) throws Exception {
        Field f = peer.getClass().getDeclaredField("disposed");
        f.setAccessible(true);

        EventQueue.invokeAndWait(() -> SystemTray.getSystemTray().remove(icon));
        if (!(boolean) f.get(peer)) {
            throw new RuntimeException("FAIL: peer.disposed should be true after remove()");
        }
        // Second remove must not throw
        EventQueue.invokeAndWait(() -> SystemTray.getSystemTray().remove(icon));
        System.out.println("PASS: dispose() is idempotent");
    }
}
