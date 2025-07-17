/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4270889 4271058 4285098
 * @summary Tests that <table border>, <table align> and <td width> tags work
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4271058
*/

import javax.swing.JEditorPane;
import javax.swing.JFrame;

public class bug4271058 {

    private static String INSTRUCTIONS = """
        What should be seen is three 2x2 tables.

        The first table should have borders and lines distinguishing
        table cells. If they are not shown, test fails (bug 4271058).

        In the second table, the first (left) column should be about
        four times as wide as the second (right) column.
        If this is not so, test fails (bug 4270889).

        The third table should be right aligned, i.e. its right edge
        should be close to the right edge of the viewable area.
        Otherwise test fails (bug 4285098).
        """;

    public static void main(String[] args) throws Exception {
         PassFailJFrame.builder()
                .title("CSS html tag verification Instructions")
                .instructions(INSTRUCTIONS)
                .rows(15)
                .columns(30)
                .testUI(bug4271058::createTestUI)
                .screenCapture()
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        String htmlText =
            "<html><table border width=300 height=200>" +
            "<tr><th>col A</th><th>col B</th></tr>" +
            "<tr>" +
            "<td>aaaaaa</td>" +
            "<td>bbbbbbb</td>" +
            "</tr></table>" +
            "<table border width=300>" +
            "<tr><th>A</th><th>B</th></tr>" +
            "<tr>" +
            "<td width=\"80%\">a</td>" +
            "<td width=\"20%\">b</td>" +
            "</tr></table>" +
            "<table align=right border width=200>" +
            "<tr><th>col A</th><th>col B</th></tr>" +
            "<tr>" +
            "<td>aaaaaa</td>" +
            "<td>bbbbbbb</td>" +
            "</tr></table></html>";

        JEditorPane lbl = new JEditorPane("text/html", htmlText);
        JFrame frame = new JFrame("bug4271058");
        frame.add(lbl);
        frame.pack();
        return frame;
    }
}
