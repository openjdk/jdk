/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4100188
 * @key headful
 * @summary Make sure that TextFields contain all of,
 * and exactly, the text that was entered into them.
 * @run main GetTextTest
 */

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;

public class GetTextTest extends Frame implements ActionListener {
    private final String s = "test string";
    private volatile String ac;
    private TextField t;
    private Point location;
    private Dimension size;

    public void setupGUI() {
        setLayout(new FlowLayout(FlowLayout.LEFT));

        t = new TextField(s, 32);
        add(new Label("Hit <CR> after text"));
        add(t);
        t.addActionListener(this);
        setLocationRelativeTo(null);
        pack();
        setVisible(true);
    }

    public void actionPerformed(ActionEvent evt) {
        ac = evt.getActionCommand();
    }

    public void performTest() throws AWTException, InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            location = t.getLocationOnScreen();
            size = t.getSize();
        });
        Robot robot = new Robot();
        robot.setAutoDelay(50);
        robot.delay(1000);
        robot.waitForIdle();
        robot.mouseMove(location.x + size.width - 3, location.y + (size.height / 2));
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(1000);
        robot.waitForIdle();
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        robot.delay(1000);
        if (!s.equals(ac)) {
            throw new RuntimeException("Action command should be the same as text field content");
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        GetTextTest test = new GetTextTest();
        EventQueue.invokeAndWait(test::setupGUI);
        try {
            test.performTest();
        } finally {
            EventQueue.invokeAndWait(test::dispose);
        }
    }
}
