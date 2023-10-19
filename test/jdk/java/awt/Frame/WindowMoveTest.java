/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * @test
 * @bug 4077874
 * @key headful
 * @summary Test window position at opening, closing, and closed for consistency
 */

public class WindowMoveTest {

    static WindowMove frame;
    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(50);
        robot.setAutoWaitForIdle(true);

        EventQueue.invokeAndWait(() -> frame = new WindowMove());

        robot.waitForIdle();
        robot.delay(1000);

        EventQueue.invokeAndWait(() ->
                frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));

        if (!WindowMove.latch.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("Test timeout.");
        }

        if (WindowMove.failMessage != null) {
            throw new RuntimeException(WindowMove.failMessage);
        }
    }
}

class WindowMove extends Frame implements WindowListener {
    static final Rectangle expectedBounds =
            new Rectangle(100, 100, 300, 300);

    static CountDownLatch latch = new CountDownLatch(1);
    static String failMessage = null;

    private boolean layoutCheck;
    private boolean visibleCheck;
    private boolean openedCheck;
    private boolean closingCheck;
    private boolean closedCheck;

    public WindowMove() {
        super("WindowMove");
        addWindowListener(this);

        setSize(300, 300);
        setLocation(100, 100);
        setBackground(Color.white);

        setLayout(null);
        if (checkBounds()) {
            layoutCheck = true;
        }
        System.out.println("setLayout bounds: " + getBounds());

        setVisible(true);
        if (checkBounds()) {
            visibleCheck = true;
        }
        System.out.println("setVisible bounds: " + getBounds());
    }

    private boolean checkBounds() {
        return getBounds().equals(expectedBounds);
    }

    public void checkResult() {
        if (layoutCheck
                && visibleCheck
                && openedCheck
                && closingCheck
                && closedCheck) {
            System.out.println("Test passed.");
        } else {
            failMessage = """
                    Some of the checks failed:
                    layoutCheck %s
                    visibleCheck %s
                    openedCheck %s
                    closingCheck %s
                    closedCheck %s
                    """
                    .formatted(
                            layoutCheck,
                            visibleCheck,
                            openedCheck,
                            closingCheck,
                            closedCheck
                    );
        }

        latch.countDown();
    }

    public void windowClosing(WindowEvent evt) {
        if (checkBounds()) {
            closingCheck = true;
        }
        System.out.println("Closing bounds: " + getBounds());

        setVisible(false);
        dispose();
    }

    public void windowClosed(WindowEvent evt) {
        if (checkBounds()) {
            closedCheck = true;
        }
        System.out.println("Closed bounds: " + getBounds());

        checkResult();
    }

    public void windowOpened(WindowEvent evt) {
        if (checkBounds()) {
            openedCheck = true;
        }
        System.out.println("Opening bounds: " + getBounds());
    }

    public void windowActivated(WindowEvent evt) {}

    public void windowIconified(WindowEvent evt) {}

    public void windowDeactivated(WindowEvent evt) {}

    public void windowDeiconified(WindowEvent evt) {}
}
