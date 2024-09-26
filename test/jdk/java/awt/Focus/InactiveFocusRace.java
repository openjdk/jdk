/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4697451
 * @summary Test that there is no race between focus component in inactive window and window activation
 * @key headful
 * @run main InactiveFocusRace
*/

import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.InputEvent;

public class InactiveFocusRace {

    static Frame activeFrame, inactiveFrame;
    Button activeButton, inactiveButton1, inactiveButton2;
    Semaphore sema;
    final static int TIMEOUT = 10000;

    public static void main(String[] args) throws Exception {
        try {
            InactiveFocusRace test = new InactiveFocusRace();
            test.init();
            test.start();
        } finally {
            if (activeFrame != null) {
                activeFrame.dispose();
            }
            if (inactiveFrame != null) {
                inactiveFrame.dispose();
            }
        }
    }

    public void init() {
        activeButton = new Button("active button");
        inactiveButton1 = new Button("inactive button1");
        inactiveButton2 = new Button("inactive button2");
        activeFrame = new Frame("Active frame");
        inactiveFrame = new Frame("Inactive frame");
        inactiveFrame.setLayout(new FlowLayout());
        activeFrame.add(activeButton);
        inactiveFrame.add(inactiveButton1);
        inactiveFrame.add(inactiveButton2);
        activeFrame.pack();
        activeFrame.setLocation(300, 10);
        inactiveFrame.pack();
        inactiveFrame.setLocation(300, 300);
        sema = new Semaphore();

        inactiveButton1.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                System.err.println("Button 1 got focus");
            }
        });
        inactiveButton2.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                System.err.println("Button2 got focus");
                sema.raise();
            }
        });
        activeFrame.addWindowListener(new WindowAdapter() {
            public void windowActivated(WindowEvent e) {
                System.err.println("Window activated");
                sema.raise();
            }
        });
    }

    public void start() {
        Robot robot = null;
        try {
            robot = new Robot();
        } catch (Exception e) {
            throw new RuntimeException("Unable to create robot");
        }

        inactiveFrame.setVisible(true);
        activeFrame.setVisible(true);

        // Wait for active frame to become active
        try {
            sema.doWait(TIMEOUT);
        } catch (InterruptedException ie) {
            throw new RuntimeException("Wait was interrupted");
        }
        if (!sema.getState()) {
            throw new RuntimeException("Frame doesn't become active on show");
        }
        sema.setState(false);

        // press on second button in inactive frame
        Point loc = inactiveButton2.getLocationOnScreen();
        robot.mouseMove(loc.x+5, loc.y+5);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        // after all second button should be focus owner.
        try {
            sema.doWait(TIMEOUT);
        } catch (InterruptedException ie) {
            throw new RuntimeException("Wait was interrupted");
        }
        if (!sema.getState()) {
            throw new RuntimeException("Button2 didn't become focus owner");
        }
        Toolkit.getDefaultToolkit().sync();
        robot.waitForIdle();
        if (!(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == inactiveButton2)) {
            throw new RuntimeException("Button2 should be the focus owner after all");
        }

    }

}

class Semaphore {
    boolean state = false;
    int waiting = 0;
    public Semaphore() {
    }
    public void doWait() throws InterruptedException {
        synchronized(this) {
            if (state) return;
            waiting++;
            wait();
            waiting--;
        }
    }
    public void doWait(int timeout) throws InterruptedException {
        synchronized(this) {
            if (state) return;
            waiting++;
            wait(timeout);
            waiting--;
        }
    }
    public void raise() {
        synchronized(this) {
            state = true;
            if (waiting > 0) {
                notifyAll();
            }
        }
    }
    public boolean getState() {
        synchronized(this) {
            return state;
        }
    }
    public void setState(boolean newState) {
        synchronized(this) {
            state = newState;
        }
    }
}
