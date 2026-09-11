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
 * @summary PopupMenu Accessibility test.
 * @library ../../swing/regtesthelpers/accessibility/
 * @build AccessibleTestUtils AccessibleMenuComponentTester
 * @run main PopupMenuTest
 */

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.PopupMenu;
import java.awt.Robot;
import java.lang.reflect.InvocationTargetException;

public class PopupMenuTest {

    private static PopupMenu popupMenu;
    private static Frame frame;

    private static final String ACCESSIBLE_NAME = "PopupMenu Test";
    private static final String ACCESSIBLE_DESCRIPTION =
            "Regression Test:  javax.accessibility, PopupMenu";

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        PopupMenuTest popupMenuTest = new PopupMenuTest();
        EventQueue.invokeAndWait(popupMenuTest::createGUI);

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(5000);

        try {
            EventQueue.invokeAndWait(popupMenuTest::test);
        } finally {
            popupMenuTest.dispose();
        }
    }

    private void createGUI() {
        frame = new Frame("PopupMenu Test");
        popupMenu = new PopupMenu("PopupMenu");

        popupMenu.add("Sleepy");
        popupMenu.add("Happy");
        popupMenu.add("Grumpy");
        popupMenu.add("Dopey");

        popupMenu.getAccessibleContext().setAccessibleName(ACCESSIBLE_NAME);
        popupMenu.getAccessibleContext().setAccessibleDescription(
                ACCESSIBLE_DESCRIPTION);

        frame.add(popupMenu);
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
        AccessibleTestUtils.verifyPopupMenuAccessibility(
                popupMenu,
                ACCESSIBLE_NAME,
                ACCESSIBLE_DESCRIPTION
        );
    }
}
