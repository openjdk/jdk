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
 * @bug 4813831
 * @summary Verifies contents of table cells in HTML in JEditorPane wraps correctly
 * @key headful
 * @run main bug4813831
*/

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.text.View;
import javax.swing.text.ParagraphView;
import javax.swing.text.html.HTMLEditorKit;

import java.awt.Robot;
import java.awt.Shape;

public class bug4813831 {

    private static boolean passed = false;
    private boolean finished = false;

    private static JEditorPane jep;
    private static JFrame f;

    public void init() {

        String text =
            "<html><body>" +
            "<table border><tr>" +
            "<td align=center>XXXXXXXXXXXXXX<BR>X<BR>X</td>" +
            "</tr></table>" +
            "</body></html>";

        f = new JFrame();
        jep = new JEditorPane();
        jep.setEditorKit(new HTMLEditorKit());
        jep.setEditable(false);

        jep.setText(text);

        f.getContentPane().add(jep);
        f.setSize(20,500);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }


    public static void main(String args[]) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        bug4813831 test = new bug4813831();
        try {
            SwingUtilities.invokeAndWait(() -> test.init());
            robot.waitForIdle();
            robot.delay(1000);
            Shape r = jep.getBounds();
            View v = jep.getUI().getRootView(jep);
            do {
                int n = v.getViewCount();
                Shape sh = v.getChildAllocation(n - 1, r);
                if (sh != null) {
                    r = sh;
                }
                v = v.getView(n - 1);
            } while (!(v instanceof ParagraphView));

            int n = v.getViewCount();
            // there should be 3 lines or more (if the first long line was wrapped) in a cell
            passed = n >= 3;

            if (passed) {
                Shape sh = v.getChildAllocation(n - 2, r);
                int x1 = sh.getBounds().x;
                sh = v.getChildAllocation(n - 1, r);
                int x2 = sh.getBounds().x;
                System.out.println("x1: " + x1 + " x2: " + x2);
                // lines should be equally aligned
                passed = (x1 == x2);
            }
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
