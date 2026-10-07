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

import java.awt.Font;
import java.awt.MenuComponent;

import javax.accessibility.AccessibleComponent;

public class AccessibleMenuComponentTester {

    private final AccessibleComponent acomp;
    private final MenuComponent comp;

    public AccessibleMenuComponentTester(MenuComponent menuComponent,
                                         AccessibleComponent ac) {
        if (menuComponent == null) {
            throw new RuntimeException("MenuComponent should not be null");
        }

        if (ac == null) {
            throw new RuntimeException("AccessibleComponent should not be null");
        }

        this.comp = menuComponent;
        this.acomp = ac;
    }

    // The only method supported by MenuComponents is getFont(). Everything
    // else should return null.
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
    }

    public void testGetBounds() {
    }

    public void testGetCursor() {
    }

    public void testGetFont() {
        Font accessibleFont = acomp.getFont();
        Font componentFont = comp.getFont();

        if (componentFont == null) {
            if (accessibleFont != null) {
                throw new RuntimeException(
                        "MenuComponent.getFont returned null but " +
                                "AccessibleComponent.getFont did not");
            }
        } else if (!componentFont.equals(accessibleFont)) {
            throw new RuntimeException(
                    "AccessibleComponent.getFont does not match " +
                            "MenuComponent.getFont");
        }
    }

    public void testGetForeground() {
    }

    public void testGetLocation() {
    }

    public void testGetLocationOnScreen() {
    }

    public void testGetSize() {
    }

    public void testIsEnabled() {
    }

    public void testIsFocusTraversable() {
    }

    public void testIsShowing() {
    }

    public void testIsVisible() {
    }
}
