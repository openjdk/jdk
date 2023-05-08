/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 4147740
   @summary Tests that AbstractButton does not update images it doesn't use
   @key headful
   @run main bug4147740
*/

import java.awt.Image;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import java.awt.Robot;
import javax.swing.SwingUtilities;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

public class bug4147740 {

    static JButton b;
    static JFrame frame;
    static volatile boolean imageUpdated = false;
    static volatile boolean shouldUpdate = true;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4147740");
                b = new AnimatedButton();
                frame.getContentPane().add(b);
                b.addHierarchyListener(new Listener());
                frame.setSize(200, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    static class Listener implements HierarchyListener {
        public void hierarchyChanged(HierarchyEvent ev) {
            if ((ev.getChangeFlags() | HierarchyEvent.SHOWING_CHANGED) != 0 &&
                frame.isShowing()) {

                frame.repaint();
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        synchronized(b) {
                            b.setEnabled(false);
                            shouldUpdate = false;
                        }
                    }
                });
            }
        }
    }

    static class AnimatedButton extends JButton {
        boolean shouldNotUpdate = false;

        AnimatedButton() {
            super();
            setIcon(new ImageIcon("animated.gif"));
            setDisabledIcon(new ImageIcon("static.gif"));
        }

        public boolean imageUpdate(Image img, int infoflags,
                                   int x, int y, int w, int h) {
            boolean updated;
            synchronized(b) {
                updated = super.imageUpdate(img, infoflags, x, y, w, h);
                if (!shouldUpdate && updated) {
                    throw new RuntimeException("Failed: unused image is being updated");
                }
            }
            return updated;
        }
    }
}
