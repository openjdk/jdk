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
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Label;
import java.awt.List;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuComponent;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.PopupMenu;
import java.awt.Scrollbar;
import java.awt.ScrollPane;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.Window;
import java.util.Locale;
import java.util.Objects;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleComponent;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleSelection;
import javax.accessibility.AccessibleState;
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

        assertExpectedString("getAccessibleName",
                expectedName, context.getAccessibleName());
        assertExpectedString("getAccessibleDescription",
                expectedDescription, context.getAccessibleDescription());

        AccessibleRole actualRole = context.getAccessibleRole();
        if (actualRole == null) {
            throw new RuntimeException("getAccessibleRole returned null");
        }

        if (!expectedRole.equals(actualRole)) {
            throw new RuntimeException(
                    "getAccessibleRole returned [" + actualRole +
                            "]; expected [" + expectedRole + "]");
        }

        AccessibleStateSet stateSet = context.getAccessibleStateSet();
        if (stateSet == null) {
            throw new RuntimeException("getAccessibleStateSet returned null");
        }

        assertPresence("getAccessibleAction",
                expectAction, context.getAccessibleAction());
        assertPresence("getAccessibleSelection",
                expectSelection, context.getAccessibleSelection());
        assertPresence("getAccessibleText",
                expectText, context.getAccessibleText());
        assertPresence("getAccessibleValue",
                expectValue, context.getAccessibleValue());
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
        Objects.requireNonNull(component, "Component must not be null");

        AccessibleContext context = component.getAccessibleContext();
        if (context == null) {
            throw new RuntimeException("getAccessibleContext returned null");
        }

        verifyAccessibleContextCommon(
                context,
                expectedName,
                expectedDescription,
                expectedRole,
                expectAction,
                expectSelection,
                expectText,
                expectValue);

        assertLocaleMatches(component, context);

        AccessibleComponent accessibleComponent =
                context.getAccessibleComponent();
        if (accessibleComponent == null) {
            throw new RuntimeException("getAccessibleComponent returned null");
        }

        new AccessibleComponentTester(component, accessibleComponent).test();
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
                true);

        AccessibleContext context = button.getAccessibleContext();
        verifyClickAction(context, "Button");
        verifyZeroAccessibleValue(context, "Button");
    }

    public static void verifyCheckboxAccessibility(
            Checkbox checkbox,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(checkbox, "Checkbox must not be null");

        verifyAWTComponentAccessibility(
                checkbox,
                expectedName,
                expectedDescription,
                AccessibleRole.CHECK_BOX,
                true,
                false,
                false,
                true);
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
                false);
    }

    public static void verifyFrameAccessibility(
            Frame frame,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(frame, "Frame must not be null");

        verifyAWTComponentAccessibility(
                frame,
                expectedName,
                expectedDescription,
                AccessibleRole.FRAME,
                false,
                false,
                false,
                false);
    }

    public static void verifyLabelAccessibility(
            Label label,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(label, "Label must not be null");

        verifyAWTComponentAccessibility(
                label,
                expectedName,
                expectedDescription,
                AccessibleRole.LABEL,
                false,
                false,
                false,
                false);
    }

    public static void verifyListAccessibility(
            List list,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(list, "List must not be null");

        verifyAWTComponentAccessibility(
                list,
                expectedName,
                expectedDescription,
                AccessibleRole.LIST,
                false,
                true,
                false,
                false);

        verifyListChildren(list);
    }

    public static void verifyPanelAccessibility(
            Panel panel,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(panel, "Panel must not be null");

        verifyAWTComponentAccessibility(
                panel,
                expectedName,
                expectedDescription,
                AccessibleRole.PANEL,
                false,
                false,
                false,
                false);

        verifyContainerChildren(panel, "Panel");
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
                true);

        AccessibleValue value =
                scrollbar.getAccessibleContext().getAccessibleValue();

        assertIntValueEquals(
                "getCurrentAccessibleValue",
                scrollbar.getValue(),
                value.getCurrentAccessibleValue());

        assertIntValueEquals(
                "getMinimumAccessibleValue",
                scrollbar.getMinimum(),
                value.getMinimumAccessibleValue());

        assertIntValueEquals(
                "getMaximumAccessibleValue",
                scrollbar.getMaximum(),
                value.getMaximumAccessibleValue());

        if (!value.setCurrentAccessibleValue(Integer.valueOf(5))) {
            throw new RuntimeException(
                    "setCurrentAccessibleValue should not return false " +
                            "for Scrollbar");
        }

        assertIntValueEquals(
                "getCurrentAccessibleValue after setCurrentAccessibleValue(5)",
                5,
                value.getCurrentAccessibleValue());

        if (scrollbar.getValue() != 5) {
            throw new RuntimeException(
                    "setCurrentAccessibleValue should change the Scrollbar " +
                            "value");
        }
    }

    public static void verifyScrollPaneAccessibility(
            ScrollPane scrollPane,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(scrollPane, "ScrollPane must not be null");

        verifyAWTComponentAccessibility(
                scrollPane,
                expectedName,
                expectedDescription,
                AccessibleRole.SCROLL_PANE,
                false,
                false,
                false,
                false);
    }

    public static void verifyTextAreaAccessibility(
            TextArea textArea,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(textArea, "TextArea must not be null");

        verifyAWTComponentAccessibility(
                textArea,
                expectedName,
                expectedDescription,
                AccessibleRole.TEXT,
                false,
                false,
                true,
                false);
    }

    public static void verifyTextFieldAccessibility(
            TextField textField,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(textField, "TextField must not be null");

        verifyAWTComponentAccessibility(
                textField,
                expectedName,
                expectedDescription,
                AccessibleRole.TEXT,
                false,
                false,
                true,
                false);
    }

    public static void verifyWindowAccessibility(
            Window window,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(window, "Window must not be null");

        verifyAWTComponentAccessibility(
                window,
                expectedName,
                expectedDescription,
                AccessibleRole.WINDOW,
                false,
                false,
                false,
                false);
    }

    public static void verifyMenuAccessibility(
            Menu menu,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(menu, "Menu must not be null");

        verifyMenuComponentAccessibility(
                menu,
                expectedName,
                expectedDescription,
                AccessibleRole.MENU,
                true,
                true,
                false,
                true,
                "Menu");

        AccessibleContext context = menu.getAccessibleContext();
        verifyClickAction(context, "Menu");
        verifyZeroAccessibleValue(context, "Menu");
    }

    public static void verifyMenuBarAccessibility(
            MenuBar menuBar,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(menuBar, "MenuBar must not be null");

        verifyMenuComponentAccessibility(
                menuBar,
                expectedName,
                expectedDescription,
                AccessibleRole.MENU_BAR,
                false,
                true,
                false,
                false,
                "MenuBar");
    }

    public static void verifyMenuItemAccessibility(
            MenuItem menuItem,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(menuItem, "MenuItem must not be null");

        verifyMenuComponentAccessibility(
                menuItem,
                expectedName,
                expectedDescription,
                AccessibleRole.MENU_ITEM,
                true,
                true,
                false,
                true,
                "MenuItem");

        AccessibleContext context = menuItem.getAccessibleContext();
        verifyClickAction(context, "MenuItem");
        verifyZeroAccessibleValue(context, "MenuItem");
    }

    public static void verifyPopupMenuAccessibility(
            PopupMenu popupMenu,
            String expectedName,
            String expectedDescription) {
        Objects.requireNonNull(popupMenu, "PopupMenu must not be null");

        verifyMenuComponentAccessibility(
                popupMenu,
                expectedName,
                expectedDescription,
                AccessibleRole.POPUP_MENU,
                true,
                true,
                false,
                true,
                "PopupMenu");

        AccessibleContext context = popupMenu.getAccessibleContext();
        verifyClickAction(context, "PopupMenu");
        verifyZeroAccessibleValue(context, "PopupMenu");
    }

    private static void verifyMenuComponentAccessibility(
            MenuComponent menuComponent,
            String expectedName,
            String expectedDescription,
            AccessibleRole expectedRole,
            boolean expectAction,
            boolean expectSelection,
            boolean expectText,
            boolean expectValue,
            String componentName) {
        Objects.requireNonNull(menuComponent,
                componentName + " must not be null");

        AccessibleContext context = menuComponent.getAccessibleContext();
        if (context == null) {
            throw new RuntimeException("getAccessibleContext returned null");
        }

        verifyAccessibleContextCommon(
                context,
                expectedName,
                expectedDescription,
                expectedRole,
                expectAction,
                expectSelection,
                expectText,
                expectValue);

        AccessibleComponent accessibleComponent =
                context.getAccessibleComponent();
        if (accessibleComponent == null) {
            throw new RuntimeException("getAccessibleComponent returned null");
        }

        new AccessibleMenuComponentTester(
                menuComponent, accessibleComponent).test();
    }

    private static void verifyClickAction(AccessibleContext context,
                                          String componentName) {
        AccessibleAction action = context.getAccessibleAction();
        if (action == null) {
            throw new RuntimeException(
                    "getAccessibleAction should not return null for " +
                            componentName);
        }

        int actionCount = action.getAccessibleActionCount();
        if (actionCount != 1) {
            throw new RuntimeException(
                    "getAccessibleActionCount returned the wrong number for " +
                            componentName);
        }

        String actionDescription = action.getAccessibleActionDescription(0);
        if (!"click".equals(actionDescription)) {
            throw new RuntimeException(
                    "getAccessibleActionDescription returned the wrong " +
                            "description for " + componentName);
        }
    }

    private static void verifyZeroAccessibleValue(AccessibleContext context,
                                                  String componentName) {
        AccessibleValue value = context.getAccessibleValue();
        if (value == null) {
            throw new RuntimeException(
                    "getAccessibleValue should not return null for " +
                            componentName);
        }

        assertIntValueEquals(
                "getCurrentAccessibleValue",
                0,
                value.getCurrentAccessibleValue());

        assertIntValueEquals(
                "getMinimumAccessibleValue",
                0,
                value.getMinimumAccessibleValue());

        assertIntValueEquals(
                "getMaximumAccessibleValue",
                0,
                value.getMaximumAccessibleValue());

        if (value.setCurrentAccessibleValue(Integer.valueOf(5))) {
            throw new RuntimeException(
                    "setCurrentAccessibleValue should return false for " +
                            componentName);
        }

        assertIntValueEquals(
                "getCurrentAccessibleValue after setCurrentAccessibleValue(5)",
                0,
                value.getCurrentAccessibleValue());
    }

    private static void verifyListChildren(List list) {
        AccessibleContext context = list.getAccessibleContext();

        int childCount = context.getAccessibleChildrenCount();
        if (childCount != list.getItemCount()) {
            throw new RuntimeException(
                    "getAccessibleChildrenCount returned an incorrect value " +
                            "for List");
        }

        for (int i = 0; i < childCount; i++) {
            Accessible child = context.getAccessibleChild(i);
            if (child == null) {
                throw new RuntimeException(
                        "getAccessibleChild returned null for child " + i);
            }

            AccessibleContext childContext = child.getAccessibleContext();
            if (childContext == null) {
                throw new RuntimeException(
                        "getAccessibleContext returned null for List child " +
                                i);
            }

            if (childContext.getAccessibleRole() != AccessibleRole.LIST_ITEM) {
                throw new RuntimeException(
                        "The AccessibleRole of AccessibleAWTListChild is " +
                                "incorrect for child " + i);
            }

            if (childContext.getAccessibleIndexInParent() != i) {
                throw new RuntimeException(
                        "getAccessibleIndexInParent returned an incorrect " +
                                "value for AccessibleAWTListChild " + i);
            }

            AccessibleStateSet childStateSet =
                    childContext.getAccessibleStateSet();
            if (childStateSet == null) {
                throw new RuntimeException(
                        "getAccessibleStateSet returned null for List child " +
                                i);
            }

            boolean accessibleSelected =
                    childStateSet.contains(AccessibleState.SELECTED);
            boolean listSelected = list.isIndexSelected(i);

            if (accessibleSelected != listSelected) {
                throw new RuntimeException(
                        "getAccessibleStateSet reports that list item " + i +
                                (accessibleSelected ? " is " : " is not ") +
                                "selected but List reports that it " +
                                (listSelected ? "is" : "is not") + " selected");
            }
        }
    }

    private static void verifyContainerChildren(Component component,
                                                String componentName) {
        AccessibleContext context = component.getAccessibleContext();

        int childCount = context.getAccessibleChildrenCount();
        if (childCount != component.getAccessibleContext()
                .getAccessibleChildrenCount()) {
            throw new RuntimeException(
                    "getAccessibleChildrenCount returned an incorrect value " +
                            "for " + componentName);
        }

        for (int i = 0; i < childCount; i++) {
            Accessible child = context.getAccessibleChild(i);
            if (child == null) {
                throw new RuntimeException(
                        "getAccessibleChild returned null for " +
                                componentName + " child " + i);
            }

            AccessibleContext childContext = child.getAccessibleContext();
            if (childContext == null) {
                throw new RuntimeException(
                        "getAccessibleContext returned null for " +
                                componentName + " child " + i);
            }
        }
    }

    private static void assertExpectedString(String methodName,
                                             String expected,
                                             String actual) {
        if (expected == null) {
            throw new RuntimeException(
                    "Expected value for " + methodName + " should not be null");
        }

        if (actual == null) {
            throw new RuntimeException(
                    methodName + " returned null; expected [" + expected + "]");
        }

        if (!expected.equals(actual)) {
            throw new RuntimeException(
                    methodName + " returned [" + actual +
                            "]; expected [" + expected + "]");
        }
    }

    private static void assertPresence(String methodName,
                                       boolean expectedPresent,
                                       Object value) {
        if (expectedPresent && value == null) {
            throw new RuntimeException(
                    methodName + " returned null but should not");
        }

        if (!expectedPresent && value != null) {
            throw new RuntimeException(
                    methodName + " returned non-null but should return null");
        }
    }

    private static void assertLocaleMatches(Component component,
                                            AccessibleContext context) {
        Locale componentLocale = component.getLocale();
        Locale accessibleLocale = context.getLocale();

        if (componentLocale == null) {
            throw new RuntimeException("Component.getLocale returned null");
        }

        if (accessibleLocale == null) {
            throw new RuntimeException(
                    "AccessibleContext.getLocale returned null");
        }

        if (!componentLocale.equals(accessibleLocale)) {
            throw new RuntimeException(
                    "AccessibleContext.getLocale returned [" +
                            accessibleLocale + "], but Component.getLocale returned [" +
                            componentLocale + "]");
        }
    }

    private static void assertIntValueEquals(String methodName,
                                             int expected,
                                             Number actual) {
        if (actual == null) {
            throw new RuntimeException(
                    methodName + " returned null; expected [" + expected + "]");
        }

        if (actual.intValue() != expected) {
            throw new RuntimeException(
                    methodName + " returned [" + actual +
                            "]; expected [" + expected + "]");
        }
    }
}
