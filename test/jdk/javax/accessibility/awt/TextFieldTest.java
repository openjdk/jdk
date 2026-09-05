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
 * @summary TextField Accessibility test.
 * @library ../../swing/regtesthelpers/accessibility/
 * @build AccessibleTestUtils AccessibleComponentTester AccessibleStateSetTester
 * @run main TextFieldTest
 */

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.TextField;
import java.lang.reflect.InvocationTargetException;

import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

public class TextFieldTest {

    private static TextField textField;
    private static Frame frame;

    private static final String ACCESSIBLE_NAME = "TextField Test";
    private static final String ACCESSIBLE_DESCRIPTION =
            "Regression Test:  javax.accessibility, TextField";
    private static final String TEXT =
            "I love Cheesy Poofs you love Cheesy Poofs if we didn't " +
            "eat Cheesy Poofs we'd be lame!";

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        TextFieldTest textFieldTest = new TextFieldTest();
        EventQueue.invokeAndWait(textFieldTest::createGUI);

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(5000);

        try {
            EventQueue.invokeAndWait(textFieldTest::test);
        } finally {
            textFieldTest.dispose();
        }
    }

    private void createGUI() {
        frame = new Frame("TextField Test");
        textField = new TextField(TEXT, 80);

        textField.getAccessibleContext().setAccessibleName(ACCESSIBLE_NAME);
        textField.getAccessibleContext().setAccessibleDescription(
                ACCESSIBLE_DESCRIPTION);

        frame.add(textField);
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
        AccessibleTestUtils.verifyTextFieldAccessibility(
                textField,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new TextStateTester(
                textField,
                textField.getAccessibleContext().getAccessibleStateSet()
        ).testAll();

        textField.setEditable(false);

        AccessibleTestUtils.verifyTextFieldAccessibility(
                textField,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new TextStateTester(
                textField,
                textField.getAccessibleContext().getAccessibleStateSet()
        ).testAll();
    }

    private static final class TextStateTester
            extends AccessibleStateSetTester {
        private final TextField textField;
        private final AccessibleStateSet set;

        private TextStateTester(TextField textField, AccessibleStateSet set) {
            super(textField, set);
            this.textField = textField;
            this.set = set;
        }

        @Override
        public void testEditable() {
            if (set.contains(AccessibleState.EDITABLE)) {
                if (!textField.isEditable()) {
                    throw new RuntimeException(
                            "AccessibleStateSet contains EDITABLE but " +
                            "this component is not editable");
                }
            } else {
                if (textField.isEditable()) {
                    throw new RuntimeException(
                            "AccessibleStateSet does not contain EDITABLE " +
                            "but this component is editable");
                }
            }
        }
    }
}
