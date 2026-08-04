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
 * @summary Label Accessibility test.
 * @library ../../swing/regtesthelpers/accessibility/
 * @build AccessibleTestUtils AccessibleComponentTester AccessibleStateSetTester
 * @run main LabelTest
 */

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Robot;
import java.lang.reflect.InvocationTargetException;

public class LabelTest {

    private static Label label;
    private static Frame frame;

    private static final String ACCESSIBLE_NAME = "Label Test";
    private static final String ACCESSIBLE_DESCRIPTION =
            "Regression Test:  javax.accessibility, Label";

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        LabelTest labelTest = new LabelTest();
        EventQueue.invokeAndWait(labelTest::createGUI);

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(5000);

        try {
            EventQueue.invokeAndWait(labelTest::test);
        } finally {
            labelTest.dispose();
        }
    }

    private void createGUI() {
        frame = new Frame("Label Test");
        label = new Label("This is a label");

        label.getAccessibleContext().setAccessibleName(ACCESSIBLE_NAME);
        label.getAccessibleContext().setAccessibleDescription(
                ACCESSIBLE_DESCRIPTION);

        frame.add(label);
        frame.setSize(200, 200);
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
        AccessibleTestUtils.verifyLabelAccessibility(
                label,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new AccessibleStateSetTester(
                label,
                label.getAccessibleContext().getAccessibleStateSet()
        ).testAll();
    }
}
