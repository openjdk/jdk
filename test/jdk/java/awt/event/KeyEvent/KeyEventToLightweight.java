/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4397557
 * @summary Check that focused lightweight component gets key events
 *          even if mouse is outside of it or on top of heavyweight component
 * @key headful
 * @run main KeyEventToLightweight
 */

import java.awt.AWTException;
import java.awt.Button;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;

public class KeyEventToLightweight extends Frame {
    JButton lwbutton = new JButton("Select Me");
    Button hwbutton = new Button("Heavyweight");

    AtomicBoolean aTyped = new AtomicBoolean(false);
    AtomicBoolean bTyped = new AtomicBoolean(false);
    AtomicBoolean cTyped = new AtomicBoolean(false);

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        KeyEventToLightweight test = new KeyEventToLightweight();
        try {
            EventQueue.invokeAndWait(test::initUI);
            test.performTest();
        } finally {
            EventQueue.invokeAndWait(test::dispose);
        }
    }

    public void initUI() {
        this.setLayout(new FlowLayout());
        add(lwbutton);
        add(hwbutton);
        setSize(200, 200);
        setLocationRelativeTo(null);
        lwbutton.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_A) {
                    aTyped.set(true);
                } else if (e.getKeyCode() == KeyEvent.VK_B) {
                    bTyped.set(true);
                } else if (e.getKeyCode() == KeyEvent.VK_C) {
                    cTyped.set(true);
                }
            }
        });
        setVisible(true);
    }

    public void middleOf(Component c, Point p) {
        Point loc = c.getLocationOnScreen();
        Dimension size = c.getSize();
        p.setLocation(loc.x + (size.width / 2), loc.y + (size.height / 2));
    }

    public void performTest() throws AWTException, InterruptedException,
            InvocationTargetException {
        Robot robot = new Robot();
        robot.setAutoDelay(50);
        robot.delay(500);
        robot.waitForIdle();
        Point target = new Point();
        EventQueue.invokeAndWait(() -> middleOf(lwbutton, target));
        robot.mouseMove(target.x, target.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.delay(500);
        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        robot.waitForIdle();
        robot.mouseMove(target.x - 200, target.y);
        robot.keyPress(KeyEvent.VK_B);
        robot.keyRelease(KeyEvent.VK_B);
        robot.waitForIdle();
        robot.delay(500);
        EventQueue.invokeAndWait(() -> middleOf(hwbutton, target));
        robot.mouseMove(target.x, target.y);
        robot.keyPress(KeyEvent.VK_C);
        robot.keyRelease(KeyEvent.VK_C);
        if (!aTyped.get() || !bTyped.get() || !cTyped.get()) {
            throw new RuntimeException("Key event was not delivered, case 1: "
                    + aTyped.get() + ", case 2: " + bTyped.get() + ", case 3: "
                    + cTyped.get());
        }
    }
}
