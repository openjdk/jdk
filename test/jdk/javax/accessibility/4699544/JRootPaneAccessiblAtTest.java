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

/*
 * @test
 * @bug 4699544
 * @key headful
 * @summary AccessibleJRootPane always returns null for getAccessibleAt
 * @run main JRootPaneAccessiblAtTest
 */
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleComponent;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

public class JRootPaneAccessiblAtTest extends JFrame {

    public JRootPaneAccessiblAtTest() {
        JRootPane rootPane = getRootPane();
        AccessibleComponent accessibleComponent =
            rootPane.getAccessibleContext().getAccessibleComponent();
        Accessible accessible = accessibleComponent
            .getAccessibleAt(accessibleComponent.getLocation());
        if (accessible == null) {
            throw new RuntimeException("Test Failed: AccessibleJRootPane "
                + "always returns null for getAccessibleAt()");
        } else {
            System.out.println("Test Passed: AccessibilityJRootPane returns "
                + accessible + " for getAccessibleAt()");
        }
    }

    public static void main(String args[]) throws Exception {
        SwingUtilities.invokeAndWait(() -> new JRootPaneAccessiblAtTest());
    }
}

