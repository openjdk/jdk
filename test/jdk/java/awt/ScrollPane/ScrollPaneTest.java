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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.ScrollPane;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

/*
 * @test
 * @bug 4124460
 * @key headful
 * @summary Test for initializing a Motif peer component causes a crash.
*/

public class ScrollPaneTest {
    private static volatile Point p1 = null;
    private static volatile Point p2 = null;
    private static Robot robot = null;

    private static Point getClickPoint(Component component) {
        Point locationOnScreen = component.getLocationOnScreen();
        Dimension size = component.getSize();
        locationOnScreen.x += size.width / 2;
        locationOnScreen.y += size.height / 2;
        return locationOnScreen;
    }
    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(100);

        try {
            doTest();
        } finally {
            ScrollPaneTester.disposeAll();
        }
    }

    private static void doTest() throws Exception {
        EventQueue.invokeAndWait(ScrollPaneTester::initAndShowGui);

        robot.waitForIdle();
        robot.delay(1000);

        EventQueue.invokeAndWait(() -> {
            p1 = getClickPoint(ScrollPaneTester.st1.buttonRight);
            p2 = getClickPoint(ScrollPaneTester.st1.buttonSwap);
        });

        robot.mouseMove(p1.x, p1.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.mouseMove(p2.x, p2.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.delay(1000);

        EventQueue.invokeAndWait(() -> {
            p1 = getClickPoint(ScrollPaneTester.st2.buttonRight);
            p2 = getClickPoint(ScrollPaneTester.st2.buttonSwap);
        });

        robot.mouseMove(p1.x, p1.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.mouseMove(p2.x, p2.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }
}

class ScrollPaneTester implements ActionListener {
    static ScrollPaneTester st1, st2;
    final Button buttonLeft, buttonRight, buttonQuit, buttonSwap;
    protected ScrollPane sp;
    protected Frame f;

    public static void initAndShowGui() {
        ScrollPaneTester.st1 = new ScrollPaneTester(true);
        ScrollPaneTester.st2 = new ScrollPaneTester(false);
    }

    public ScrollPaneTester(boolean large) {
        sp = new ScrollPane(ScrollPane.SCROLLBARS_NEVER);

        Panel p = new Panel();

        if (large) {
            p.setLayout(new GridLayout(10, 10));
            for (int i = 0; i < 10; i++)
                for (int j = 0; j < 10; j++) {
                    TextField tf = new TextField("I am " + i + j);
                    tf.setSize(100, 20);
                    p.add(tf);
                }
        } else {
            TextField tf = new TextField("Smallness:");
            tf.setSize(150, 50);
            p.add(tf);
        }

        sp.add(p);

        // Button panel
        buttonLeft = new Button("Left");
        buttonLeft.addActionListener(this);
        buttonQuit = new Button("Quit");
        buttonQuit.addActionListener(this);
        buttonSwap = new Button("Swap");
        buttonSwap.addActionListener(this);
        buttonRight = new Button("Right");
        buttonRight.addActionListener(this);

        Panel bp = new Panel();
        bp.add(buttonLeft);
        bp.add(buttonSwap);
        bp.add(buttonQuit);
        bp.add(buttonRight);

        // Window w/ button panel and scrollpane
        f = new Frame("ScrollPane Test " + (large ? "large" : "small"));
        f.setLayout(new BorderLayout());
        f.add("South", bp);
        f.add("Center", sp);

        if (large) {
            f.setSize(300, 200);
            f.setLocation(100, 100);
        } else {
            f.setSize(200, 100);
            f.setLocation(500, 100);
        }

        f.setVisible(true);
    }

    public static void disposeAll() {
        ScrollPaneTester.st1.f.dispose();
        ScrollPaneTester.st2.f.dispose();
    }

    public static void
    swapPanels() {
        ScrollPane sss = st1.sp;

        st1.f.add("Center", st2.sp);
        st1.sp = st2.sp;

        st2.f.add("Center", sss);
        st2.sp = sss;
    }

    public void
    actionPerformed(ActionEvent ev) {
        Object s = ev.getSource();

        if (s == buttonLeft) {
            scroll(true);
        } else if (s == buttonRight) {
            scroll(false);
        } else if (s == buttonSwap) {
            swapPanels();
        } else if (s == buttonQuit) {
            disposeAll();
        }
    }

    private void
    scroll(boolean scroll_left) {
        Point p = sp.getScrollPosition();

        if (scroll_left)
            p.x = Math.max(0, p.x - 20);
        else {
            int cwidth = sp.getComponent(0).getSize().width;
            p.x = Math.min(p.x + 20, cwidth);
        }

        sp.setScrollPosition(p);
    }
}
