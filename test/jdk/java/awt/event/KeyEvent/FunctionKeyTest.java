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
import java.awt.Color;
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.event.KeyEvent;

/*
 * @test
 * @bug 4011219
 * @summary Test for function key press/release received by Java client.
 * @key headful
 */

public class FunctionKeyTest {
    private static FunctionKeyTester frame;
    private static Robot robot;

    static volatile boolean keyPressReceived;
    static volatile boolean keyReleaseReceived;

    static final StringBuilder failures = new StringBuilder();

    private static void testKey(int keyCode, String keyText) {
        keyPressReceived = false;
        keyReleaseReceived = false;

        robot.keyPress(keyCode);

        if (!keyPressReceived) {
            failures.append(keyText).append(" key press is not received\n");
        }

        robot.keyRelease(keyCode);

        if (!keyReleaseReceived) {
            failures.append(keyText).append(" key release is not received\n");
        }
    }

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(150);

        try {
            EventQueue.invokeAndWait(() -> {
                frame = new FunctionKeyTester();
                frame.setSize(200, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(1000);

            testKey(KeyEvent.VK_F11, "F11");
            testKey(KeyEvent.VK_F12, "F12");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }

        if (failures.isEmpty()) {
            System.out.println("Passed");
        } else {
            throw new RuntimeException(failures.toString());
        }
    }
}

class FunctionKeyTester extends Frame {
    Label l = new Label ("NULL");
    Button b = new Button();
    TextArea log = new TextArea();

    FunctionKeyTester() {
        super("Function Key Test");
        this.setLayout(new BorderLayout());
        this.add(BorderLayout.NORTH, l);
        this.add(BorderLayout.SOUTH, b);
        this.add(BorderLayout.CENTER, log);
        log.setFocusable(false);
        log.setEditable(false);
        l.setBackground(Color.red);
        setSize(200, 200);
    }

    public boolean handleEvent(Event e) {
        String message = "e.id=" + e.id + "\n";
        System.out.print(message);
        log.append(message);

        switch (e.id) {
            case 403 -> FunctionKeyTest.keyPressReceived = true;
            case 404 -> FunctionKeyTest.keyReleaseReceived = true;
        }

        return super.handleEvent(e);
    }

    public boolean keyDown(Event e, int key) {
        l.setText("e.key=" + Integer.valueOf(e.key).toString());
        return false;
    }
}
