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

/*
 * @test
 * @bug 4690812
 * @summary  Tests if tables are correctly formatted in some cases
 * @key headful
 * @run main bug4690812
*/

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.text.View;
import javax.swing.text.html.HTMLEditorKit;

import java.awt.Robot;
import java.awt.Shape;

public class bug4690812 {

    private static boolean passed = false;

    private static JEditorPane jep;
    private static JFrame f;

    public void init() {

        String text =
            "<table cellpadding=0 cellspacing=0 border=0 width=100%>" +
            "<tr><td width=100%>a</td><td></td></tr>" +
            "<tr><td width=100%>something</td>" +
            "<td width=1><img src=\"file:/a.jpg\" width=1 height=1></td></tr>" +
            "</table>";

        JFrame f = new JFrame();
        jep = new JEditorPane();
        jep.setEditorKit(new HTMLEditorKit());
        jep.setEditable(false);

        jep.setText(text);

        f.getContentPane().add(jep);
        f.setSize(500,500);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    public static void main(String args[]) throws Exception {
        Robot robot = new Robot();
        bug4690812 test = new bug4690812();
        try {
            SwingUtilities.invokeAndWait(() -> test.init());
            robot.waitForIdle();
            robot.delay(1000);
            Shape r = jep.getBounds();
            View v = jep.getUI().getRootView(jep);
            int tableHeight = 0;
            while (!(v instanceof javax.swing.text.html.ParagraphView)) {
                int n = v.getViewCount();
                Shape sh = v.getChildAllocation(n - 1, r);
                v = v.getView(n - 1);
                if (sh != null) {
                    r = sh;
                }
            }
            // left column in the second table row should have width == 1
            passed = (r.getBounds().width == 1) ? true : false;
            if (!passed) {
                throw new RuntimeException("Test failed.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }
}
