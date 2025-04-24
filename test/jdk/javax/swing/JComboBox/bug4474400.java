/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4474400
 * @summary Tests JTextArea wrapping with font change
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4474400
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class bug4474400 {
    private static final String INSTRUCTIONS = """
            Press the "Change Font" button. The two lines of text should be
            properly drawn using the larger font, there should be empty line
            between them. If display is garbled, test fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4474400 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .splitUIRight(bug4474400::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JComponent createTestUI() {
        Font smallFont = new Font("SansSerif", Font.PLAIN, 12);
        Font largeFont = new Font("SansSerif", Font.PLAIN, 36);

        JTextArea textArea = new JTextArea("This is the first line\n\nThis is the third line");
        textArea.setFont(smallFont);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        JButton b = new JButton("Change Font");
        b.addActionListener((e) -> textArea.setFont(largeFont));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(200, 300));
        panel.add(textArea, BorderLayout.CENTER);
        panel.add(b, BorderLayout.SOUTH);

        return panel;
    }
}
