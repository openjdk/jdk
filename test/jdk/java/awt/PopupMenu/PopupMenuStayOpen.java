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
  @test
  @bug 4791953
  @requires (os.family == "linux" | os.family == "mac")
  @summary Checks that popup menu stay open after a triggering click.
  @key headful
  @run main/othervm -Dsun.java2d.uiScale=1 PopupMenuStayOpen
*/

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Robot;
import java.awt.Toolkit;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;

public class PopupMenuStayOpen {
    public static final int MAX_COUNT = 100;
    public volatile static boolean wasActionFired = false;
    static Frame frame;
    static PopupMenu pom;
    volatile static Point point;

    public static void main(String[] args) throws Exception {

        String nm = Toolkit.getDefaultToolkit().getClass().getName();

        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("Click-to-see-Popup");
                pom = new PopupMenu();
                frame.setTitle(nm);
                frame.setSize(300, 300);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                pom.add("A long enough line");

                pom.getItem(0).addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ae) {
                        wasActionFired = true;
                    }
                });

                frame.add(pom);
                frame.addMouseListener(new MouseAdapter() {
                    public void mousePressed(MouseEvent me) {
                        pom.show(frame, me.getX(), me.getY());
                    }
                });
            });

            Robot robot = new Robot();
            robot.delay(1000);
            robot.waitForIdle();

            EventQueue.invokeAndWait(() -> {
                point = frame.getLocationOnScreen();
            });

            robot.mouseMove(point.x + 50, point.y + 100);
            robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);

            robot.delay(1000);
            robot.waitForIdle();

            robot.mouseMove(point.x + 50 + 30, point.y + 100 + 15);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(500);


            if (!wasActionFired) {
                throw new RuntimeException("Popup not visible or has no focus");
            }
            System.out.println("Test Pass!!");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
