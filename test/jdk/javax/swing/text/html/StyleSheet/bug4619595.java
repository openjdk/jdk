/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4619595
 * @summary Tests that embedded list items do not inherit the 'value'
 *          property from their parent list item
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4619595
 */

import javax.swing.JEditorPane;
import javax.swing.JFrame;

public class bug4619595 {

    static final String INSTRUCTIONS = """
        The test window contains numbered lists.
        Look at the three indented/embedded list items (the ones that are bold).
        If they are not numbered 1, 2 and 3, then press FAIL.

        Below the lists there should also be a line saying: "The quick brown fox".
        If you don't see this, press FAIL.

        If all is as expected, PASS the test.
    """;

    final static String HTML = "<html><body>" +
        "<ol>  <li>Let's start  <li value=4>Look @ inner list" +
        "    <ol> <li><b>Inner list starts</b>  <li><b>Second inner item</b>" +
        "         <li><b>Inner list ends</b>  </ol>" +
        "    <li>That's all </ol>" +
        " <p style='background-color:'>The quick brown fox</p>" +
        "</body> </html>";

    static JFrame createUI() {

        JFrame frame = new JFrame("bug4619595");
        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setText(HTML);
        frame.add(pane);
        frame.setSize(400, 400);
        return frame;
    }

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(40)
            .testUI(bug4619595::createUI)
            .build()
            .awaitAndCheck();
    }
}
