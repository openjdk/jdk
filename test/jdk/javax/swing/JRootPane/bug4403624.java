/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 4403624
 * @summary Tests JRootPane layout with invisible menubar
 * @key headful
 * @run main bug4403624
 */

import java.awt.Color;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;

public class bug4403624 {
    private static JFrame f;
    private static Container c;
    private static JButton b;
    private static volatile Point p;
    private static volatile int bWidth;
    private static volatile int bHeight;
    private static final int OFFSET = 2;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                f = new JFrame("bug4403624 Test");
                JMenuBar mbar;
                mbar = new JMenuBar();
                mbar.add(new JMenu("Menu"));
                f.setJMenuBar(mbar);
                b = new JButton("Hide Menu");
                b.addActionListener(e -> mbar.setVisible(false));
                c = f.getContentPane();
                c.setLayout(new FlowLayout());
                c.setBackground(Color.GREEN);
                c.add(b);
                f.pack();
                f.setLocationRelativeTo(null);
                f.setAlwaysOnTop(true);
                f.setVisible(true);
            });

            Robot r = new Robot();
            r.setAutoDelay(200);
            r.waitForIdle();
            r.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                p = b.getLocationOnScreen();
                bWidth = b.getWidth();
                bHeight = b.getHeight();
            });

            r.mouseMove(p.x + (bWidth / 2), p.y + (bHeight / 2));
            r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            SwingUtilities.invokeAndWait(() -> p = c.getLocationOnScreen());

            Color c = r.getPixelColor(p.x + OFFSET, p.y + OFFSET);

            if (c.getGreen() < 240 && c.getBlue() > 10 && c.getRed() > 10) {
                System.out.println("EXPECTED: " + Color.GREEN);
                System.out.println("ACTUAL: " + c);
                throw new RuntimeException("Failure to hide menu bar.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }
}
