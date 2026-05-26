/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Choice;
import java.awt.Component;
import java.awt.Scrollbar;
import java.util.Locale;
import java.util.Objects;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleComponent;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleSelection;
import javax.accessibility.AccessibleStateSet;
import javax.accessibility.AccessibleText;
import javax.accessibility.AccessibleValue;

public final class AccessibleTestUtils {

    private AccessibleTestUtils() {
    }

    public static void verifyAccessibleContextCommon(
            AccessibleContext context,
            String expectedName,
            String expectedDescription,
            AccessibleRole expectedRole,
            boolean expectAction,
            boolean expectSelection,
            boolean expectText,
            boolean expectValue) {

        Objects.requireNonNull(context, "AccessibleContext must not be null");

        assertExpectedString(
                "getAccessibleName",
                expectedName,
                context.getAccessibleName()
        );

        assertExpectedString(
                "getAccessibleDescription",
                expectedDescription,
                context.getAccessibleDescription()
        );

        if (expectedRole != null) {
            AccessibleRole actualRole = context.getAccessibleRole();
            if (actualRole == null) {
                throw new RuntimeException("getAccessibleRole returned null");
            }
            if (!expectedRole.equals(actualRole)) {
                throw new RuntimeException(String.format(
                        "getAccessibleRole returned [%s]; expected [%s]",
                        actualRole, expectedRole
                ));
            }
        }

        AccessibleStateSet stateSet = context.getAccessibleStateSet();
        if (stateSet == null) {
            throw new RuntimeException("getAccessibleStateSet returned null");
        }

        assertPresence("getAccessibleAction", expectAction, context.getAccessibleAction());
        assertPresence("getAccessibleSelection", expectSelection, context.getAccessibleSelection());
        assertPresence("getAccessibleText", expectText, context.getAccessibleText());
        assertPresence("getAccessibleValue", expectValue, context.getAccessibleValue());
    }

    public static void verifyAWTComponentAccessibility(
            Component component,
            String expectedName,
            String expectedDescription,
            AccessibleRole expectedRole,
            boolean expectAction,
            boolean expectSelection,
            boolean expectText,
            boolean expectValue) {

        Objects.requireNonNull(component, "Component under test must not be null");

        AccessibleContext context = component.getAccessibleContext();
        verifyAccessibleContextCommon(
                context,
                expectedName,
                expectedDescription,
                expectedRole,
                expectAction,
                expectSelection,
                expectText,
                expectValue
        );

        assertLocaleMatches(component, context);

        AccessibleComponent accessibleComponent = context.getAccessibleComponent();
        if (accessibleComponent == null) {
            throw new RuntimeException("getAccessibleComponent returned null");
        }

        new AccessibleComponentTester(component, accessibleComponent).test();
    }

    public static void verifyChoiceAccessibility(
            Choice choice,
            String expectedName,
            String expectedDescription) {

        Objects.requireNonNull(choice, "Choice must not be null");

        verifyAWTComponentAccessibility(
                choice,
                expectedName,
                expectedDescription,
                AccessibleRole.COMBO_BOX,
                true,
                false,
                false,
                false
        );

        AccessibleContext context = choice.getAccessibleContext();

        AccessibleAction action = context.getAccessibleAction();
        if (action == null) {
            throw new RuntimeException("getAccessibleAction should not return null for Choice");
        }

        AccessibleValue value = context.getAccessibleValue();
        if (value != null) {
            throw new RuntimeException("getAccessibleValue should return null for Choice");
        }
    }

    public static void verifyScrollbarAccessibility(
            Scrollbar scrollbar,
            String expectedName,
            String expectedDescription) {

        Objects.requireNonNull(scrollbar, "Scrollbar must not be null");

        verifyAWTComponentAccessibility(
                scrollbar,
                expectedName,
                expectedDescription,
                AccessibleRole.SCROLL_BAR,
                false,
                false,
                false,
                true
        );

        AccessibleValue value = scrollbar.getAccessibleContext().getAccessibleValue();
        if (value == null) {
            throw new RuntimeException("getAccessibleValue should not return null for Scrollbar");
        }

        assertIntValueEquals(
                "getCurrentAccessibleValue",
                scrollbar.getValue(),
                value.getCurrentAccessibleValue()
        );

        assertIntValueEquals(
                "getMinimumAccessibleValue",
                scrollbar.getMinimum(),
                value.getMinimumAccessibleValue()
        );

        assertIntValueEquals(
                "getMaximumAccessibleValue",
                scrollbar.getMaximum(),
                value.getMaximumAccessibleValue()
        );

        if (!value.setCurrentAccessibleValue(Integer.valueOf(5))) {
            throw new RuntimeException("setCurrentAccessibleValue(5) returned false for Scrollbar");
        }

        assertIntValueEquals(
                "getCurrentAccessibleValue after setCurrentAccessibleValue(5)",
                5,
                value.getCurrentAccessibleValue()
        );

        if (scrollbar.getValue() != 5) {
            throw new RuntimeException(
                    "setCurrentAccessibleValue(5) did not update Scrollbar.getValue(); actual value: "
                            + scrollbar.getValue()
            );
        }
    }

    public static void verifyButtonAccessibility(
            Button button,
            String expectedName,
            String expectedDescription) {

        Objects.requireNonNull(button, "Button must not be null");

        verifyAWTComponentAccessibility(
                button,
                expectedName,
                expectedDescription,
                AccessibleRole.PUSH_BUTTON,
                true,
                false,
                false,
                true
        );

        AccessibleContext context = button.getAccessibleContext();

        AccessibleAction action = context.getAccessibleAction();
        if (action == null) {
            throw new RuntimeException("getAccessibleAction should not return null for Button");
        }

        int actionCount = action.getAccessibleActionCount();
        if (actionCount != 1) {
            throw new RuntimeException(
                    "getAccessibleActionCount should return 1 for Button; got " + actionCount
            );
        }

        String actionDescription = action.getAccessibleActionDescription(0);
        if (!"click".equals(actionDescription)) {
            throw new RuntimeException(
                    "getAccessibleActionDescription(0) should return \"click\" for Button; got ["
                            + actionDescription + "]"
            );
        }

        AccessibleValue value = context.getAccessibleValue();
        if (value == null) {
            throw new RuntimeException("getAccessibleValue should not return null for Button");
        }

        assertIntValueEquals("getCurrentAccessibleValue", 0, value.getCurrentAccessibleValue());
        assertIntValueEquals("getMinimumAccessibleValue", 0, value.getMinimumAccessibleValue());
        assertIntValueEquals("getMaximumAccessibleValue", 0, value.getMaximumAccessibleValue());

        if (value.setCurrentAccessibleValue(Integer.valueOf(5))) {
            throw new RuntimeException(
                    "setCurrentAccessibleValue(5) should return false for Button"
            );
        }

        assertIntValueEquals(
                "getCurrentAccessibleValue after setCurrentAccessibleValue(5)",
                0,
                value.getCurrentAccessibleValue()
        );
    }

    private static void assertExpectedString(String methodName, String expected, String actual) {
        if (expected == null) {
            throw new RuntimeException("Excepted value is null. Provide " +
                    "excepted value");
        }

        if (actual == null) {
            throw new RuntimeException(methodName + " returned null; expected" +
                    " [" + expected + "]");
        }
        if (!expected.equals(actual)) {
            throw new RuntimeException(methodName + " returned [" + actual +
                    "]; expected [" + expected + "]");
        }
    }

    private static void assertPresence(String methodName, boolean expectedPresent, Object value) {
        if (expectedPresent && value == null) {
            throw new RuntimeException(methodName + " returned null but was expected");
        }
        if (!expectedPresent && value != null) {
            throw new RuntimeException(methodName + " returned non-null but " +
                    "was expected to be null");
        }
    }

    private static void assertLocaleMatches(Component component, AccessibleContext context) {
        Locale componentLocale = component.getLocale();
        Locale accessibleLocale = context.getLocale();

        if (componentLocale == null) {
            throw new RuntimeException("Component.getLocale returned null");
        }
        if (accessibleLocale == null) {
            throw new RuntimeException("AccessibleContext.getLocale returned null");
        }
        if (!componentLocale.equals(accessibleLocale)) {
            throw new RuntimeException(String.format(
                    "AccessibleContext.getLocale returned [%s], but Component" +
                            ".getLocale returned [%s]",
                    accessibleLocale, componentLocale
            ));
        }
    }

    private static void assertIntValueEquals(String methodName, int expected, Number actual) {
        if (actual == null) {
            throw new RuntimeException(methodName + " returned null; expected [" + expected + "]");
        }
        if (actual.intValue() != expected) {
            throw new RuntimeException(
                    methodName + " returned [" + actual + "]; expected [" + expected + "]"
            );
        }
    }
}
