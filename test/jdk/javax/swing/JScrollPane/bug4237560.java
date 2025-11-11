/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4237560
 * @summary Tests that JScrollPane do not distort TAB order in an HTML page
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4237560
 */

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;

public class bug4237560 {
    static final String INSTRUCTIONS = """
        A JEditorPane contains 10 input fields and is inserted into
        JScrollPane. Click text field #8 so that it is selected. Press
        TAB three times (even if text field #9 and #10 are not visible in
        the ScrollPane). If this gives focus to the first text field (#1)
        the test PASSES, else it FAILS.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4237560 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4237560::createUI)
                .build()
                .awaitAndCheck();
    }

    private static final String TEXT = "<html><body><form method=\"POST\"><table>\n" +
            "<tr><td><input type=\"text\" value=\"1\" size=\"20\"></td></tr>\n" +
            "<tr><td><input type=\"text\" value=\"2\" size=\"20\"></td></tr>\n" +
            "<tr><td><input type=\"text\" value=\"3\" size=\"20\"></td></tr>\n" +
            "<tr><td><input type=\"text\" value=\"4\" size=\"20\"></td></tr>\n" +
            "<tr><td><input type=\"text\" value=\"5\" size=\"20\"></td></tr>\n" +
            "<tr><td><input type=\"text\" value=\"6\" size=\"20\"></td></tr>\n" +
            "<tr><td><input type=\"text\" value=\"7\" size=\"20\"></td></tr>\n" +
            "<tr><td><input type=\"text\" value=\"8\" size=\"20\"></td></tr>\n" +
            "<tr><td><input type=\"text\" value=\"9\" size=\"20\"></td></tr>\n" +
            "<tr><td><input type=\"text\" value=\"10\" size=\"20\"></td></tr>\n" +
            "</table></form></body></html>";

    private static JFrame createUI() {
        JFrame frame = new JFrame("JScrollPane HTML TAB Test");
        JEditorPane viewer = new JEditorPane("text/html", TEXT);
        viewer.setEditable(false);
        frame.add(new JScrollPane(viewer));
        frame.setSize(300, 300);
        return frame;
    }
}
