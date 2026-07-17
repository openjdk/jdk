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
 * @summary Regression Test: javax.accessibility, Button
 * @library ../../swing/regtesthelpers/accessibility/
 * @build AccessibleTestUtils AccessibleComponentTester AccessibleStateSetTester
 * @run main ButtonTest
 */

import java.awt.AWTException;
import java.awt.Button;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.lang.reflect.InvocationTargetException;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleStateSet;

public class ButtonTest {

    static Button button;
    final String accName = "Button Test";
    final String accDesc = "Regression Test:  javax.accessibility, Button";
    final AccessibleRole role = AccessibleRole.PUSH_BUTTON;
    static Frame frame;

    public void createGUI() {
        frame = new Frame("ButtonTest");
        button = new Button("This is a Button!");
        AccessibleContext ac = button.getAccessibleContext();

        ac.setAccessibleName(accName);
        ac.setAccessibleDescription(accDesc);

        frame.add(button);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args)
            throws InterruptedException, InvocationTargetException, AWTException {

        ButtonTest buttonTest = new ButtonTest();
        EventQueue.invokeAndWait(buttonTest::createGUI);

        Robot rbt = new Robot();
        rbt.waitForIdle();
        rbt.delay(5000);

        try {
            EventQueue.invokeAndWait(buttonTest::test);
        } finally {
            buttonTest.dispose();
        }
    }

    private void dispose()
            throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
    }

    public Component getComponent() {
        return button;
    }

    public void test() {
        Button b = (Button) getComponent();

        AccessibleContext ac = b.getAccessibleContext();
        AccessibleStateSet aset = ac.getAccessibleStateSet();
        if (aset == null) {
            throw new RuntimeException("getAccessibleStateSet should not return null");
        }
        AccessibleStateSetTester astr =
                new AccessibleStateSetTester(b, aset);
        astr.testAll();

        AccessibleTestUtils.verifyButtonAccessibility(
                b,
                accName,
                accDesc
        );
    }
}
