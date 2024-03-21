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
 * @bug 4803145
 * @summary  Tests if bullets for HTML <ul> are on the correct side for Arabic and Hebrew in JEditorPane
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4803145
*/

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.text.html.HTMLEditorKit;

public class bug4803145 {

    private static final String INSTRUCTIONS = """
        A JEditorPane with some html list in Hebrew appears.
        The bullets should be on the left side of the list items.
        Press the "switch text orientation" button.
        After the text relayouts:

            - If the bullets are to the right of the list items then test PASSED.

            - If the bullets remained on the left side then test FAILED.""";

    public static void main(String[] args) throws Exception {
         PassFailJFrame.builder()
                .title("JEditorPane Instructions")
                .instructions(INSTRUCTIONS)
                .rows(10)
                .columns(30)
                .testUI(bug4803145::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {

        String text =
           "<ul>" +
             "<li>&#1502;&#1489;&#1493;&#1488;" +
             "<li>&#1488;&#1495;&#1505;&#1493;&#1503;" +
             "<li>(new code) &#1492;&#1511;&#1493;&#1491; &#1492;&#1497;&#1513;&#1503; (Old Code)" +
            "</ul>";

        JFrame f = new JFrame("bug4803145");
        JEditorPane jep = new JEditorPane();
        jep.setEditorKit(new HTMLEditorKit());
        jep.setEditable(false);

        jep.setText(text);

        f.setSize(500, 500);
        f.add(jep);

        JButton switchButton = new JButton("switch text orientation");
        switchButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean isLeftToRight = jep.getComponentOrientation().isLeftToRight();
                jep.setComponentOrientation(isLeftToRight ? ComponentOrientation.RIGHT_TO_LEFT :
                                                            ComponentOrientation.LEFT_TO_RIGHT);
            }
        });
        f.add(switchButton, BorderLayout.SOUTH);
        f.pack();
        return f;
    }

}
