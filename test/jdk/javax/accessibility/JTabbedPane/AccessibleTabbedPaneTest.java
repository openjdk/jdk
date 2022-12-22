/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.accessibility.AccessibleValue;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * @test
 * @bug 8283387
 * @summary [macos] a11y : Screen magnifier does not show selected Tab
 *          JTabbedPane accessible children had no AccessibleValue manipulation API
 *          Testing this API since it was added in the fix for 8283387
 * @run main AccessibleTabbedPaneTest
 */

public class AccessibleTabbedPaneTest {
    public static void main(String[] args) {
        JTabbedPane pane = new JTabbedPane();
        JPanel p1, p2, p3;
        p1 = new JPanel();
        p2 = new JPanel();
        p3 = new JPanel();
        pane.add("One", p1);
        pane.add("Two", p2);
        pane.add("Three", p3);
        for (int i = 0; i < pane.getAccessibleContext().getAccessibleChildrenCount(); i++) {
            if (pane.getAccessibleContext()
                    .getAccessibleChild(i)
                    .getAccessibleContext()
                    .getAccessibleValue() == null) {
                throw new RuntimeException("Test failed, accessible value for tab "+ i + " is null");
            }
        }
        AccessibleValue p2a = pane.getAccessibleContext()
                                  .getAccessibleChild(1)
                                  .getAccessibleContext()
                                  .getAccessibleValue();
        // Select second tab using a11y API.
        if (p2a.setCurrentAccessibleValue(1)) {
            if (pane.getSelectedIndex() != 1) {
                throw new RuntimeException("Can not change tab selection using a11y API");
            }
        }

        // Try to deselect it - that should not be allowed
        if (p2a.setCurrentAccessibleValue(0)) {
            throw new RuntimeException("We should not be able to deselect "
                    + "currently selected tab via a11y API");
        }
    }
}
