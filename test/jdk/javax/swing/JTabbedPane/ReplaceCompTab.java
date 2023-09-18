/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4213896 4228439
 * @summary Ensure that inserting a new tab with a component
 * where that component already exists as another tab is handled
 * properly. The old tab should be removed and the new tab added.
 * @key headful
 * @run main ReplaceCompTab
 */

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public class ReplaceCompTab {
    static JFrame f;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                f = new JFrame("ReplaceCompTab");
                JTabbedPane tabbedpane = new JTabbedPane();
                f.getContentPane().add(tabbedpane, BorderLayout.CENTER);

                JPanel comp = new JPanel();

                // Add first tab
                tabbedpane.addTab("First(temp)", comp);

                // Add second tab with same component (should just replace first one)
                tabbedpane.insertTab("First", null, comp, "component added next", 0);

                // Check to ensure only a single tab exists
                if (tabbedpane.getTabCount() > 1) {
                    throw new RuntimeException("Only one tab should exist");
                }
                // Check to make sure second tab correctly replaced the first
                if (!(tabbedpane.getTitleAt(0).equals("First"))) {
                    throw new RuntimeException("Tab not replaced correctly");
                }
                // Check to make sure adding null continues to work
                try {
                    tabbedpane.addTab("Second", null);
                } catch (Exception e) {
                    throw new RuntimeException("Adding first null " +
                            "component failed:", e);
                }
                try {
                    tabbedpane.addTab("Third", null);
                } catch (Exception e) {
                    throw new RuntimeException("Adding subsequent null " +
                            "component failed: ", e);
                }
                try {
                    tabbedpane.setComponentAt(1, new JLabel("Second Component"));
                    tabbedpane.setComponentAt(2, new JLabel("Third Component"));
                } catch (Exception e) {
                    throw new RuntimeException("Setting null component " +
                            "to non-null failed: ", e);
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }
}
