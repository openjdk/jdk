/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @requires (os.family == "linux")
 * @summary To make sure that System & Primary clipboards should behave independently
 * @library /lib/client
 * @build ExtendedRobot
 * @run main IndependenceSwingTest
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class IndependenceSwingTest {
    private static JFrame frame;
    private static JTextField tf1;
    private static JTextField tf2;
    private static JTextField tf3;
    private static Clipboard systemClip;
    private static Clipboard primaryClip;
    private static ExtendedRobot robot;
    private static volatile Point ttf1Center;
    private static volatile Point glideStartLocation;

    public static void main (String[] args) throws Exception {
        try {
            robot = new ExtendedRobot();
            SwingUtilities.invokeAndWait(IndependenceSwingTest::createAndShowUI);
            robot.waitForIdle();
            robot.delay(1000);
            test();
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("IndependenceSwingTest");
        frame.setSize(200, 200);

        // This textfield will be used to update the contents of clipboards
        tf1 = new JTextField();
        tf1.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent fe) {
                tf1.setText("Clipboards_Independence_Testing");
            }
        });

        // TextFields to get the contents of clipboard
        tf2 = new JTextField();
        tf3 = new JTextField();

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        panel.add(tf2, BorderLayout.NORTH);
        panel.add(tf3, BorderLayout.SOUTH);

        frame.add(tf1, BorderLayout.NORTH);
        frame.add(panel, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);

        frame.setVisible(true);
        tf1.requestFocus();
    }

    // Get System Selection i.e. Primary Clipboard
    private static void getPrimaryClipboard() {
        try {
            primaryClip = Toolkit.getDefaultToolkit().getSystemSelection();
            if (primaryClip == null) {
                throw new RuntimeException("Method getSystemSelection() is returning null"
                                           + " on Linux platform");
            }
        } catch (HeadlessException e) {
            System.out.println("Headless exception thrown " + e);
        }
    }

    // Method to get the contents of both of the clipboards
    private static void getClipboardsContent() throws Exception {
        systemClip = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable tp;
        Transferable ts;

        StringSelection content = new StringSelection(tf1.getText());
        systemClip.setContents(content, content);

        tp = primaryClip.getContents(null);
        ts = systemClip.getContents(null);

        // Paste the contents of System clipboard on textfield tf2 while the paste the contents of
        // of primary clipboard on textfiled tf3
        if ((ts != null) && (ts.isDataFlavorSupported(DataFlavor.stringFlavor))) {
            tf2.setBackground(Color.white);
            tf2.setForeground(Color.black);
            tf2.setText((String) ts.getTransferData(DataFlavor.stringFlavor));
        }

        if ((tp != null) && (tp.isDataFlavorSupported(DataFlavor.stringFlavor))) {
            tf3.setBackground(Color.white);
            tf3.setForeground(Color.black);
            tf3.setText((String) tp.getTransferData(DataFlavor.stringFlavor));
        }
    }

    // Method to compare the Contents return by system & primary clipboard
    private static void compareText (boolean mustEqual) {
        if ((tf2.getText()).equals(tf3.getText())) {
            if (mustEqual)
                System.out.println("Selected text & clipboard contents are same\n");
            else
                throw new RuntimeException("Selected text & clipboard contents are same\n");
        } else {
            if (mustEqual)
                throw new RuntimeException("Selected text & clipboard contents differs\n");
            else
                System.out.println("Selected text & clipboard contents differs\n");
        }
    }

    private static void test() throws Exception {
        getPrimaryClipboard();
        robot.waitForIdle(500);

        SwingUtilities.invokeAndWait(() -> {
            Point center = tf1.getLocationOnScreen();
            center.translate(tf1.getWidth() / 2, tf1.getHeight() / 2);
            ttf1Center = center;

            glideStartLocation = frame.getLocationOnScreen();
            glideStartLocation.x -= 10;
        });

        robot.glide(glideStartLocation, ttf1Center);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle(20);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle(500);

        getClipboardsContent();
        compareText(true);

        //Change the text selection to update the contents of primary clipboard
        robot.mouseMove(ttf1Center);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        robot.delay(20);
        robot.mouseMove(ttf1Center.x + 15, ttf1Center.y);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle(500);

        getClipboardsContent();
        compareText(false);
    }
}
