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

/* @test
 * @bug 4102068
 * @summary Tests HTML editor JEditorPane change mouse icon over the link
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4102068
 */

import java.awt.Cursor;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.text.html.HTMLEditorKit;

public class bug4102068 {

    static final String INSTRUCTIONS = """
        The test window contains an HTML frame containing a string with one hyperlink.
        Move the mouse pointer over this hyperlink.
        If the pointer over the hyperlink became a HAND cursor then the test PASSES,
        otherwise the test FAILS.
    """;

    static JFrame createUI() {

        JFrame frame = new JFrame("bug4102068");
        JTextPane ep = new JTextPane();
        ep.setContentType("text/html");
        HTMLEditorKit ek = new HTMLEditorKit();
        ep.setEditorKit(ek);
        ep.setText("<html><body>Here is a <a href=''>HyperLink Cursor Test</a></body></html>");
        ek.setDefaultCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        Cursor ct = ek.getDefaultCursor();
        ek.setLinkCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Cursor cl = ek.getLinkCursor();
        if (ct.getType() != Cursor.DEFAULT_CURSOR || cl.getType() != Cursor.HAND_CURSOR) {
             throw new RuntimeException("Error with cursor settings...");
        }
        ep.setEditable(false);

        frame.add(ep);
        frame.setSize(300, 300);
        return frame;
    }

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(50)
            .testUI(bug4102068::createUI)
            .build()
            .awaitAndCheck();
    }
}
