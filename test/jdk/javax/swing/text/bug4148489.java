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
 * @bug 4148489
 * @summary Text gets deleted with negative values for setFirstLineIndent.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4148489
 */

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.Style;

public class bug4148489 {

    static StyleContext sc;
    static DefaultStyledDocument doc;

    private static final String INSTRUCTIONS = """
        Put the cursor at the beginning of the first text line and move the
        cursor to the right using arrow key.
        If the text is not corrupted then click Pass
        If the text disappear while cursor moves click Fail.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Text traversal Instructions")
                .instructions(INSTRUCTIONS)
                .rows(5)
                .columns(35)
                .testUI(bug4148489::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Error loading L&F: " + e);
        }
        JPanel testPanel = new JPanel();
        testPanel.setLayout(new BorderLayout());
        sc = new StyleContext();
        doc = new DefaultStyledDocument(sc);

        setParagraph();
        JTextComponent editor = new JTextPane(doc);
        JScrollPane scroller = new JScrollPane();
        scroller.getViewport().add(editor);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add("Center", scroller);
        testPanel.add("Center", panel);
        JFrame frame = new JFrame("Styled Document");
        frame.add(testPanel);
        frame.pack();
        return frame;
    }

    static void setParagraph() {
        Style sty = sc.addStyle("normal", sc.getStyle(StyleContext.DEFAULT_STYLE));
        //here sets the negative value for setFirstLineIndent
        StyleConstants.setFirstLineIndent(sty, -50);
        StyleConstants.setLeftIndent(sty, 50);
        String data = "Here I wrote some text for test. You can ignore this text because of it's a senseless text.";
        try {
            Style s = null;
            doc.insertString(doc.getLength(), data, s);
            Style ls = sc.getStyle("normal");
            doc.setLogicalStyle(doc.getLength() - 1, ls);
            doc.insertString(doc.getLength(), "\n", null);
        } catch (BadLocationException e) {
            throw new RuntimeException("BadLocationException occures while calls insertString()...", e);
        }
    }
}
