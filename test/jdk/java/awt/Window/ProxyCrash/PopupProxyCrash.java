/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4381561
 * @key headful
 * @summary Tests that when we show the popup window AWT doesn't crash due to
 * the problems with focus proxy window code
 * @run main PopupProxyCrash
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

public class PopupProxyCrash implements ActionListener {
    private static JTextField jtf;
    private static Button tf;
    private static Panel panel;
    private static Font[] fonts;
    private static Robot robot;

    private static JComboBox cb;

    private static MyComboBoxUI comboBoxUI;
    private static Frame frame;
    private static int TEST_COUNT = 10;
    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoDelay(100);
            EventQueue.invokeAndWait(() -> createUI());
            robot.waitForIdle();
            runTest();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createUI() {
        frame = new Frame("PopupProxyCrash");
        Font dialog = new Font("Dialog", Font.PLAIN, 12);
        Font serif = new Font("Serif", Font.PLAIN, 12);
        Font monospaced = new Font("Monospaced", Font.PLAIN, 12);

        fonts = new Font[] { dialog, serif, monospaced };

        cb = new JComboBox(fonts);

        cb.setLightWeightPopupEnabled(false);
        comboBoxUI = new MyComboBoxUI();
        cb.setUI(comboBoxUI);
        jtf = new JTextField("JTextField");
        jtf.setFont(fonts[1]);
        tf = new Button("TextField");
        tf.setFont(fonts[1]);
        cb.addActionListener(new PopupProxyCrash());

        panel = new Panel() {
            public Dimension getPreferredSize() {
                return new Dimension(100, 20);
            }
            public void paint(Graphics g) {
                System.out.println("Painting with font " + getFont());
                g.setColor(Color.white);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.black);
                g.setFont(getFont());
                g.drawString("LightWeight", 10, 10);
            }
        };
        panel.setFont(fonts[1]);

        Container parent = Box.createVerticalBox();
        parent.add(jtf);
        parent.add(tf);
        parent.add(panel);
        parent.add(cb);

        frame.add(parent, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static Point getComboBoxLocation() throws Exception {
        final Point[] result = new Point[1];

        EventQueue.invokeAndWait(() -> {
            Point point = cb.getLocationOnScreen();
            Dimension size = cb.getSize();

            point.x += size.width / 2;
            point.y += size.height / 2;
            result[0] = point;
        });
        return result[0];
    }

    private static Point getItemPointToClick(final int item) throws Exception {
        final Point[] result = new Point[1];

        EventQueue.invokeAndWait(() -> {
            BasicComboPopup popup = (BasicComboPopup)comboBoxUI.getComboPopup();
            Point point = popup.getLocationOnScreen();
            Dimension size = popup.getSize();

            int step = size.height / fonts.length;
            point.x += size.width / 2;
            point.y += step / 2 + step * item;
            result[0] = point;
        });
        return result[0];
    }

    static void runTest() throws Exception {
        for (int i = 0; i < TEST_COUNT; i++) {
            Point point = getComboBoxLocation();
            robot.mouseMove(point.x, point.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(500);

            point = getItemPointToClick(i % fonts.length);
            robot.mouseMove(point.x, point.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(500);
        }
    }
    public void actionPerformed(ActionEvent ae) {
        System.out.println("Font selected");
        Font font = fonts[((JComboBox)ae.getSource()).getSelectedIndex()];

        tf.setFont(font);
        jtf.setFont(font);
        panel.setFont(font);
        panel.repaint();
    }

    private static class MyComboBoxUI extends BasicComboBoxUI {
        public ComboPopup getComboPopup() {
            return popup;
        }
    }
}
