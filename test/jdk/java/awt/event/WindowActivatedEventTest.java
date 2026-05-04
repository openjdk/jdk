/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4219344
 * @summary tests that WINDOW_ACTIVATED events are generated properly
 * @key headful
 * @library /test/jdk/java/awt/regtesthelpers
 * @build Util
 * @run main WindowActivatedEventTest
 */

import test.java.awt.regtesthelpers.Util;

import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class WindowActivatedEventTest {

    static Robot robot;
    static Frame frame;
    static Dialog dialog;

    public static void main(String[] args) throws Exception {
        robot = new Robot();

        try {
            EventQueue.invokeAndWait(WindowActivatedEventTest::createAndShowGUI);
            robot.waitForIdle();
            robot.delay(500);

            Util.clickOnComp(dialog, robot);

            robot.waitForIdle();
            robot.delay(500);

            for (int i = 0; i < 3 ; i++) {
                clickAndCheck(frame);
                clickAndCheck(dialog);
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
                if (dialog != null) {
                    dialog.dispose();
                }
            });
        }
    }

    private static void clickAndCheck(Window windowToFocus)
            throws InterruptedException, InvocationTargetException {
        Window oppositeWindow = (windowToFocus == frame) ? dialog : frame;

        System.out.println("Clicking on " + windowToFocus);

        EventQueue.invokeAndWait(() -> {
            if (windowToFocus.isFocused() || !oppositeWindow.isFocused()) {
                throw new RuntimeException("%s isFocused %b, %s isFocused %b".formatted(
                        windowToFocus.getName(), windowToFocus.isFocused(),
                        oppositeWindow.getName(), oppositeWindow.isFocused()
                ));
            }
        });

        WindowEventLogger windowLogger = WindowEventLogger.getFromWindow(windowToFocus);
        WindowEventLogger oppositeWindowLogger = WindowEventLogger.getFromWindow(oppositeWindow);

        windowLogger.resetCounters();
        oppositeWindowLogger.resetCounters();

        Util.clickOnComp(windowToFocus, robot);

        robot.delay(500);

        int windowActivatedCount = windowLogger.activatedCount.get();
        int windowDeactivatedCount = windowLogger.deactivatedCount.get();
        int oppositeWindowActivatedCount = oppositeWindowLogger.activatedCount.get();
        int oppositeWindowDeactivatedCount = oppositeWindowLogger.deactivatedCount.get();

        if (windowActivatedCount != 1
                || windowDeactivatedCount != 0
                || oppositeWindowActivatedCount != 0
                || oppositeWindowDeactivatedCount != 1) {
            throw new RuntimeException(
                    "Invalid activated/deactivated count: %s (%d/%d) / %s (%d/%d)"
                    .formatted(
                            windowToFocus.getName(),
                            windowActivatedCount,
                            windowDeactivatedCount,
                            oppositeWindow.getName(),
                            oppositeWindowActivatedCount,
                            oppositeWindowDeactivatedCount
                    ));
        }
    }

    private static void createAndShowGUI() {
        frame = new Frame("frame WindowActivatedEventTest");
        dialog = new Dialog(frame, "dialog WindowActivatedEventTest");

        frame.addWindowListener(new WindowEventLogger());
        dialog.addWindowListener(new WindowEventLogger());

        frame.setBounds(400, 0, 200, 200);
        frame.setVisible(true);

        dialog.setBounds(400, 200, 200, 200);
        dialog.setVisible(true);
    }

    private static class WindowEventLogger extends WindowAdapter {
        final AtomicInteger activatedCount = new AtomicInteger(0);
        final AtomicInteger deactivatedCount = new AtomicInteger(0);

        public void windowActivated(WindowEvent e) {
            activatedCount.incrementAndGet();
            System.out.println(e);
        }

        public void windowDeactivated(WindowEvent e) {
            deactivatedCount.incrementAndGet();
            System.out.println(e);
        }

        public void resetCounters() {
            activatedCount.set(0);
            deactivatedCount.set(0);
        }

        public static WindowEventLogger getFromWindow(Window window) {
            return (WindowEventLogger) Arrays
                    .stream(window.getWindowListeners())
                    .filter(listener -> listener instanceof WindowEventLogger)
                    .findFirst().get();
        }
    }
}
