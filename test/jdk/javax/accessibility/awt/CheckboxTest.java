/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

/*
 * @test
 * @key headful
 * @summary Checkbox Accessibility test.
 * @library ../../swing/regtesthelpers/accessibility/
 * @build AccessibleTestUtils AccessibleComponentTester AccessibleStateSetTester
 * @run main CheckboxTest
 */

import java.awt.AWTException;
import java.awt.Checkbox;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.lang.reflect.InvocationTargetException;

import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

public class CheckboxTest {

    private static Checkbox checkbox;
    private static Frame frame;

    private static final String ACCESSIBLE_NAME = "Checkbox Test";
    private static final String ACCESSIBLE_DESCRIPTION =
            "Regression Test:  javax.accessibility, Checkbox";

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        CheckboxTest checkboxTest = new CheckboxTest();
        EventQueue.invokeAndWait(checkboxTest::createGUI);

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(5000);

        try {
            EventQueue.invokeAndWait(checkboxTest::test);
        } finally {
            checkboxTest.dispose();
        }
    }

    private void createGUI() {
        frame = new Frame("Checkbox Test");
        checkbox = new Checkbox("This is a checkbox", true);

        checkbox.getAccessibleContext().setAccessibleName(ACCESSIBLE_NAME);
        checkbox.getAccessibleContext().setAccessibleDescription(
                ACCESSIBLE_DESCRIPTION);

        frame.add(checkbox);
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
        AccessibleTestUtils.verifyCheckboxAccessibility(
                checkbox,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new CheckboxStateTester(
                checkbox,
                checkbox.getAccessibleContext().getAccessibleStateSet()
        ).testAll();

        checkbox.setState(!checkbox.getState());

        AccessibleTestUtils.verifyCheckboxAccessibility(
                checkbox,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new CheckboxStateTester(
                checkbox,
                checkbox.getAccessibleContext().getAccessibleStateSet()
        ).testAll();
    }

    private static final class CheckboxStateTester
            extends AccessibleStateSetTester {
        private final Checkbox checkbox;
        private final AccessibleStateSet set;

        private CheckboxStateTester(Checkbox checkbox, AccessibleStateSet set) {
            super(checkbox, set);
            this.checkbox = checkbox;
            this.set = set;
        }

        @Override
        public void testChecked() {
            if (set.contains(AccessibleState.CHECKED)) {
                if (!checkbox.getState()) {
                    throw new RuntimeException(
                            "AccessibleStateSet contains CHECKED but " +
                            "this component is not checked");
                }
            } else {
                if (checkbox.getState()) {
                    throw new RuntimeException(
                            "AccessibleStateSet does not contain CHECKED " +
                            "but this component is checked");
                }
            }
        }
    }
}
