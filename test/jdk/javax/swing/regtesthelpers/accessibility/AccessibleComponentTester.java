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

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Objects;
import java.util.function.Supplier;

import javax.accessibility.AccessibleComponent;

public final class AccessibleComponentTester {

    private final AccessibleComponent accessibleComponent;
    private final Component component;

    public AccessibleComponentTester(Component component, AccessibleComponent accessibleComponent) {
        this.component = Objects.requireNonNull(component, "component must not be null");
        this.accessibleComponent = Objects.requireNonNull(accessibleComponent, "accessibleComponent must not be null");
    }

    public void test() {
        testGetBackground();
        testGetBounds();
        testGetCursor();
        testGetFont();
        testGetForeground();
        testGetLocation();
        testGetLocationOnScreen();
        testGetSize();
        testIsEnabled();
        testIsFocusTraversable();
        testIsShowing();
        testIsVisible();
    }

    public void testGetBackground() {
        assertEqual(
                "getBackground",
                component.getBackground(),
                accessibleComponent.getBackground()
        );
    }

    public void testGetBounds() {
        assertEqual(
                "getBounds",
                component.getBounds(),
                accessibleComponent.getBounds()
        );
    }

    public void testGetCursor() {
        assertEqual(
                "getCursor",
                component.getCursor(),
                accessibleComponent.getCursor()
        );
    }

    public void testGetFont() {
        assertEqual(
                "getFont",
                component.getFont(),
                accessibleComponent.getFont()
        );
    }

    public void testGetForeground() {
        assertEqual(
                "getForeground",
                component.getForeground(),
                accessibleComponent.getForeground()
        );
    }

    public void testGetLocation() {
        assertEqualWithStateHandling(
                "getLocation",
                component::getLocation,
                accessibleComponent::getLocation
        );
    }

    public void testGetLocationOnScreen() {
        assertEqualWithStateHandling(
                "getLocationOnScreen",
                component::getLocationOnScreen,
                accessibleComponent::getLocationOnScreen
        );
    }

    public void testGetSize() {
        assertEqual(
                "getSize",
                component.getSize(),
                accessibleComponent.getSize()
        );
    }

    public void testIsEnabled() {
        assertBooleanEqual(
                "isEnabled",
                component.isEnabled(),
                accessibleComponent.isEnabled()
        );
    }

    public void testIsFocusTraversable() {
        assertBooleanEqual(
                "isFocusTraversable",
                component.isFocusTraversable(),
                accessibleComponent.isFocusTraversable()
        );
    }

    public void testIsShowing() {
        assertBooleanEqual(
                "isShowing",
                component.isShowing(),
                accessibleComponent.isShowing()
        );
    }

    public void testIsVisible() {
        assertBooleanEqual(
                "isVisible",
                component.isVisible(),
                accessibleComponent.isVisible()
        );
    }

    private void assertEqual(String methodName, Object componentValue, Object accessibleValue) {
        if (!Objects.equals(componentValue, accessibleValue)) {
            throw new RuntimeException(buildMismatchMessage(methodName, componentValue, accessibleValue));
        }
    }

    private void assertBooleanEqual(String methodName, boolean componentValue, boolean accessibleValue) {
        if (componentValue != accessibleValue) {
            throw new RuntimeException(
                    String.format(
                            "Mismatch in %s: Component returned [%s], AccessibleComponent returned [%s]",
                            methodName, componentValue, accessibleValue
                    )
            );
        }
    }

    private <T> void assertEqualWithStateHandling(
            String methodName,
            Supplier<T> componentSupplier,
            Supplier<T> accessibleSupplier
    ) {
        try {
            T componentValue = componentSupplier.get();
            T accessibleValue = accessibleSupplier.get();
            assertEqual(methodName, componentValue, accessibleValue);
        } catch (java.awt.IllegalComponentStateException ex) {
            throw new RuntimeException(
                    "Component was not in a valid state when " + methodName + " was called. " +
                            "This is not necessarily an accessibility issue.",
                    ex
            );
        }
    }

    private String buildMismatchMessage(String methodName, Object componentValue, Object accessibleValue) {
        return String.format(
                "Mismatch in %s: Component returned [%s], AccessibleComponent returned [%s]",
                methodName, componentValue, accessibleValue
        );
    }
}
