/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Font;
import java.awt.GraphicsEnvironment;

import javax.swing.JFrame;
import javax.swing.JTextArea;

/*
 * @test
 * @bug 5014727
 * @summary Display Devanagari text and make sure the character
 *          that appears after the nukta (dot) isn't duplicated.
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @run main/manual TestDevanagari
 */

public class TestDevanagari {

    private static final String text = """
                Ra Nukta Ra
                \u0930\u093c\u0930""";
    private static final Font font = getPhysicalFontForText(text, Font.PLAIN, 20);

    public static void main(String[] args) throws Exception {
        if (font == null) {
            throw new jtreg.SkippedException("No Devanagari font found. Test Skipped.");
        }

        final String INSTRUCTIONS = """
                You should see two Devanagari Letters 'Ra':
                The first with Nukta sign (dot under it), the second without.
                The second character (after the Nukta sign) shouldn't be visible twice

                Pass the test if this is true.
                """;

        PassFailJFrame.builder()
                .title("TestDevanagari Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(TestDevanagari::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JFrame frame = new JFrame("TestDevanagari UI");
        JTextArea textArea = new JTextArea();
        textArea.setFont(font);
        textArea.setText(text);

        frame.add(textArea);
        frame.setSize(300, 200);
        return frame;
    }

    private static Font getPhysicalFontForText(String text, int style, int size) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] names = ge.getAvailableFontFamilyNames();

        for (String n : names) {
            switch (n.toLowerCase()) {
                case "dialog":
                case "dialoginput":
                case "serif":
                case "sansserif":
                case "monospaced":
                     break;
                default:
                    Font f = new Font(n, style, size);
                    if (f.canDisplayUpTo(text) == -1) {
                        return f;
                    }
             }
        }
        return null;
    }
}
