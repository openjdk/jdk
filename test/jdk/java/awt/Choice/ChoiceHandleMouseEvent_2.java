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

/*
  @test
  @bug 6239944
  @summary PIT: Right clicking on the scrollbar of the choice's dropdown disposes the drop-down, on XToolkit
  @key headful
  @requires (os.family == "linux" | os.family == "windows")
*/

import java.awt.Choice;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ChoiceHandleMouseEvent_2 {

    static Robot robot;
    static volatile Choice choice1;
    static volatile Frame frame;
    static boolean isWindows;

    public static void main(String[] args) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.startsWith("windows") && !os.startsWith("linux")) {
            System.out.println("This test is only for Windows and Linux");
            return;
        }
        isWindows = os.startsWith("windows");
        try {
            EventQueue.invokeAndWait(() -> createUI());
            runTest();
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(() -> frame.dispose());
            }
       }
    }

    static void createUI() {
        choice1 = new Choice();
        for (int i = 1; i<50; i++) {
            choice1.add("item-0"+i);
        }
        choice1.setForeground(Color.red);
        choice1.setBackground(Color.red);
        frame = new Frame("ChoiceHandleMouseEvent_2");
        frame.setBackground(Color.green);
        Panel panel = new Panel();
        panel.setBackground(Color.green);
        panel.add(choice1);
        frame.add(panel);
        frame.setSize(300,300);
        frame.setLocationRelativeTo(null);
        frame.validate();
        frame.setVisible(true);
    }

    static void runTest() throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(50);
        robot.delay(100);

        /*
         * Stage 1. Choice should be closed if user dragged mouse
         * outside of Choice after opening it.
         * Should only pass on Windows or XAWT.
         * Choice on motif might be opened only by click on small box
         * in the right side.
         */
         testDragMouseButtonOut(InputEvent.BUTTON1_DOWN_MASK);
         System.out.println("Passed Stage 1: Choice should be closed if mouse dragged out.");

        /*
         * Stage 2: Choice should be closed if LeftMouse drag finished
         * on Scrollbar. This involeves only one
         * MousePress and one MouseRelease event
         */
         // first parameter is for opening choice. The second is for
         // selecting item inside the menu
         testDragMouseButtonOnSB(InputEvent.BUTTON1_DOWN_MASK);
         System.out.println("Passed Stage 2: Choice should be closed if " +
                            "LeftMouse drag finished on Scrollbar.");

        /*
         * Stage 3: Pressing RIGHT/MIDDLE MouseButton on Scrollbar
         * shouldn't close Choice's pop-down menu.
         * Pressing LEFT MouseButton shouldn't close it too. It should
         * scroll it.
         * This is an unstable test because we doesn't have an API to
         * get Scrollbar from Choice. There is a possibility not to
         * hit the scrollbar that couldn't been predicted.
         */
         // first parameter is for opening choice. The second is for
         // selecting item inside the menu
         testPressOnScrollbar(InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON2_DOWN_MASK);
         testPressOnScrollbar(InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON3_DOWN_MASK);
         System.out.println("Passed Stage 3: Choice correctly reacts on mouse click on its Scrollbar.");

         /*
          * Stage 4: Choice should close its popdown menu if user opened a Choice then
          * releases Mouse and then presses Mouse again and dragged it on Choice's Scrollbar
          * This involves only one MousePress and one MouseRelease
          * event, so it differs from Stage 2.
          */
          // first parameter is for opening choice. The second is for
          // selecting item inside the menu or scrollbar
          testDragMouseOnScrollbar(InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON1_DOWN_MASK);
          System.out.println("Passed Stage 4: Choice should close if user opened a " +
                             "Choice then releases Mouse and then presses Mouse again " +
                             "and drag it on Choice's Scrollbar .");
    }


    //Stage 4
    static void testDragMouseOnScrollbar(int openButton, int button) {
        Point pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        robot.delay(100);
        robot.mousePress(openButton);
        robot.mouseRelease(openButton);
        robot.delay(200);

        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        robot.mousePress(button);
        /*X-coordinate should be closer to right edge of Choice, so
          divider 4 is used. */
        dragMouse(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2,
                  pt.x + choice1.getWidth() - choice1.getHeight()/4, pt.y + 5*choice1.getHeight());
        robot.mouseRelease(button);
        robot.delay(200);

        int px = pt.x + choice1.getWidth()/2;
        int py = pt.y + 3 * choice1.getHeight();
        Color color = robot.getPixelColor(px, py);
        //should take a color on the point on the choice's menu
        System.out.println("Got color " + color + " at (" + px + "," + py + ")");
        if (color.equals(Color.red)) {
            throw new RuntimeException(
               "Test failed. Choice didn't close after drag without firstPress on ScrollBar " + button);
        } else {
            System.out.println("Stage 4 passed."+ button);
        }

        //close opened choice
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(200);
    }

    //stage 3
    static void testPressOnScrollbar(int openButton, int button) {
        if (!isWindows) {
            return; // Windows-only tests.
        }
        Point pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        robot.delay(100);
        robot.mousePress(openButton);
        robot.mouseRelease(openButton);
        robot.delay(200);
        /*X-coordinate should be closer to right edge of Choice, so
          divide by 4 is used. */
        int px = pt.x + choice1.getWidth() - choice1.getHeight()/4;
        int py = pt.y + 5*choice1.getHeight();
        robot.mouseMove(px, py);
        robot.delay(200);
        robot.mousePress(button);
        robot.mouseRelease(button);
        robot.delay(200);

        System.out.println("x= "+px);
        System.out.println("y= "+py);

        /*
          This is for Windows only.
          On XP theme choice become closed on RightMouseClick over a scrollbar.
          A system menu is opened after that. On Classic theme Choice doesn't react on it at all.
        */
        boolean isXPTheme = false;
        Object themeObject = Toolkit.getDefaultToolkit().getDesktopProperty("win.xpstyle.themeActive");
        // it returns null when Classic theme is active but we should
        // check it's boolean value anyway if event it's not null.
        if (themeObject != null) {
            isXPTheme = ((Boolean)themeObject).booleanValue();
        }
        System.out.println("isXPTheme="+isXPTheme);
        px = pt.x + choice1.getWidth()/2;
        py = pt.y + 3 * choice1.getHeight();
        Color color = robot.getPixelColor(px, py);
        //we should take a color on the point on the choice's menu
        System.out.println("Got color " + color + " at (" + px + "," + py + ")");
        System.out.println("RED="+Color.red);
        System.out.println("GREEN="+Color.green);
        if (isXPTheme && button == InputEvent.BUTTON3_DOWN_MASK) {
            if (!color.equals(Color.green)) {
                throw new RuntimeException("Stage 3 failed(XP theme). " +
                  "Choice wasn't closed with pressing button on its Scrollbar" + openButton +":"+button);
            } else {
                System.out.println("Stage 3 passed(XP theme)." + openButton +":"+button);
            }
        } else {
            if (!color.equals(Color.red)) {
                throw new RuntimeException("Stage 3 failed(classic theme). " +
                   "Choice is being closed with pressing button on its Scrollbar" + openButton +":"+button);
            } else {
                System.out.println("Stage 3 passed(classic theme)." + openButton +":"+button);
            }
        }

        //close opened choice
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(200);
    }

    // Stage 1
    static void testDragMouseButtonOut(int button) {
        Point pt = choice1.getLocationOnScreen();

        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        robot.mousePress(button);
        dragMouse(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2,
                  pt.x + choice1.getWidth()*2, pt.y + choice1.getHeight()/2);
        robot.mouseRelease(button);
        robot.delay(200);
        int px = pt.x + choice1.getWidth()/2;
        int py = pt.y + 3 * choice1.getHeight();
        Color color = robot.getPixelColor(px, py);
        //should take a color on the point on the choice's menu
        System.out.println("Got color " + color + " at (" + px + "," + py + ")");
        System.out.println("RED="+Color.red);
        // fix 6268989: On Windows Choice shouldn't been closed if
        //  Mouse dragged outside of Choice after one mouse press.
        if (isWindows) {
            if (color.equals(Color.red)) {
                System.out.println("Stage 1 passed. On Windows Choice shouldn't be " +
                      "closed if Mouse dragged outside of Choice after one mouse press "+button);
            } else {
                throw new RuntimeException("Test failed. Choice on Windows shouldn't be " +
                   "closed after drag outside of Choice after one mouse press "+button);
            }
        } else {
            if (color.equals(Color.red)) {
                throw new RuntimeException("Test failed. Choice didn't close " +
                                           "after drag outside of Choice "+button);
            } else {
                System.out.println("Stage 1 passed."+ button);
            }
        }

        //close opened choice
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(200);
    }

    //stage 2
    static void testDragMouseButtonOnSB(int button) {
        Point pt = choice1.getLocationOnScreen();

        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        robot.mousePress(button);
        /*X-coordinate should be closer to right edge of Choice, so
          divider 4 is used. */
        dragMouse(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2,
                  pt.x + choice1.getWidth() - choice1.getHeight()/4, pt.y + 5*choice1.getHeight());
        robot.mouseRelease(button);
        robot.delay(200);
        int px = pt.x + choice1.getWidth()/2;
        int py = pt.y + 3 * choice1.getHeight();
        Color color = robot.getPixelColor(px, py);
        //should take a color on the point on the choice's menu
        System.out.println("Got color " + color + " at (" + px + "," + py + ")");
        if (isWindows) {
            if (color.equals(Color.red)) {
                System.out.println("Stage 2 passed. On Windows Choice shouldn't be " +
                                   " closed if Mouse dragged on its scrollbar "+button);
            } else {
                throw new RuntimeException("Test failed. On Windows Choice shouldn't be " +
                                           " closed if Mouse dragged on its scrollbar  "+button);
            }
        } else {
            if (color.equals(Color.red)) {
                throw new RuntimeException("Test failed. Choice didn't close after drag on ScrollBar "+button);
            } else {
                System.out.println("Stage 2 passed."+ button);
            }
        }

        //close opened choice
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        robot.delay(200);
    }

    static void dragMouse(int x0, int y0, int x1, int y1) {
        int curX = x0;
        int curY = y0;
        int dx = x0 < x1 ? 1 : -1;
        int dy = y0 < y1 ? 1 : -1;

        while (curX != x1) {
            curX += dx;
            robot.mouseMove(curX, curY);
        }
        while (curY != y1) {
            curY += dy;
            robot.mouseMove(curX, curY);
        }
    }
}
