/*
 * Copyright (c) 2004, 2021, Oracle and/or its affiliates. All rights reserved.
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
  @test 1.0 04/05/20
  @key headful
  @bug 4140484
  @summary Heavyweight components inside invisible lightweight containers still show
  @run main NativeInLightShow
*/

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;


// The test verifies that the mixing code correctly handles COMPONENT_SHOWN events
// while the top-level container is invisible.

public class NativeInLightShow {
    //Declare things used in the test, like buttons and labels here
    static volatile boolean buttonPressed = false;

    public static void main(String[] args) throws Exception {
        Frame frame = new Frame("Test");

        Robot robot = new Robot();

        Container container = new Container();
        container.setLayout(new BorderLayout());
        Button button = new Button("I'm should be visible!");
        button.addActionListener(e -> {
            System.out.println("Test PASSED");
            buttonPressed = true;
        });

        container.add(button);
        frame.add(container);
        frame.pack();

        container.setVisible(false);
        container.setVisible(true);

        // Wait for a while for COMPONENT_SHOW event to be dispatched
        robot.waitForIdle();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        robot.waitForIdle();
        robot.delay(1000);

        Point buttonLocation = button.getLocationOnScreen();

        robot.mouseMove(buttonLocation.x + 5, buttonLocation.y + 5);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        // Wait for a while for ACTION event to be dispatched
        robot.waitForIdle();
        robot.delay(500);

        frame.dispose();
        if (!buttonPressed) {
            System.out.println("Test FAILED");
            throw new RuntimeException("Button was not pressed");
        }
    }
}
