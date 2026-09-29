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
 * @bug 8314731
 * @key headful
 * @summary FormView doesn't support the alt attribute
 */

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

public class bug8314731 {

    private static JFrame frame;
    private static JEditorPane editorPane;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(bug8314731::createAndSetVisibleUI);
            SwingUtilities.invokeAndWait(() -> {
                if (!containsAlt(editorPane)) {
                    throw new RuntimeException("FormView doesn't support the alt attribute, see JDK-8314731.");
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndSetVisibleUI() {
        editorPane = new JEditorPane();
        editorPane.setEditable(false);
        frame = new JFrame("alt attribute test in HTML image type input");

        JScrollPane scrollPane = new JScrollPane(editorPane);
        HTMLEditorKit kit = new HTMLEditorKit();
        editorPane.setEditorKit(kit);
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("""
                body {
                    color: #000;
                    font-family: times;
                    margin: 4px;
                }
                """);
        String htmlString = """
                <html>
                    <body>
                        <input type=image
                               name=point
                               alt="Logo">
                    </body>
                </html>
                """;
        Document doc = kit.createDefaultDocument();
        editorPane.setDocument(doc);
        editorPane.setText(htmlString);

        frame.add(scrollPane, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(new Dimension(400, 200));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static boolean containsAlt(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton button) {
                return "Logo".equals(button.getText());
            } else if (c instanceof Container cont) {
                return containsAlt(cont);
            }
        }
        return false;
    }
}
