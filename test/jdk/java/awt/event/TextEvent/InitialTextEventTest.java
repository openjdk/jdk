/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;

/*
 * @test
 * @bug 4503516
 * @key headful
 * @summary TextEvent behaves differently across platforms, especially Solaris.
 *          Following testcase is used to test whether an initial TextEvent
 *          is triggered when a TextArea or TextField is initially added to UI.
 */

public class InitialTextEventTest implements TextListener {
    private static Frame frame;
    private static TextField textField;
    private static TextArea textArea;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();

            InitialTextEventTest textEventObj = new InitialTextEventTest();
            EventQueue.invokeAndWait(textEventObj::createUI);
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(textEventObj::testInitialTextEvent);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private void createUI() {
        frame = new Frame();
        frame.setTitle("Text Event Test");
        frame.setLayout(new FlowLayout());

        textField = new TextField("TextField");
        textArea = new TextArea("TextArea", 5, 10);

        textField.addTextListener(this);
        textArea.addTextListener(this);

        frame.add(textField);
        frame.add(textArea);

        frame.setBackground(Color.red);
        frame.setSize(500,200);
        frame.setVisible(true);
    }

    private void testInitialTextEvent() {
        Point pt;
        boolean drawn = false;
        while (!drawn) {
            try {
                pt = textArea.getLocationOnScreen();
                System.out.println("On-Screen Location on Text Area: " + pt);
                pt = textField.getLocationOnScreen();
                System.out.println("On-Screen Location on Text Field: " + pt);
            } catch (IllegalComponentStateException icse) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
                continue;
            }
            drawn = true;
        }
    }

    @Override
    public void textValueChanged(TextEvent e) {
        System.out.println("text event paramString: " + e.paramString());
        System.out.println("text event changed on: " + e.getSource().getClass().getName());
        throw new RuntimeException("InitialTextEventTest FAILED");
    }
}
