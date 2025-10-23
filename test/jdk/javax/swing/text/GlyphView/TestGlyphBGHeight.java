/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8017266
 * @summary Verifies if Background is painted taller than needed for styled text.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestGlyphBGHeight
 */

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class TestGlyphBGHeight {

    final static String INSTRUCTIONS = """
        A StyledDocument with a text and yellow background will be shown.
        If the yellow background is way taller than the text,
        press FAIL else press PASS.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TestGlyphBGHeight Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(30)
                .testUI(TestGlyphBGHeight::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JFrame frame = new JFrame("TestGlyphBGHeight");
        frame.setSize(100, 100);
        frame.getContentPane().setLayout(new BorderLayout());

        final JTextPane comp = new JTextPane();
        final StyledDocument doc = comp.getStyledDocument();

        Style style = comp.addStyle("superscript", null);
        StyleConstants.setSuperscript(style, true);
        StyleConstants.setFontSize(style, 32);
        StyleConstants.setBackground(style, Color.YELLOW);
        try {
            doc.insertString(doc.getLength(), "hello", style);
        } catch(Exception e) {}

        comp.setDocument(doc);

        frame.getContentPane().add(comp, BorderLayout.CENTER);
        return frame;
    }
}
