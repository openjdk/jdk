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
 * @bug 8314731
 * @summary FormView doesn't support the alt attribute
 * @library /java/swing/text/html
*/

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

public class bug8314731 {

    private static boolean testFailed = true;

    public static void main(String[] args) throws Exception {
        new bug8314731();
        if (testFailed) {
            System.out.println("ok");
        } else {
            throw new RuntimeException("FormView doesn't support the alt attribute, see JDK-8314731.");
        }
    }

    public bug8314731() throws Exception {
        JEditorPane jEditorPane = new JEditorPane();
        jEditorPane.setEditable(false);
        JFrame jf = new JFrame("CSS named colors Test");

        JScrollPane scrollPane = new JScrollPane(jEditorPane);
        HTMLEditorKit kit = new HTMLEditorKit();
        jEditorPane.setEditorKit(kit);
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("""
                body {
                    color: #000;
                    font-family:times;
                    margin: 4px;
                }
                """);
        String htmlString = """
                <html>
                    <body>
                        <input type=image
                               name=point
                               src="file:logo.jpeg"
                               alt="Logo">
                    </body>
                </html>
                """;
        Document doc = kit.createDefaultDocument();
        jEditorPane.setDocument(doc);
        jEditorPane.setText(htmlString);

        jf.getContentPane().add(scrollPane, BorderLayout.CENTER);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setSize(new Dimension(400, 200));
        jf.setLocationRelativeTo(null);


        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                jf.setVisible(true);
            }
        });

        testFailed = ContainsAlt(jEditorPane);


        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                jf.dispose();
            }
        });

    }

    private boolean ContainsAlt(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton butt) {
                String text = butt.getText();
                if (text.equals("Logo")) {
                    return true;
                }
            } else if (c instanceof Container cont) {
                if (ContainsAlt(cont)) {
                    return true;
                }
            }
        }
        return false;
    }
}
