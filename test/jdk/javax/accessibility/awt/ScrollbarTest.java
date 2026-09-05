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
 * @summary Scrollbar Accessibility test.
 * @library ../../swing/regtesthelpers/accessibility/
 * @build AccessibleTestUtils AccessibleComponentTester AccessibleStateSetTester
 * @run main ScrollbarTest
 */

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.Scrollbar;
import java.lang.reflect.InvocationTargetException;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

public final class ScrollbarTest {

    private static Scrollbar scrollbar;
    private static Frame frame;

    private static final String ACCESSIBLE_NAME = "Scrollbar Test";
    private static final String ACCESSIBLE_DESCRIPTION =
            "Regression Test: javax.accessibility, Scrollbar";

    public static void main(String[] args)
            throws InterruptedException, InvocationTargetException, AWTException {

        ScrollbarTest test = new ScrollbarTest();
        EventQueue.invokeAndWait(test::createGui);

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(5000);

        try {
            EventQueue.invokeAndWait(test::testAccessibility);
        } finally {
            test.dispose();
        }
    }

    private void createGui() {
        frame = new Frame("ScrollbarTest");
        scrollbar = new Scrollbar(Scrollbar.VERTICAL, 0, 60, 0, 300);

        AccessibleContext context = scrollbar.getAccessibleContext();
        context.setAccessibleName(ACCESSIBLE_NAME);
        context.setAccessibleDescription(ACCESSIBLE_DESCRIPTION);

        frame.add(scrollbar);
        frame.setSize(300, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void dispose() throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
    }

    private Scrollbar getComponent() {
        return scrollbar;
    }

    private void testAccessibility() {
        Scrollbar component = getComponent();

        AccessibleTestUtils.verifyScrollbarAccessibility(
                component,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        AccessibleStateSet stateSet = component.getAccessibleContext().getAccessibleStateSet();
        if (stateSet == null) {
            throw new RuntimeException("getAccessibleStateSet returned null");
        }

        new ScrollAccessibleStateSetTester(component, stateSet).testAll();
    }

    private static final class ScrollAccessibleStateSetTester extends AccessibleStateSetTester {

        private final Scrollbar scrollbar;
        private final AccessibleStateSet stateSet;

        private ScrollAccessibleStateSetTester(Scrollbar scrollbar, AccessibleStateSet stateSet) {
            super(scrollbar, stateSet);
            this.scrollbar = scrollbar;
            this.stateSet = stateSet;
        }

        @Override
        public void testHorizontal() {
            if (scrollbar.getOrientation() == Scrollbar.HORIZONTAL) {
                if (!stateSet.contains(AccessibleState.HORIZONTAL)) {
                    throw new RuntimeException(
                            "Scrollbar is horizontal but AccessibleStateSet does not contain HORIZONTAL"
                    );
                }

                if (stateSet.contains(AccessibleState.VERTICAL)) {
                    throw new RuntimeException(
                            "Scrollbar is horizontal but AccessibleStateSet contains both HORIZONTAL and VERTICAL"
                    );
                }
            }
        }

        @Override
        public void testVertical() {
            if (scrollbar.getOrientation() == Scrollbar.VERTICAL) {
                if (!stateSet.contains(AccessibleState.VERTICAL)) {
                    throw new RuntimeException(
                            "Scrollbar is vertical but AccessibleStateSet does not contain VERTICAL"
                    );
                }

                if (stateSet.contains(AccessibleState.HORIZONTAL)) {
                    throw new RuntimeException(
                            "Scrollbar is vertical but AccessibleStateSet contains both VERTICAL and HORIZONTAL"
                    );
                }
            }
        }
    }
}
