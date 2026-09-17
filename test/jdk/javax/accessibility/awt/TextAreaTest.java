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
 * @summary TextArea Accessibility test.
 * @library ../../swing/regtesthelpers/accessibility/
 * @build AccessibleTestUtils AccessibleComponentTester AccessibleStateSetTester
 * @run main TextAreaTest
 */

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.TextArea;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.InvocationTargetException;

import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

public class TextAreaTest {

    private static TextArea textArea;
    private static Frame frame;

    private static final String ACCESSIBLE_NAME = "TextArea Test";
    private static final String ACCESSIBLE_DESCRIPTION =
            "Regression Test:  javax.accessibility, TextArea";

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        TextAreaTest textAreaTest = new TextAreaTest();
        EventQueue.invokeAndWait(textAreaTest::createGUI);

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(5000);

        try {
            EventQueue.invokeAndWait(textAreaTest::test);
        } finally {
            textAreaTest.dispose();
        }
    }

    private void createGUI() {
        frame = new Frame("TextAreaTest");
        String TEXT_CONTENT = """
                1. Test TextArea javax.accessibility methods
                2. Test TextArea javax.accessibility setAccessibleName
                3. Test TextArea javax.accessibility setAccessibleDescription
                4. Test TextArea javax.accessibility setAccessibleStateSet
                """;
        textArea = new TextArea(TEXT_CONTENT, 24, 80);

        textArea.getAccessibleContext().setAccessibleName(ACCESSIBLE_NAME);
        textArea.getAccessibleContext().setAccessibleDescription(
                ACCESSIBLE_DESCRIPTION);

        frame.add(textArea);
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
        AccessibleTestUtils.verifyTextAreaAccessibility(
                textArea,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new TextStateTester(
                textArea,
                textArea.getAccessibleContext().getAccessibleStateSet()
        ).testAll();

        textArea.setEditable(false);

        AccessibleTestUtils.verifyTextAreaAccessibility(
                textArea,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new TextStateTester(
                textArea,
                textArea.getAccessibleContext().getAccessibleStateSet()
        ).testAll();
    }

    private static final class TextStateTester
            extends AccessibleStateSetTester {
        private final TextArea textArea;
        private final AccessibleStateSet stateSet;

        private TextStateTester(TextArea textArea,
                                AccessibleStateSet stateSet) {
            super(textArea, stateSet);
            this.textArea = textArea;
            this.stateSet = stateSet;
        }

        @Override
        public void testEditable() {
            if (stateSet.contains(AccessibleState.EDITABLE)) {
                if (!textArea.isEditable()) {
                    throw new RuntimeException(
                            "AccessibleStateSet contains EDITABLE but " +
                                    "this component is not editable");
                }
            } else {
                if (textArea.isEditable()) {
                    throw new RuntimeException(
                            "AccessibleStateSet does not contain EDITABLE " +
                                    "but this component is editable");
                }
            }
        }
    }
}
