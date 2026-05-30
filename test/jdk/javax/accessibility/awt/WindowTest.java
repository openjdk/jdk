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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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
 * @summary Window Accessibility test.
 * @library ../../swing/regtesthelpers/accessibility/
 * @build AccessibleTestUtils AccessibleComponentTester AccessibleStateSetTester
 * @run main WindowTest
 */

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;

import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

public class WindowTest {

    private static Window window;

    private static final String ACCESSIBLE_NAME = "Window Test";
    private static final String ACCESSIBLE_DESCRIPTION =
            "Regression Test:  javax.accessibility, Window";

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        WindowTest windowTest = new WindowTest();
        EventQueue.invokeAndWait(windowTest::createGUI);

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(5000);

        try {
            EventQueue.invokeAndWait(windowTest::test);
        } finally {
            windowTest.dispose();
        }
    }

    private void createGUI() {
        window = new Window(new Frame("Frame"));
        window.setSize(300, 300);
        window.setLocationRelativeTo(null);

        window.getAccessibleContext().setAccessibleName(ACCESSIBLE_NAME);
        window.getAccessibleContext().setAccessibleDescription(
                ACCESSIBLE_DESCRIPTION);

        window.setVisible(true);
    }

    private void dispose() throws InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            if (window != null) {
                window.dispose();
            }
        });
    }

    private void test() {
        AccessibleTestUtils.verifyWindowAccessibility(
                window,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new WindowStateTester(
                window,
                window.getAccessibleContext().getAccessibleStateSet()
        ).testAll();
    }

    private static final class WindowStateTester
            extends AccessibleStateSetTester {
        private final Window window;
        private final AccessibleStateSet set;

        private WindowStateTester(Window window, AccessibleStateSet set) {
            super(window, set);
            this.window = window;
            this.set = set;
        }

        @Override
        public void testActive() {
            if (set.contains(AccessibleState.ACTIVE)) {
                if (window.getFocusOwner() == null) {
                    throw new RuntimeException(
                            "AccessibleStateSet contains ACTIVE but " +
                                    "this component is not active");
                }
            } else {
                if (window.getFocusOwner() != null) {
                    throw new RuntimeException(
                            "AccessibleStateSet does not contain ACTIVE " +
                                    "but this component is active");
                }
            }
        }
    }
}
