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
 * @summary List Accessibility test.
 * @library ../../swing/regtesthelpers/accessibility/
 * @build AccessibleTestUtils AccessibleComponentTester AccessibleStateSetTester
 * @run main ListTest
 */

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.List;
import java.awt.Robot;
import java.lang.reflect.InvocationTargetException;

import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

public class ListTest {

    private static List list;
    private static Frame frame;

    private static final String ACCESSIBLE_NAME = "List Test";
    private static final String ACCESSIBLE_DESCRIPTION =
            "Regression Test:  javax.accessibility, List";

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        ListTest listTest = new ListTest();
        EventQueue.invokeAndWait(listTest::createGUI);

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(5000);

        try {
            EventQueue.invokeAndWait(listTest::test);
        } finally {
            listTest.dispose();
        }
    }

    private void createGUI() {
        frame = new Frame("List Test");
        list = new List();

        list.add("Mercury");
        list.add("Venus");
        list.add("Earth");
        list.add("JavaSoft");
        list.add("Mars");
        list.add("Jupiter");
        list.add("Saturn");
        list.add("Uranus");
        list.add("Neptune");
        list.add("Pluto");

        list.getAccessibleContext().setAccessibleName(ACCESSIBLE_NAME);
        list.getAccessibleContext().setAccessibleDescription(
                ACCESSIBLE_DESCRIPTION);

        frame.add(list);
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
        AccessibleTestUtils.verifyListAccessibility(
                list,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new ListStateTester(
                list,
                list.getAccessibleContext().getAccessibleStateSet()
        ).testAll();

        list.setMultipleMode(!list.isMultipleMode());

        AccessibleTestUtils.verifyListAccessibility(
                list,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );

        new ListStateTester(
                list,
                list.getAccessibleContext().getAccessibleStateSet()
        ).testAll();
    }

    private static final class ListStateTester
            extends AccessibleStateSetTester {
        private final List list;
        private final AccessibleStateSet set;

        private ListStateTester(List list, AccessibleStateSet set) {
            super(list, set);
            this.list = list;
            this.set = set;
        }

        @Override
        public void testMultiSelectable() {
            if (set.contains(AccessibleState.MULTISELECTABLE)) {
                if (!list.isMultipleMode()) {
                    throw new RuntimeException(
                            "AccessibleStateSet contains MULTISELECTABLE " +
                                    "but this component is not multiselectable");
                }
            } else {
                if (list.isMultipleMode()) {
                    throw new RuntimeException(
                            "AccessibleStateSet does not contain " +
                                    "MULTISELECTABLE but this component is " +
                                    "multiselectable");
                }
            }
        }
    }
}
