/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @summary Frame Accessibility test.
 * @library ../../swing/regtesthelpers/accessibility/
 * @build AccessibleTestUtils AccessibleComponentTester AccessibleStateSetTester
 * @run main FrameTest
 */

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.lang.reflect.InvocationTargetException;

import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

public class FrameTest {

    private static Frame frame;

    private static final String ACCESSIBLE_NAME = "Frame Test";
    private static final String ACCESSIBLE_DESCRIPTION =
            "Regression Test:  javax.accessibility, Frame";

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        FrameTest frameTest = new FrameTest();
        EventQueue.invokeAndWait(frameTest::createGUI);

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(5000);

        try {
            EventQueue.invokeAndWait(frameTest::test);
        } finally {
            frameTest.dispose();
        }
    }

    private void createGUI() {
        frame = new Frame("Frame Test");

        frame.getAccessibleContext().setAccessibleName(ACCESSIBLE_NAME);
        frame.getAccessibleContext().setAccessibleDescription(
                ACCESSIBLE_DESCRIPTION);

        frame.setSize(300, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void dispose() throws InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
    }

    private void test() {
        AccessibleTestUtils.verifyFrameAccessibility(
                frame,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new FrameStateTester(
                frame,
                frame.getAccessibleContext().getAccessibleStateSet()
        ).testAll();

        frame.setResizable(false);

        AccessibleTestUtils.verifyFrameAccessibility(
                frame,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new FrameStateTester(
                frame,
                frame.getAccessibleContext().getAccessibleStateSet()
        ).testAll();
    }

    private static final class FrameStateTester
            extends AccessibleStateSetTester {
        private final Frame component;
        private final AccessibleStateSet set;

        private FrameStateTester(Frame frame, AccessibleStateSet set) {
            super(frame, set);
            this.component = frame;
            this.set = set;
        }

        @Override
        public void testResizable() {
            if (set.contains(AccessibleState.RESIZABLE)) {
                if (!component.isResizable()) {
                    throw new RuntimeException(
                            "AccessibleStateSet contains RESIZABLE but " +
                                    "this component is not resizable");
                }
            } else {
                if (component.isResizable()) {
                    throw new RuntimeException(
                            "AccessibleStateSet does not contain RESIZABLE " +
                                    "but this component is resizable");
                }
            }
        }

        @Override
        public void testActive() {
            if (set.contains(AccessibleState.ACTIVE)) {
                if (component.getFocusOwner() == null) {
                    throw new RuntimeException(
                            "AccessibleStateSet contains ACTIVE but " +
                                    "this component is not active");
                }
            } else {
                if (component.getFocusOwner() != null) {
                    throw new RuntimeException(
                            "AccessibleStateSet does not contain ACTIVE but " +
                                    "this component is active");
                }
            }
        }
    }
}
