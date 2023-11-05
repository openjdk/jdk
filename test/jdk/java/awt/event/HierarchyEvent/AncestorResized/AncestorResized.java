/*
 * Copyright (c) 2007, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;

/**
 * @test
 * @key headful
 * @bug 6533330
 * @summary ANCESTOR_RESIZED is not sent while resizing a frame. Regression
 *          caused by 6500477.
 */
public class AncestorResized {

    public static volatile int ancestorResizedCounter = 0;

    static class HierarchyBoundsListenerImpl implements HierarchyBoundsListener {
        public void ancestorMoved(HierarchyEvent ce) {
            // ANCESTOR_MOVED seems to work OK.
        }
        public void ancestorResized(HierarchyEvent ce) {
            ancestorResizedCounter++;
        }
    }

    public static void main(String[] args) throws Exception {
        Frame frame;
        Panel panel;
        Button button;
        Label label;
        Component[] components;

        frame = new Frame("Test Frame");
        frame.setLayout(new FlowLayout());

        panel = new Panel();
        button = new Button("Button");
        label = new Label("Label");

        components = new Component[] {
            panel, button, label
        };

        frame.setSize(300, 300);
        frame.setVisible(true);

        Robot robot = new Robot();
        robot.setAutoDelay(50);

        // To ensure the window is shown and packed
        robot.waitForIdle();

        Insets insets = frame.getInsets();
        if (insets.right == 0 || insets.bottom == 0) {
            frame.dispose();
            // Because we want to catch the "size-grip" of the frame.
            System.out.println("The test environment must have non-zero right & bottom insets! The current insets are: " + insets);
            return;
        }

        // Let's move the mouse pointer to the bottom-right coner of the frame (the "size-grip")
        Rectangle bounds = frame.getBounds();

        robot.mouseMove(bounds.x + bounds.width - 1, bounds.y + bounds.height - 1);

        // From now on the ANCESTOR_RESIZED events get counted.
        HierarchyBoundsListener listener = new HierarchyBoundsListenerImpl();
        for (int i = 0; i < components.length; i++) {
            components[i].addHierarchyBoundsListener(listener);
            frame.add(components[i]);
        }

        // ... and start resizing
        robot.mousePress( InputEvent.BUTTON1_MASK );
        robot.mouseMove(bounds.x + bounds.width + 20, bounds.y + bounds.height + 15);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        robot.waitForIdle();
        frame.dispose();

        if (ancestorResizedCounter == 0) {
            throw new RuntimeException("No ANCESTOR_RESIZED events received.");
        }
    }
}
