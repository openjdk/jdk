/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8322239
 * @summary [macos] a11y : java.lang.NullPointerException is thrown when
 *          focus is moved on the JTabbedPane
 * @key headful
 * @run main TabbedPaneNPECheck
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleComponent;
import javax.accessibility.AccessibleContext;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public class TabbedPaneNPECheck {
    JTabbedPane pane;
    JFrame mainFrame;
    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        TabbedPaneNPECheck me = new TabbedPaneNPECheck();
        SwingUtilities.invokeAndWait(me::setupGUI);
        try {
            SwingUtilities.invokeAndWait(me::test);
        } finally {
            SwingUtilities.invokeAndWait(me::shutdownGUI);
        }
    }

    public void setupGUI() {
        mainFrame = new JFrame("TabbedPaneNPECheck");
        pane = new JTabbedPane();
        Dimension panelSize = new Dimension(200, 200);
        for (int i = 0; i < 25; i++) {
            JPanel p = new JPanel();
            p.setMinimumSize(panelSize);
            p.setMaximumSize(panelSize);
            p.setSize(panelSize);
            pane.addTab("Tab no." + i, p);
        }
        mainFrame.setLayout(new BorderLayout());
        mainFrame.add(pane, BorderLayout.CENTER);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setSize(250, 250);
        mainFrame.setVisible(true);
    }

    public void test() {
        AccessibleContext context = pane.getAccessibleContext();
        int nChild = context.getAccessibleChildrenCount();
        for (int i = 0; i < nChild; i++) {
            Accessible accessible = context.getAccessibleChild(i);
            if (accessible instanceof AccessibleComponent) {
                try {
                    AccessibleComponent component = (AccessibleComponent) accessible;
                    Point p = component.getLocationOnScreen();
                    Rectangle r = component.getBounds();
                } catch (NullPointerException npe) {
                    throw new RuntimeException("Unexpected NullPointerException " +
                            "while getting accessible component bounds: ", npe);
                }
            }
        }
    }

    public void shutdownGUI() {
        if (mainFrame != null) {
            mainFrame.setVisible(false);
            mainFrame.dispose();
        }
    }
}
