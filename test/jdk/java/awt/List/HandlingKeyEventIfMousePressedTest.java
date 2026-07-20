/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6293432
 * @summary Key events ('SPACE', 'UP', 'DOWN') aren't blocked
 *          if mouse is kept in 'PRESSED' state for List
 * @key headful
 * @run main HandlingKeyEventIfMousePressedTest
 */

import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public class HandlingKeyEventIfMousePressedTest {

    static Frame frame;
    static List list;
    static volatile Point loc;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            EventQueue.invokeAndWait(() -> createUI());
            robot.waitForIdle();
            robot.delay(1000);
            EventQueue.invokeAndWait(() -> {
                loc = list.getLocationOnScreen();
            });
            robot.mouseMove(loc.x + 10, loc.y + 10);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);

            // key pressing when the mouse is kept in the 'pressed' state
            robot.keyPress(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_DOWN);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();

            int selectedIndex = list.getSelectedIndex();
            if (selectedIndex != 0) {
                throw new RuntimeException("Test failed: list.getCurrentItem = " + selectedIndex);
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createUI() {
        frame = new Frame("HandlingKeyEventIfMousePressedTest");
        list = new List(10, false);

        list.add("111");
        list.add("222");
        list.add("333");
        list.add("444");
        frame.add(list);

        addListeners();

        frame.setLayout(new FlowLayout());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // added in order to have more information in failed case
    private static void addListeners() {

        list.addMouseMotionListener(
            new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent me) {
                    System.out.println(me);
                }

                @Override
                public void mouseMoved(MouseEvent me) {
                    System.out.println(me);
                }
            });

        list.addMouseListener(
            new MouseAdapter(){
                public void mousePressed(MouseEvent me) {
                    System.out.println(me);
                }
                public void mouseClicked(MouseEvent me) {
                    System.out.println(me);
                }
                public void mouseEntered(MouseEvent me) {
                    System.out.println(me);
                }
                public void mouseExited(MouseEvent me) {
                    System.out.println(me);
                }
                public void mouseReleased(MouseEvent me) {
                    System.out.println(me);
                }
            });

        list.addActionListener(
            new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    System.out.println(ae);
                }
            });

        list.addItemListener(
            new ItemListener() {
                public void itemStateChanged(ItemEvent ie) {
                    System.out.println(ie);
                }
            });

        list.addFocusListener(
            new FocusAdapter() {
                public void focusGained(FocusEvent fe) {
                    System.out.println(fe);
                }
                public void focusLost(FocusEvent fe) {
                    System.out.println(fe);
                }
            });
    }
}
