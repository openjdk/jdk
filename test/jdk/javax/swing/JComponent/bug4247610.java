/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4247610
 * @summary Tests an unnecessary repaint issue
 * @key headful
 * @run main bug4247610
 */

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

public class bug4247610 {

    private static JFrame frame;
    private static JButton damager;
    private static volatile Point loc;
    private static volatile Dimension size;
    private static volatile boolean traced;
    private static volatile boolean failed;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("bug4247610");
            JDesktopPane pane = new JDesktopPane();

            JInternalFrame jif = new JInternalFrame(
                                 "Damager", true, true, true, true);
            InternalFramePanel ifp = new InternalFramePanel();
            damager = new JButton("Damage!");
            ifp.add(damager);
            jif.setContentPane(ifp);
            jif.setBounds(0, 0, 300, 300);
            jif.setVisible(true);
            pane.add(jif);

            jif = new JInternalFrame("Damagee", true, true, true, true);
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            final JLabel damagee = new JLabel("");
            panel.add(damagee);
            jif.setContentPane(panel);
            jif.setBounds(60, 220, 300, 100);
            jif.setVisible(true);
            pane.add(jif);

            final Random random = new Random();

            damager.addActionListener((e) -> {
                System.out.println("trace paints enabled");
                traced = true;
                damagee.setText(Integer.toString(random.nextInt()));
            });
            frame.setContentPane(pane);
            frame.setSize(500, 500);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
        robot.waitForIdle();
        robot.delay(1000);
        SwingUtilities.invokeAndWait(() -> {
            loc = damager.getLocationOnScreen();
            size = damager.getSize();
        });
        robot.mouseMove(loc.x + size.width / 2, loc.y + size.height / 2);
        robot.waitForIdle();
        robot.delay(200);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        if (failed) {
            throw new RuntimeException("Failed: unnecessary repaint occured");
        }
    }


    static class InternalFramePanel extends JPanel {
        final AtomicInteger repaintCounter = new AtomicInteger(0);
        InternalFramePanel() {
            super(new FlowLayout());
            setOpaque(true);
        }

        public synchronized void paintComponent(Graphics g) {
            super.paintComponent(g);
            repaintCounter.incrementAndGet();
            System.out.println("repaintCounter " + repaintCounter.intValue());
            if (traced) {
                failed = true;
            }
        }
    }
}
