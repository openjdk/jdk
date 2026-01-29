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
 * @bug 4188841
 * @summary Tests a JTextPane wrapping issue
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4188841
*/

import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

public class bug4188841 {

    static final String INSTRUCTIONS = """
        The text pane contains the phrase "the quick brown fox jumps over the lazy dog",
        all the words are separated by tabs. When the test starts, the whole phrase should
        appear on one line. If it is wrapped along two or more lines, the test FAILS.

        Otherwise, place the text caret in the very end of the line (e.g. by clicking
        in the line and hitting End). Press Enter twice. The text should appear as one
        line at all times. If the text wraps when you press Enter, the test FAILS.
    """;

    static class NoWrapTextPane extends JTextPane {

        public boolean getScrollableTracksViewportWidth() {
           //should not allow text to be wrapped
           return false;
        }

        public void setSize(Dimension d) {
           // don't let the Textpane get sized smaller than its parent
           if (d.width < getParent().getSize().width) {
              super.setSize(getParent().getSize());
           }
           else {
              super.setSize(d);
           }
        }
    }

    static JFrame createUI() {

        JFrame frame = new JFrame("bug4188841");

        NoWrapTextPane nwp = new NoWrapTextPane();
        nwp.setText("the\tquick\tbrown\tfox\tjumps\tover\tthe\tlazy\tdog!");
        nwp.setCaretPosition(nwp.getText().length());

        JScrollPane scrollPane = new JScrollPane(nwp,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        frame.add(scrollPane);
        frame.setSize(400, 300);
        return frame;
    }

    public static void main(String args[]) throws Exception {
        PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(60)
            .testUI(bug4188841::createUI)
            .build()
            .awaitAndCheck();
    }
}
