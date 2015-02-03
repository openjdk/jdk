/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
  @test
  @bug 7154841
  @summary JPopupMenu is overlapped by a Dock on Mac OS X
  @author Petr Pchelko
  @library ../../../../lib/testlibrary
  @build ExtendedRobot jdk.testlibrary.OSInfo
  @run main bug7154841
 */

import java.awt.*;
import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.concurrent.atomic.AtomicReference;
import jdk.testlibrary.OSInfo;

public class bug7154841 {

    private static final int STEP = 10;

    private static volatile boolean passed = false;
    private static JFrame frame;
    private static JPopupMenu popupMenu;
    private static AtomicReference<Rectangle> screenBounds = new AtomicReference<>();

    private static void initAndShowUI() {
        popupMenu = new JPopupMenu();
        for (int i = 0; i < 100; i++) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(" Test " + i);
            item.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    passed = true;
                }
            });
            popupMenu.add(item);
        }

        frame = new JFrame();
        screenBounds.set(getScreenBounds());
        frame.setBounds(screenBounds.get());
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        if (OSInfo.getOSType() != OSInfo.OSType.MACOSX) {
            return; // Test only for Mac OS X
        }

        try {
            ExtendedRobot r = new ExtendedRobot();
            r.setAutoDelay(100);
            r.setAutoWaitForIdle(true);
            r.mouseMove(0, 0);

            SwingUtilities.invokeAndWait(bug7154841::initAndShowUI);

            r.waitForIdle(200);

            SwingUtilities.invokeAndWait(() -> {
                popupMenu.show(frame, frame.getX() + frame.getWidth() / 2, frame.getY() + frame.getHeight() / 2);
            });

            r.waitForIdle(200);

            int y = (int)screenBounds.get().getY() + (int)screenBounds.get().getHeight() - 10;
            int center = (int)(screenBounds.get().getX() + screenBounds.get().getWidth() / 2);
            for (int x = center - 10 * STEP; x < center + 10 * STEP; x += STEP) {
                r.mouseMove(x, y);
            }

            if (!passed) {
                throw new RuntimeException("Failed: no mouse events on the popup menu");
            }
        } finally {
            SwingUtilities.invokeLater(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static Rectangle getScreenBounds() {
        return GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .getBounds();
    }

}
