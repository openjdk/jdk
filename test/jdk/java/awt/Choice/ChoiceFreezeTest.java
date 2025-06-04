/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4303064
  @summary Tests that choice doesn't freeze display when its container is
           disabled and enabled after.
  @key headful
*/

import java.awt.Button;
import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ChoiceFreezeTest {

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> createUI());
            runTest();
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(() -> frame.dispose());
            }
        }
    }

    static volatile Frame frame;
    static volatile ChoiceFreezeBug client;

    static void createUI() {
        frame = new Frame("ChoiceFreezeTest");
        client = new ChoiceFreezeBug();
        frame.add(client);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        client.init();
     }

     static void runTest() throws Exception {
         Robot robot = new Robot();
         robot.waitForIdle();
         robot.delay(2000);
         robot.mouseMove(client.choice.getLocationOnScreen().x + 1, client.choice.getLocationOnScreen().y + 1);
         robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
         robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
         robot.delay(1000);
         robot.mouseMove(client.button.getLocationOnScreen().x + 3, client.button.getLocationOnScreen().y + 3);
         robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
         robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
         robot.delay(1000);
         robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
         robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
         robot.delay(6000);

         if (!client.isPassed()) {
             throw new RuntimeException("Test failed: display is frozen.");
         }
    }
}

class ChoiceFreezeBug extends Panel {

    volatile Button button;
    volatile Choice choice;
    volatile ChoiceMouseListener listener = new ChoiceMouseListener();

    public ChoiceFreezeBug() {
        choice = new Choice();
        choice.addItem("Item 1");
        choice.addItem("Item 2");
        button = new Button("Button");
        add(choice);
        add(button);
        button.addMouseListener(listener);
        setEnabled(false);
    }

    void init() {
        setEnabled(true);
        choice.requestFocus();
    }

    public boolean isPassed() {
        return listener.isPassed();
    }
}

class ChoiceMouseListener extends MouseAdapter {

    volatile boolean passed = false;

    public void mouseReleased(MouseEvent e) {
        passed = true;
    }

    public void mousePressed(MouseEvent e) {
        passed = true;
    }

    public boolean isPassed() {
        return passed;
    }
}
