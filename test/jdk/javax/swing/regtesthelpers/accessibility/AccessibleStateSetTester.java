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

import java.awt.Component;
import java.util.Objects;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleSelection;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.JComponent;

/**
 * Validates an {@link AccessibleStateSet} against the state of a component.
 *
 * <p>This class provides generic validation for common accessibility states.
 * Subclasses can extend it to add component-specific checks.</p>
 *
 * <p>This works for both Swing and AWT components that implement
 * {@link Accessible}.</p>
 */
public class AccessibleStateSetTester {

    private final AccessibleStateSet stateSet;
    private final Component component;

    public AccessibleStateSetTester(Component component, AccessibleStateSet stateSet) {
        this.component = Objects.requireNonNull(component, "component must not be null");
        this.stateSet = Objects.requireNonNull(stateSet, "stateSet must not be null");
    }

    /**
     * Runs all generic state validations plus component-specific hooks.
     */
    public void testAll() {
        testActive();
        testArmed();
        testBusy();
        testChecked();
        testCollapsed();
        testEditable();
        testEnabled();
        testExpandable();
        testExpanded();
        testFocusable();
        testFocused();
        testHorizontal();
        testIconified();
        testModal();
        testMultiLine();
        testMultiSelectable();
        testOpaque();
        testPressed();
        testResizable();
        testSelectable();
        testSelected();
        testShowing();
        testSingleLine();
        testTransient();
        testVertical();
        testVisible();
    }

    public void testEnabled() {
        assertStateMatches(
                AccessibleState.ENABLED,
                component.isEnabled(),
                "component is enabled",
                "component is not enabled"
        );
    }

    public void testFocusable() {
        assertStateMatches(
                AccessibleState.FOCUSABLE,
                component.isFocusable(),
                "component is focusable",
                "component is not focusable"
        );
    }

    public void testFocused() {
        boolean focused = isFocused();

        if (stateSet.contains(AccessibleState.FOCUSED)) {
            if (!stateSet.contains(AccessibleState.FOCUSABLE)) {
                throw new RuntimeException(
                        "AccessibleStateSet contains FOCUSED but not FOCUSABLE"
                );
            }
            if (!focused) {
                throw new RuntimeException(
                        "AccessibleStateSet contains FOCUSED but the component does not have focus"
                );
            }
        } else if (focused) {
            throw new RuntimeException(
                    "AccessibleStateSet does not contain FOCUSED but the component has focus"
            );
        }
    }

    public void testShowing() {
        assertStateMatches(
                AccessibleState.SHOWING,
                component.isShowing(),
                "component is showing",
                "component is not showing"
        );
    }

    public void testVisible() {
        assertStateMatches(
                AccessibleState.VISIBLE,
                component.isVisible(),
                "component is visible",
                "component is not visible"
        );
    }

    public void testSelectable() {
        AccessibleState selectionStatus = getSelectionStatus();

        if (stateSet.contains(AccessibleState.SELECTABLE)) {
            if (selectionStatus != AccessibleState.SELECTABLE
                    && selectionStatus != AccessibleState.SELECTED) {
                throw new RuntimeException(
                        "AccessibleStateSet contains SELECTABLE but the component is not selectable"
                );
            }
        } else if (selectionStatus == AccessibleState.SELECTABLE) {
            throw new RuntimeException(
                    "AccessibleStateSet does not contain SELECTABLE but the component is selectable"
            );
        }
    }

    public void testSelected() {
        AccessibleState selectionStatus = getSelectionStatus();

        if (stateSet.contains(AccessibleState.SELECTED)) {
            if (!stateSet.contains(AccessibleState.SELECTABLE)) {
                throw new RuntimeException(
                        "AccessibleStateSet contains SELECTED but not SELECTABLE"
                );
            }
            if (selectionStatus != AccessibleState.SELECTED) {
                throw new RuntimeException(
                        "AccessibleStateSet contains SELECTED but the component is not selected"
                );
            }
        } else if (selectionStatus == AccessibleState.SELECTED) {
            throw new RuntimeException(
                    "AccessibleStateSet does not contain SELECTED but the component is selected"
            );
        }
    }

    public void testOpaque() {
        if (!(component instanceof JComponent jComponent)) {
            return;
        }

        assertStateMatches(
                AccessibleState.OPAQUE,
                jComponent.isOpaque(),
                "component is opaque",
                "component is not opaque"
        );
    }

    /**
     * Returns true if the component currently has focus.
     */
    public boolean isFocused() {
        return component.hasFocus();
    }

    /**
     * Determines whether the component is selectable or selected.
     *
     * @return {@link AccessibleState#SELECTED} if selected,
     * {@link AccessibleState#SELECTABLE} if selectable but not selected,
     * or {@code null} if neither applies
     */
    public AccessibleState getSelectionStatus() {
        if (!(component instanceof Accessible accessible)) {
            return null;
        }

        AccessibleContext context = accessible.getAccessibleContext();
        if (context == null) {
            return null;
        }

        Accessible parent = context.getAccessibleParent();
        if (parent == null) {
            return null;
        }

        AccessibleContext parentContext = parent.getAccessibleContext();
        if (parentContext == null) {
            return null;
        }

        AccessibleSelection selection = parentContext.getAccessibleSelection();
        if (selection == null) {
            return null;
        }

        int index = context.getAccessibleIndexInParent();
        if (index < 0) {
            return AccessibleState.SELECTABLE;
        }

        return selection.isAccessibleChildSelected(index)
                ? AccessibleState.SELECTED
                : AccessibleState.SELECTABLE;
    }

    private void assertStateMatches(
            AccessibleState state,
            boolean actualCondition,
            String presentMessage,
            String absentMessage) {

        boolean presentInStateSet = stateSet.contains(state);

        if (presentInStateSet && !actualCondition) {
            throw new RuntimeException(
                    "AccessibleStateSet contains " + state + " but " + absentMessage
            );
        }

        if (!presentInStateSet && actualCondition) {
            throw new RuntimeException(
                    "AccessibleStateSet does not contain " + state + " but " + presentMessage
            );
        }
    }

    // Component-specific hooks (intentionally empty by default)

    public void testActive() {
    }

    public void testArmed() {
    }

    public void testBusy() {
    }

    public void testChecked() {
    }

    public void testCollapsed() {
    }

    public void testEditable() {
    }

    public void testExpandable() {
    }

    public void testExpanded() {
    }

    public void testHorizontal() {
    }

    public void testIconified() {
    }

    public void testModal() {
    }

    public void testMultiLine() {
    }

    public void testMultiSelectable() {
    }

    public void testPressed() {
    }

    public void testResizable() {
    }

    public void testSingleLine() {
    }

    public void testTransient() {
    }

    public void testVertical() {
    }
}
