/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6539700
 * @summary test that the long space-less lines are correctly soft-wrapped
 * @author Sergey Groznyh
 * @run main bug6539700
 */

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.text.ParagraphView;
import javax.swing.text.View;

public class bug6539700 {
    static JFrame f;
    static JEditorPane ep;
    static String text = "AAAAAAAA<b>AAAAAA</b>AAAAAAAA<b>AAAAAAAAA</b>" +
                         "AA<b>AAA</b>AAAAAAAAA";
    static int size = 100;
    static Class rowClass = null;

    static void createContentPane() {
        ep = new JEditorPane();
        ep.setContentType("text/html");
        ep.setEditable(false);
        ep.setText(text);
        f = new JFrame();
        f.setSize(size, 2 * size);
        f.add(ep);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
    }

    static void checkRows(View v, boolean last) {
        int width = (int) v.getPreferredSpan(View.X_AXIS);

        if (v.getClass() == rowClass) {
            // Row width shouldn't exceed the container width
            if (width > size) {
                throw new RuntimeException("too long row: " + width);
            }

            // Row shouldn't be too short (except for the last one)
            if (!last) {
                if (width < size * 2 / 3) {
                    throw new RuntimeException("too short row: " + width);
                }
            }
        }

        int n = v.getViewCount();
        if (n > 0) {
            for (int i = 0; i < n; i++) {
                View c = v.getView(i);
                checkRows(c, i == n - 1);
            }
        }
    }

    public static void main(String[] argv) {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    createContentPane();
                }
            });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        Class[] pvchildren = ParagraphView.class.getDeclaredClasses();
        for (Class c : pvchildren) {
            if (c.getName().equals("javax.swing.text.ParagraphView$Row")) {
                rowClass = c;
                break;
            }
        }
        if (rowClass == null) {
            throw new RuntimeException("can't find ParagraphView.Row class");
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                checkRows(ep.getUI().getRootView(ep), true);
            }
        });

        System.out.println("OK");
    }
}
