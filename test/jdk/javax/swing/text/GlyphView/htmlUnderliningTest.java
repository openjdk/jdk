/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;

/* @test
 * @bug 4984669 8002148
 * @summary Tests HTML underlining
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual htmlUnderliningTest
 */

public class htmlUnderliningTest {
    public static void main(String[] args) throws Exception {
        String testInstructions = """
                The four lines printed in a bold typeface should all be underlined.
                It is a bug if any of these lines is underlined only partially.
                The very first line should not be underlined at all.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(testInstructions)
                .rows(4)
                .columns(35)
                .splitUI(htmlUnderliningTest::initializeTest)
                .build()
                .awaitAndCheck();
    }

    public static JPanel initializeTest() {
        JPanel panel = new JPanel();
        JEditorPane pane = new JEditorPane();
        panel.add(new JScrollPane(pane));
        pane.setEditorKit(new StyledEditorKit());

        try {
            pane.getDocument().insertString(0, "12   \n", null);
            MutableAttributeSet attrs = new SimpleAttributeSet();

            StyleConstants.setFontSize(attrs, 36);
            StyleConstants.setBold(attrs, true);
            StyleConstants.setUnderline(attrs, true);
            pane.getDocument().insertString(6, "aa\n", attrs);
            pane.getDocument().insertString(9, "bbb\n", attrs);
            pane.getDocument().insertString(13, "cccc\n", attrs);
            pane.getDocument().insertString(18, "ddddd\n", attrs);
        } catch (Exception e) {
            throw new Error("Failed: Unexpected Exception", e);
        }
        return panel;
    }
}
