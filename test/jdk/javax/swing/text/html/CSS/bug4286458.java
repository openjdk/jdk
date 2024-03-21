/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4286458
 * @summary  Tests if cellpadding in tables is non-negative number
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4286458
*/

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.text.html.HTMLEditorKit;

public class bug4286458 {

    private static String INSTRUCTIONS = """
        If you can clearly read the line of text in the appeared frame
        press PASS. Otherwise test fails.""";

    public static void main(String[] args) throws Exception {
         PassFailJFrame.builder()
                .title("CSS tag Instructions")
                .instructions(INSTRUCTIONS)
                .rows(5)
                .columns(30)
                .testUI(bug4286458::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {

        String text =
            "<html><body><table border=\"1\" cellpadding=\"-10\">" +
            "<tr><td>This line should be clearly readable</td></tr>" +
            "</table></body></html>";

        JFrame f = new JFrame("bug4286458");
        JEditorPane jep = new JEditorPane("text/html", text);
        jep.setEditable(false);

        f.add(jep);
        f.pack();
        return f;
    }
}
