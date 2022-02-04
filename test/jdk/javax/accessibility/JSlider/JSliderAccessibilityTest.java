/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8262981
 * @summary Test JSlider Accessibility
 * @run main JSliderAccessibilityTest
 */

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleComponent;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleSelection;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.accessibility.AccessibleText;
import javax.accessibility.AccessibleValue;
import javax.swing.JFrame;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

public class JSliderAccessibilityTest {

    public static JFrame frame;
    public static final String accName = "JSlider Test";
    public static final String accDesc = "Regression Test:  javax" +
            ".accessibility, JSlider";
    public static JSlider jSlider;
    public static final AccessibleRole role = AccessibleRole.SLIDER;

    public static void createTestUI() {
        frame = new JFrame("JSlider Accessibility Test");
        jSlider = new JSlider();
        AccessibleContext ac = jSlider.getAccessibleContext();

        // Set the AccessibleName and Description for later one.
        ac.setAccessibleName(accName);
        ac.setAccessibleDescription(accDesc);
        frame.getContentPane().add(jSlider);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }

    public static void test() {
        AccessibleContext accessibleContext = jSlider.getAccessibleContext();
        if (accessibleContext == null) {
            throw new RuntimeException("getAccessibleContext returned null!");
        }

        String name = accessibleContext.getAccessibleName();
        if (name == null) {
            throw new RuntimeException("getAccessibleName returned null even though an Accessible name was explicitly set");
        }

        if (!name.equals(accName)) {
            throw new RuntimeException("getAccessibleName returned an incorrect name");
        }

        // AccessibleDescription
        String desc = accessibleContext.getAccessibleDescription();
        if (desc == null) {
            throw new RuntimeException("getAccessibleDescription returned null even though an Accessible description was explicitly set");
        }

        if (!desc.equals(accDesc)) {
            throw new RuntimeException("getAccessibleDescription returned an incorrect description");
        }

        // AccessibleRole
        AccessibleRole accessibleRole = accessibleContext.getAccessibleRole();
        if (accessibleRole == null) {
            throw new RuntimeException("getAccessibleRole should not return null");
        }

        if (accessibleRole != role) {
            throw new RuntimeException("the AccessibleRole for this button is incorrect");
        }

        AccessibleStateSet set = accessibleContext.getAccessibleStateSet();
        if (set == null) {
            throw new RuntimeException("getAccessibleStateSet should not return a null value");
        }

        if (set.contains(AccessibleState.BUSY)) {
            if (!jSlider.getValueIsAdjusting()) {
                throw new RuntimeException("AccessibleStateSet contains BUSY but this slider is not currently busy");
            }
        } else {
            if (jSlider.getValueIsAdjusting()) {
                throw new RuntimeException("AccessibleStateSet does not contain BUSY but this slider is currently busy");
            }
        }

        if (set.contains(AccessibleState.HORIZONTAL)) {
            if (set.contains(AccessibleState.VERTICAL)) {
                throw new RuntimeException("AccessibleStateSet shouldn't contain both HORIZONTAL and VERTICAL");
            }
            if (jSlider.getOrientation() != jSlider.HORIZONTAL) {
                throw new RuntimeException("AccessibleStateSet contains HORIZONTAL but this Slider's orientation is not horizontal");
            }
        } else {
            if (!set.contains(AccessibleState.VERTICAL)) {
                throw new RuntimeException("AccessibleStateSet for a JSlider must contain HORIZONTAL or VERTICAL but this one does not");
            }
            if (jSlider.getOrientation() != jSlider.VERTICAL) {
                throw new RuntimeException("AccessibleStateSet does not contain HORIZONTAL but this Slider's orientation is horizontal");
            }
        }

        if (set.contains(AccessibleState.VERTICAL)) {
            if (set.contains(AccessibleState.HORIZONTAL)) {
                throw new RuntimeException("AccessibleStateSet shouldn't contain both HORIZONTAL and VERTICAL");
            }
            if (jSlider.getOrientation() != jSlider.VERTICAL) {
                throw new RuntimeException("AccessibleStateSet contains VERTICAL but this Slider's orientation is not vertical");
            }
        } else {
            if (!set.contains(AccessibleState.HORIZONTAL)) {
                throw new RuntimeException("AccessibleStateSet for a JSlider must contain HORIZONTAL or VERTICAL but this one does not");
            }
            if (jSlider.getOrientation() != jSlider.HORIZONTAL) {
                //System.err.println(this.set.toString());
                throw new RuntimeException("AccessibleStateSet does not contain HORIZONTAL but this Slider's orientation is horizontal");
            }
        }

        // Locale
        Locale aLocale = accessibleContext.getLocale();
        if (aLocale == null) {
            throw new RuntimeException("AccessibleComponent.getLocale returned null");
        }

        Locale locale = jSlider.getLocale();
        if (locale == null) {
            throw new RuntimeException("JComponent.this.getLocale returned null");
        }

        if (!locale.equals(aLocale)) {
            throw new RuntimeException("An accessible component should not have a different locale than the component it represents");
        }

        // AccessibleAction
        AccessibleAction accessibleAction = accessibleContext.getAccessibleAction();
        if (accessibleAction != null) {
            if (accessibleAction.getAccessibleActionCount() != 2) {
                throw new RuntimeException("Expected that getAccessibleActionCount value to be 2 but got " + accessibleAction.getAccessibleActionCount());
            }

            if (!accessibleAction.getAccessibleActionDescription(0).equals("increment")) {
                throw new RuntimeException("Expected increment but got " + accessibleAction.getAccessibleActionDescription(0));
            }

            if (!accessibleAction.getAccessibleActionDescription(1).equals("decrement")) {
                throw new RuntimeException("Expected decrement but got " + accessibleAction.getAccessibleActionDescription(0));
            }

            if (accessibleAction.getAccessibleActionDescription(-1) != null) {
                throw new RuntimeException("Expected null but got " + accessibleAction.getAccessibleActionDescription(-1));
            }
        }

        AccessibleComponent accessibleComponent = accessibleContext.getAccessibleComponent();
        if (accessibleComponent == null) {
            throw new RuntimeException("getAccessibleComponent returned null");
        }

        // AccessibleSelection
        AccessibleSelection accessibleSelection = accessibleContext.getAccessibleSelection();
        if (accessibleSelection != null) {
            throw new RuntimeException("getAccessibleSelection should not return a non-null value for JSlider");
        }

        // AccessibleText
        AccessibleText accessibleText = accessibleContext.getAccessibleText();
        if (accessibleText != null) {
            throw new RuntimeException("getAccessibleText should not return a non-null value for JSlider");
        }

        // AccessibleValue
        AccessibleValue accessibleValue = accessibleContext.getAccessibleValue();
        if (accessibleValue == null) {
            throw new RuntimeException("getAccessibleValue should not return a null value for JSlider");
        }

        if (accessibleValue.getCurrentAccessibleValue() == null) {
            throw new RuntimeException("getCurrentAccessibleValue should not return a null value");
        }

        Number cur = accessibleValue.getCurrentAccessibleValue();
        Number min = accessibleValue.getMinimumAccessibleValue();
        Number max = accessibleValue.getMaximumAccessibleValue();

        if (cur.intValue() < min.intValue() || cur.intValue() > max.intValue()) {
            throw new RuntimeException("getCurrentAccessibleValue is out of range");
        }
        if (cur.intValue() != jSlider.getValue()) {
            throw new RuntimeException("getCurrentAccessibleValue returned an incorrect value");
        }

        if (min == null) {
            throw new RuntimeException("getMinimumAccessibleValue should not return a null value");
        }
        if (min.intValue() != jSlider.getMinimum()) {
            throw new RuntimeException("getMinimumAccessibleValue returned an incorrect value");
        }

        if (max == null) {
            throw new RuntimeException("getMaximumAccessibleValue should not return a null value");
        }
        if (max.intValue() != jSlider.getMaximum()) {
            throw new RuntimeException("getMaximumAccessibleValue returned an incorrect value");
        }

    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(JSliderAccessibilityTest::createTestUI);
        SwingUtilities.invokeAndWait(JSliderAccessibilityTest::test);
    }
}

