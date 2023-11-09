/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Robot;
import java.awt.Shape;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.View;

/*
 * @test
 * @bug 4174871
 * @key headful
 * @summary Tests if CELLSPACING attribute in HTML table is rendered.
 */

public class bug4174871 {
    private static JFrame frame;
    private static JTextPane pane;
    private static volatile boolean passed = false;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();

            SwingUtilities.invokeAndWait(bug4174871::createAndShowUI);
            robot.waitForIdle();
            robot.delay(500);

            SwingUtilities.invokeAndWait(bug4174871::testUI);

            if (!passed) {
                throw new RuntimeException("Test failed!!" +
                        " CELLSPACING attribute in HTML table is NOT rendered");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void createAndShowUI() {
        pane = new JTextPane();
        pane.setContentType("text/html");
        pane.setText("<html>"
                + "<html><head><table border=1 cellspacing=20>"
                + "<tr><td width=100>one</td><td width=100>two</td><td width=100>three</td></tr>"
                + "</table></body></html>");

        frame = new JFrame("Table CellSpacing Test");
        frame.getContentPane().add(pane);
        frame.setSize(600, 200);
        frame.setVisible(true);
    }

    private static void testUI() {
        int tableWidth = 0;
        Shape r = pane.getBounds();
        View v = pane.getUI().getRootView(pane);

        while (!(v instanceof javax.swing.text.html.ParagraphView)) {
            int n = v.getViewCount();
            Shape sh = v.getChildAllocation(n - 1, r);
            String viewName = v.getClass().getName();
            if (viewName.endsWith("TableView")) {
                tableWidth = r.getBounds().width;
            }
            v = v.getView(n - 1);
            if (sh != null) {
                r = sh;
            }
        }
        // tableWidth should be the sum of TD's widths (300)
        // and cellspacings (80)
        passed = (tableWidth >= 380);
    }
}
