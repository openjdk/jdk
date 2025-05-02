/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/*
 * @test
 * @bug 4992908
 * @key headful
 * @summary Need way to get location of MouseEvent in screen coordinates
 */

// The test consists of several parts:
// 1. create MouseEvent with new Ctor and checking get(X|Y)OnScreen(),
// getLocationOnScreen(), get(X|Y), getPoint().
// 2. create MouseEvent with old Ctor and checking get(X|Y)OnScreen(),
// getLocationOnScreen(),  get(X|Y), getPoint() .

public class MouseEventAbsoluteCoordsTest implements MouseListener {
    private static Frame frame;
    private static Robot robot;

    private static Point mousePositionOnScreen = new Point(200, 200);
    private static final Point mousePosition = new Point(100, 100);

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);

            MouseEventAbsoluteCoordsTest cordsTest =
                    new MouseEventAbsoluteCoordsTest();
            cordsTest.createTestUI();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public void createTestUI() throws Exception {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("MouseEvent Test Frame");
            frame.setSize(200, 200);
            frame.setLocation(300, 400);
            frame.addMouseListener(this);
            frame.setVisible(true);
        });

        robot.waitForIdle();
        robot.delay(1000);

        // use new MouseEvent's Ctor with user-defined absolute
        // coordinates
        System.out.println("Stage MOUSE_PRESSED");
        postMouseEventNewCtor(MouseEvent.MOUSE_PRESSED);

        System.out.println("Stage MOUSE_RELEASED");
        postMouseEventNewCtor(MouseEvent.MOUSE_RELEASED);

        System.out.println("Stage MOUSE_CLICKED");
        postMouseEventNewCtor(MouseEvent.MOUSE_CLICKED);

        // call syncLocation to get correct on-screen frame position
        syncLocationToWindowManager();

        // now we gonna use old MouseEvent's Ctor thus absolute
        // position calculates as frame's location + relative coords
        // of the event.
        EventQueue.invokeAndWait(() -> mousePositionOnScreen = new Point(
                frame.getLocationOnScreen().x + mousePosition.x,
                frame.getLocationOnScreen().y + mousePosition.y));

        System.out.println("Stage MOUSE_PRESSED");
        postMouseEventOldCtor(MouseEvent.MOUSE_PRESSED);

        System.out.println("Stage MOUSE_RELEASED");
        postMouseEventOldCtor(MouseEvent.MOUSE_RELEASED);

        System.out.println("Stage MOUSE_CLICKED");
        postMouseEventOldCtor(MouseEvent.MOUSE_CLICKED);
    }

    private static void syncLocationToWindowManager() {
        Toolkit.getDefaultToolkit().sync();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        checkEventAbsolutePosition(e, "MousePressed OK");
    };

    @Override
    public void mouseExited(MouseEvent e) {
        System.out.println("mouse exited");
    };

    @Override
    public void mouseReleased(MouseEvent e) {
        checkEventAbsolutePosition(e, "MousePressed OK");
    };

    @Override
    public void mouseEntered(MouseEvent e) {
        System.out.println("mouse entered");
    };

    @Override
    public void mouseClicked(MouseEvent e) {
        checkEventAbsolutePosition(e, "MousePressed OK");
    };

    public void postMouseEventNewCtor(int MouseEventType) {
        MouseEvent mouseEvt = new MouseEvent(frame,
                                       MouseEventType,
                                       System.currentTimeMillis(),
                                       MouseEvent.BUTTON1_DOWN_MASK,
                                       mousePosition.x, mousePosition.y,
                                       mousePositionOnScreen.x,
                                       mousePositionOnScreen.y,
                                       1,
                                       false,
                                       MouseEvent.NOBUTTON
                                       );
        frame.dispatchEvent(mouseEvt);
    }

    public void postMouseEventOldCtor(int MouseEventType) {
        MouseEvent oldMouseEvt = new MouseEvent(frame,
                                          MouseEventType,
                                          System.currentTimeMillis(),
                                          MouseEvent.BUTTON1_DOWN_MASK,
                                          mousePosition.x, mousePosition.y,
                                          1,
                                          false,
                                          MouseEvent.NOBUTTON
                                          );
        frame.dispatchEvent(oldMouseEvt);
    }

    public void checkEventAbsolutePosition(MouseEvent evt, String message) {
        if (evt.getXOnScreen() != mousePositionOnScreen.x ||
            evt.getYOnScreen() != mousePositionOnScreen.y ||
            !evt.getLocationOnScreen().equals( mousePositionOnScreen)) {
                System.out.println("evt.location = "+evt.getLocationOnScreen());
                System.out.println("mouse.location = "+mousePositionOnScreen);
                throw new RuntimeException("get(X|Y)OnScreen() or getPointOnScreen() work incorrectly");
        }

        if (evt.getX() != mousePosition.x ||
            evt.getY() != mousePosition.y ||
            !evt.getPoint().equals( mousePosition)) {
            throw new RuntimeException("get(X|Y)() or getPoint() work incorrectly");
        }
        System.out.println(message);
    }
}
