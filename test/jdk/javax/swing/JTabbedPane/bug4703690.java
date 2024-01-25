/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4703690
 * @summary JTabbedPane should focus proper component at the tab container
 * @key headful
 * @run main bug4703690
 */

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public class bug4703690 {
    static JFrame frame;
    static JTabbedPane tabbedPane;
    static JButton one, two;

    static final CountDownLatch focusButtonTwo = new CountDownLatch(1);
    static final CountDownLatch switchToTabTwo = new CountDownLatch(1);
    static final CountDownLatch focusButtonOne = new CountDownLatch(1);
    static Robot robot;

    static Point p;
    static Rectangle rect;

    public static void main(String[] args) throws Exception {
        bug4703690 test = new bug4703690();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4703690");

                JPanel panel = new JPanel();
                one = new JButton("Button 1");
                panel.add(one);
                two = new JButton("Button 2");
                panel.add(two);

                tabbedPane = new JTabbedPane();
                frame.getContentPane().add(tabbedPane);
                tabbedPane.addTab("Tab one", panel);
                tabbedPane.addTab("Tab two", new JPanel());

                two.addFocusListener(new FocusAdapter() {
                    public void focusGained(FocusEvent e) {
                        focusButtonTwo.countDown();
                    }
                });

                tabbedPane.addChangeListener(e -> {
                    if (tabbedPane.getSelectedIndex() == 1) {
                        switchToTabTwo.countDown();
                    }
                });

                frame.setSize(200, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            test.execute();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public void execute() throws Exception {
        robot = new Robot();
        robot.setAutoDelay(50);

        SwingUtilities.invokeAndWait(two::requestFocus);

        if (!focusButtonTwo.await(1, TimeUnit.SECONDS)) {
            throw new RuntimeException("Button two didn't receive focus");
        }

        one.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                focusButtonOne.countDown();
            }
        });

        // Switch to tab two
        SwingUtilities.invokeAndWait(() -> {
            p = tabbedPane.getLocationOnScreen();
            rect = tabbedPane.getBoundsAt(1);
        });
        robot.mouseMove(p.x + rect.x + rect.width / 2,
                p.y + rect.y + rect.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        if (!switchToTabTwo.await(1, TimeUnit.SECONDS)) {
            throw new RuntimeException("Switching to tab two failed");
        }

        // Switch to tab one
        SwingUtilities.invokeAndWait(() -> {
            p = tabbedPane.getLocationOnScreen();
            rect = tabbedPane.getBoundsAt(0);
        });
        robot.mouseMove(p.x + rect.x + rect.width / 2,
                p.y + rect.y + rect.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        if (!focusButtonOne.await(1, TimeUnit.SECONDS)) {
            throw new RuntimeException("The 'Button 1' button doesn't have focus");
        }
    }
}
