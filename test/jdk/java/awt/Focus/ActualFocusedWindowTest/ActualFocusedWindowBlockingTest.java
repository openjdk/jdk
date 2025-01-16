/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
  @bug       6314575
  @summary   Tests that previosly focused owned window doesn't steal focus when an owner's component requests focus.
  @library /java/awt/regtesthelpers /test/lib
  @build   Util jdk.test.lib.Platform
  @run       main ActualFocusedWindowBlockingTest
*/

import java.awt.AWTEvent;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.awt.event.WindowEvent;

import jdk.test.lib.Platform;
import test.java.awt.regtesthelpers.Util;

public class ActualFocusedWindowBlockingTest {
    Robot robot = Util.createRobot();
    Frame owner = new Frame("Owner Frame");
    Window win = new Window(owner);
    Frame frame = new Frame("Auxiliary Frame");
    Button fButton = new Button("frame button") {public String toString() {return "Frame_Button";}};
    Button wButton = new Button("window button") {public String toString() {return "Window_Button";}};
    Button aButton = new Button("auxiliary button") {public String toString() {return "Auxiliary_Button";}};

    public static void main(String[] args) throws Exception {
        ActualFocusedWindowBlockingTest app = new ActualFocusedWindowBlockingTest();
        app.init();
        app.start();
    }

    public void init() {
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                public void eventDispatched(AWTEvent e) {
                    System.out.println("--> " + e);
                }
            }, FocusEvent.FOCUS_EVENT_MASK | WindowEvent.WINDOW_FOCUS_EVENT_MASK);

        owner.add(fButton);
        win.add(wButton);
        frame.add(aButton);

        owner.setName("OWNER_FRAME");
        win.setName("OWNED_WINDOW");
        frame.setName("AUX_FRAME");

        tuneAndShowWindows(new Window[] {owner, win, frame});
    }

    public void start() throws Exception {
        System.out.println("\nTest started:\n");

        // Test 1.

        clickOnCheckFocus(wButton);
        clickOnCheckFocus(aButton);

        Util.clickOnComp(fButton, robot);
        if (!testFocused(fButton)) {
            throw new TestFailedException("The owner's component [" + fButton + "] couldn't be focused by click");
        }

        // Test 2.

        clickOnCheckFocus(wButton);
        clickOnCheckFocus(aButton);

        fButton.requestFocus();
        Util.waitForIdle(robot);
        if (!testFocused(fButton)) {
            throw new TestFailedException("The owner's component [" + fButton + "] couldn't be focused by request");
        }

        // Test 3.

        clickOnCheckFocus(wButton);
        clickOnCheckFocus(aButton);
        clickOnCheckFocus(fButton);
        clickOnCheckFocus(aButton);

        EventQueue.invokeAndWait(owner::toFront);

        if (!Platform.isOnWayland()) {
            Util.clickOnTitle(owner, robot);
        }

        if (!testFocused(fButton)) {
            throw new TestFailedException("The owner's component [" + fButton + "] couldn't be focused as the most recent focus owner");
        }

        System.out.println("Test passed.");
    }

    void tuneAndShowWindows(Window[] arr) {
        int y = 0;
        for (Window w: arr) {
            w.setLayout(new FlowLayout());
            w.setBounds(100, y, 400, 150);
            w.setBackground(Color.blue);
            w.setVisible(true);
            y += 200;
            Util.waitForIdle(robot);
        }
        robot.delay(500);
    }

    void clickOnCheckFocus(Component c) throws Exception {
        if (c instanceof Frame) {
            EventQueue.invokeAndWait(() -> ((Frame) c).toFront());
            if (!Platform.isOnWayland()) {
                Util.clickOnTitle((Frame) c, robot);
            }
        } else {
            Util.clickOnComp(c, robot);
        }
        if (!testFocused(c)) {
            throw new TestErrorException(c + "couldn't get focus by click.");
        }
    }

    boolean testFocused(Component c) {
        for (int i=0; i<10; i++) {
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == c) {
                return true;
            }
            Util.waitForIdle(robot);
        }
        return false;
    }

    // Thrown when the behavior being verified is found wrong.
    class TestFailedException extends RuntimeException {
        TestFailedException(String msg) {
            super("Test failed: " + msg);
        }
    }

    // Thrown when an error not related to the behavior being verified is encountered.
    class TestErrorException extends RuntimeException {
        TestErrorException(String msg) {
            super("Unexpected error: " + msg);
        }
    }
}
