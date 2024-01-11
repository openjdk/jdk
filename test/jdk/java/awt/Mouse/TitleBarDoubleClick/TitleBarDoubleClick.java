/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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
  @key headful
  @bug 4664415
  @summary Test that double clicking the titlebar does not send RELEASE/CLICKED
  @run main TitleBarDoubleClick
*/

import java.awt.AWTError;
import java.awt.AWTException;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class TitleBarDoubleClick implements MouseListener,
        WindowListener
{
    //Declare things used in the test, like buttons and labels here
    private final static Rectangle BOUNDS = new Rectangle(300, 300, 300, 300);
    private final static int TITLE_BAR_OFFSET = 10;

    Frame frame;
    Robot robot;

    private volatile boolean failed = false;

    public static void main(final String[] args) throws AWTException {
        new TitleBarDoubleClick().doTest();
    }

    public TitleBarDoubleClick() throws AWTException {
        robot = new Robot();
        robot.setAutoDelay(100);

        robot.mouseMove(
                BOUNDS.x + (BOUNDS.width / 2),
                BOUNDS.y + (BOUNDS.height/ 2)
        );

        frame = new Frame("TitleBarDoubleClick");
        frame.setBounds(BOUNDS);
        frame.addMouseListener(this);
        frame.addWindowListener(this);
        frame.setVisible(true);

        robot.waitForIdle();
        robot.delay(1000);
    }

    public void doTest() throws AWTException {
        System.out.println("doing test");
        robot.mouseMove(
                BOUNDS.x + (BOUNDS.width / 2),
                BOUNDS.y + TITLE_BAR_OFFSET
        );
        robot.waitForIdle();

        System.out.println("1st press:   currentTimeMillis: "
                + System.currentTimeMillis());
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);

        System.out.println("1st release: currentTimeMillis: "
                + System.currentTimeMillis());
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        System.out.println("2nd press:   currentTimeMillis: "
                + System.currentTimeMillis());
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);

        System.out.println("2nd release: currentTimeMillis: "
                + System.currentTimeMillis());
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        System.out.println("done:        currentTimeMillis: "
                + System.currentTimeMillis());

        robot.waitForIdle();
        robot.delay(500);

        frame.dispose();

        if (failed) {
            throw new AWTError("Test failed");
        }
    }

    private void fail(MouseEvent e) {
        System.err.println("Failed: " + e);
        failed = true;
    }

    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mousePressed(MouseEvent e) { fail(e); }
    public void mouseReleased(MouseEvent e) { fail(e); }
    public void mouseClicked(MouseEvent e) { fail(e); }

    public void windowActivated(WindowEvent e) {}
    public void windowClosed(WindowEvent e) {}
    public void windowClosing(WindowEvent e) {}
    public void windowDeactivated(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowOpened(WindowEvent e) {}

}// class TitleBarDoubleClick
