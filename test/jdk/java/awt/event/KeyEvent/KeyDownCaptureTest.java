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
 * @bug 4093998
 * @summary keyDown not called on subclasses of Component
 * @key headful
 * @run main KeyDownCaptureTest
 */

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

public class KeyDownCaptureTest extends Frame implements KeyListener {
    static AtomicBoolean passed = new AtomicBoolean(false);

    public KeyDownCaptureTest() {
        super("Key Down Capture Test");
    }

    public void initUI() {
        setLayout (new BorderLayout());
        setSize(200, 200);
        setLocationRelativeTo(null);
        Canvas canvas = new Canvas();
        canvas.setBackground(Color.RED);
        canvas.addKeyListener(this);
        add(canvas, BorderLayout.CENTER);
        setVisible(true);
    }

    public void middle(Point p) {
        Point loc = getLocationOnScreen();
        Dimension size = getSize();
        p.setLocation(loc.x + (size.width / 2), loc.y + (size.height / 2));
    }

    @Override
    public void keyTyped(KeyEvent ignore) {}

    @Override
    public void keyPressed(KeyEvent e) {
        passed.set(true);
    }

    @Override
    public void keyReleased(KeyEvent ignore) {}

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        KeyDownCaptureTest test = new KeyDownCaptureTest();
        try {
            EventQueue.invokeAndWait((test::initUI));
            Robot robot = new Robot();
            robot.setAutoDelay(50);
            robot.delay(500);
            robot.waitForIdle();
            Point target = new Point();
            EventQueue.invokeAndWait(() -> {
                test.middle(target);
            });
            robot.mouseMove(target.x, target.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.keyPress(KeyEvent.VK_SPACE);
            robot.keyRelease(KeyEvent.VK_SPACE);
            robot.delay(100);
            robot.waitForIdle();
            if (!passed.get()) {
                throw new RuntimeException("KeyPressed has not arrived to canvas");
            }
        } finally {
            if (test != null) {
                EventQueue.invokeAndWait(test::dispose);
            }
        }
    }
}
