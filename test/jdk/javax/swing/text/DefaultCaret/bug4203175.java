/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Container;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4203175
 * @key headful
 * @summary Tests that double-click on disabled JTextField doesn't
 *          cause other text-field to select content.
 */

public class bug4203175 {
    private static JFrame jFrame;
    private static JTextField tf1, tf2;
    private static JButton b;
    private static volatile Point point;
    private static volatile boolean passed = true;
    private static int clicks = 0;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(50);
            robot.setAutoWaitForIdle(true);

            SwingUtilities.invokeAndWait(bug4203175::createAndShowUI);
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> point = tf1.getLocationOnScreen());
            robot.mouseMove(point.x, point.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(200);

            SwingUtilities.invokeAndWait(() -> point = b.getLocationOnScreen());
            robot.mouseMove(point.x, point.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(200);

            SwingUtilities.invokeAndWait(() -> point = tf2.getLocationOnScreen());
            robot.mouseMove(point.x, point.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(200);

            if (!passed) {
                throw new RuntimeException("Test failed!! Text selection on disabled" +
                                           " TextField does not work as expected!");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (jFrame != null) {
                    jFrame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        jFrame = new JFrame("JTextField Text Selection");
        Container cont = jFrame.getContentPane();
        cont.setLayout(new BoxLayout(cont, BoxLayout.Y_AXIS));

        tf1 = new JTextField(20);
        tf1.setText("sometext");
        tf1.setName("Field 1");
        tf1.setCaretPosition(tf1.getDocument().getLength());
        cont.add(tf1);

        tf2 = new JTextField(20);
        tf2.setName("Field 2");
        tf2.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                clicks++;
                if (clicks == 2) {
                    String selText = tf1.getSelectedText();
                    passed = (selText == null || (selText.length() == 0));
                }
            }
        });
        cont.add(tf2);

        b = new JButton("Toggle Enabled");
        cont.add(b);
        b.addActionListener(e -> {
            if (e.getSource() == b) {
                boolean b = tf1.isEnabled();
                tf1.setEnabled(!b);
                tf2.setEnabled(!b);
            }
        });

        jFrame.pack();
        jFrame.setLocationRelativeTo(null);
        jFrame.setVisible(true);
    }
}
