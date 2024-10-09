/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4095214
 * @summary Test change of focus on lightweights using the tab key
 * @key headful
 * @run main LightWeightTabFocus
 */

import java.awt.Button;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class LightWeightTabFocus {
    private static Frame f;
    private static LightweightButton btn1;
    private static Button btn2;
    private static Robot robot;
    private static volatile Point point;
    private static Point loc;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(100);
        try {
            EventQueue.invokeAndWait(() -> createUI());
            robot.delay(1000);
            EventQueue.invokeAndWait(() -> {
                loc = f.getLocation();
                point = btn2.getLocation();
            });
            robot.mouseMove(loc.x + point.x, loc.y + point.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            // First TAB
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            if (!btn1.hasFocus()) {
                new RuntimeException("First tab failed");
            }
            // Second TAB
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            if (!btn2.hasFocus()) {
                new RuntimeException("Second tab failed");
            }
            // First SHIFT+TAB
            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.delay(100);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            if (!btn1.hasFocus()) {
                new RuntimeException("First shift+tab failed");
            }
            // Second SHIFT+TAB
            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.delay(100);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            if (!btn2.hasFocus()) {
                new RuntimeException("Second shift+tab failed");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private static Frame createUI() {
        f = new Frame("TAB Focus Change on LW Test");
        f.setLayout(new FlowLayout());
        btn1 = new LightweightButton();
        f.add(btn1);
        btn2 = new Button("Click Me To start");
        f.add(btn2);
        f.pack();
        f.setVisible(true);
        return f;
    }
}

class LightweightButton extends Component implements FocusListener {
    boolean focus;
    LightweightButton() {
        focus = false;
        addFocusListener(this);
    }

    public Dimension getPreferredSize()
    {
        return new Dimension(100, 100);
    }

    public void focusGained(FocusEvent e) {
        focus = true;
        repaint();
    }

    public void focusLost(FocusEvent e) {
        focus = false;
        repaint();
    }

    public void paint(Graphics g) {
        if (focus) {
            g.drawString("Has Focus", 10, 20);
        } else {
            g.drawString("Not Focused", 10, 20);
        }
    }

    public boolean isFocusable() {
        return true;
    }
}
