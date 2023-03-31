/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTException;
import java.awt.Button;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 8296934
 * @summary Verifies whether Undecorated Frame can be iconified or not.
 * @run main IconifyTest
 */
public class IconifyTest {

    private static Robot robot;
    private static Button button;
    private static Frame frame;
    private static volatile int windowStatusEventType;
    private static volatile int windowIconifiedEventType;
    private static volatile boolean focusGained = false;

    public static void initializeGUI() {
        frame = new Frame();
        frame.setLayout(new FlowLayout());
        frame.setSize(200, 200);
        frame.setUndecorated(true);

        frame.addWindowFocusListener(new WindowAdapter() {
            public void windowGainedFocus(WindowEvent event) {
                focusGained = true;
            }
        });

        frame.addWindowListener(new WindowAdapter() {
            public void windowActivated(WindowEvent e) {
                windowStatusEventType = WindowEvent.WINDOW_ACTIVATED;
                System.out.println("Event encountered: " + e);
            }

            public void windowIconified(WindowEvent e) {
                windowIconifiedEventType = WindowEvent.WINDOW_ICONIFIED;
                System.out.println("Event encountered: " + e);
            }

            public void windowDeiconified(WindowEvent e) {
                windowIconifiedEventType = WindowEvent.WINDOW_DEICONIFIED;
                System.out.println("Event encountered: " + e);
            }

            public void windowDeactivated(WindowEvent e) {
                windowStatusEventType = WindowEvent.WINDOW_DEACTIVATED;
                System.out.println("Event encountered: " + e);
            }
        });

        button = new Button("Minimize me");
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                frame.setExtendedState(Frame.ICONIFIED);
            }
        });

        frame.setBackground(Color.green);
        frame.add(button);
        frame.setLocationRelativeTo(null);
        frame.toFront();
        frame.setVisible(true);
    }

    public static void main(String[] args) throws AWTException,
            InvocationTargetException, InterruptedException {
        robot = new Robot();
        try {
            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);

            SwingUtilities.invokeAndWait(IconifyTest::initializeGUI);
            final AtomicReference<Point> frameloc = new AtomicReference<>();
            final AtomicReference<Dimension> framesize = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                frameloc.set(frame.getLocationOnScreen());
                framesize.set(frame.getSize());
            });
            Point locOnScreen = frameloc.get();
            Dimension frameSizeOnScreen = framesize.get();

            robot.mouseMove(locOnScreen.x + frameSizeOnScreen.width / 2,
                    locOnScreen.y + frameSizeOnScreen.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            if (windowStatusEventType != WindowEvent.WINDOW_ACTIVATED) {
                throw new RuntimeException(
                        "FAIL: WINDOW_ACTIVATED event did not occur when the undecorated frame is activated!");
            }
            clearEventTypeValue();
            final AtomicReference<Point> buttonloc = new AtomicReference<>();
            final AtomicReference<Dimension> buttonsize = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                buttonloc.set(button.getLocationOnScreen());
                buttonsize.set(button.getSize());
            });
            Point buttonLocOnScreen = buttonloc.get();
            Dimension buttonSizeOnScreen = buttonsize.get();

            robot.mouseMove(buttonLocOnScreen.x + buttonSizeOnScreen.width / 2,
                    buttonLocOnScreen.y + buttonSizeOnScreen.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            if (windowIconifiedEventType != WindowEvent.WINDOW_ICONIFIED) {
                throw new RuntimeException(
                        "FAIL: WINDOW_ICONIFIED event did not occur when the undecorated frame is iconified!");
            }
            if (windowStatusEventType != WindowEvent.WINDOW_DEACTIVATED) {
                throw new RuntimeException(
                        "FAIL: WINDOW_DEACTIVATED event did not occur when the undecorated frame is iconified!");
            }
            final AtomicReference<Boolean> frameHasFocus = new AtomicReference<>();
            SwingUtilities
                    .invokeAndWait(() -> frameHasFocus.set(frame.hasFocus()));
            final boolean hasFocus = frameHasFocus.get();
            if (hasFocus) {
                throw new RuntimeException(
                        "FAIL: The undecorated frame has focus even when it is iconified!");
            }

            clearEventTypeValue();

            SwingUtilities
                    .invokeAndWait(() -> frame.setExtendedState(Frame.NORMAL));
            robot.waitForIdle();

            if (windowIconifiedEventType != WindowEvent.WINDOW_DEICONIFIED) {
                throw new RuntimeException(
                        "FAIL: WINDOW_DEICONIFIED event did not occur when the state is set to NORMAL!");
            }
            if (windowStatusEventType != WindowEvent.WINDOW_ACTIVATED) {
                throw new RuntimeException(
                        "FAIL: WINDOW_ACTIVATED event did not occur when the state is set to NORMAL!");
            }
            if (!focusGained) {
                throw new RuntimeException(
                        "FAIL: The undecorated frame does not have focus when it is deiconified!");
            }
            System.out.println("Test passed");
        }
        finally {
            SwingUtilities.invokeAndWait(IconifyTest::disposeFrame);
        }
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    public static void clearEventTypeValue() {
        windowIconifiedEventType = -1;
        windowStatusEventType = -1;
        focusGained = false;
    }
}

