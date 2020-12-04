/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8231286
 * @key headful
 * @summary  Verifies if HTML font size too large with high-DPI scaling and W3C_UNIT_LENGTHS
 * @run main/othervm -Dsun.java2d.uiScale=1.0 HtmlFontSizeTest
 */
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Rectangle;

import javax.swing.JFrame;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

public class HtmlFontSizeTest {
    static Rectangle framesize1, framesize2;

    private static void test(boolean w3ccheck) {
        JFrame frame = null;
        JLabel label = null;
        try {
            frame = new JFrame();
            frame.setLayout(new BorderLayout());
            label = new JLabel("This is 16 pt.");
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));

            JEditorPane htmlPane = new JEditorPane();
            htmlPane.setEditable(false);

            if (w3ccheck) {
                htmlPane.putClientProperty(JEditorPane.W3C_LENGTH_UNITS, Boolean.TRUE);
            }

            HTMLEditorKit kit = new HTMLEditorKit();
            htmlPane.setEditorKit(kit);

            StyleSheet styleSheet = kit.getStyleSheet();
            styleSheet.addRule("body { font-family: SansSerif; font-size: 16pt; }");

            String htmlString = "<html>\n"
                                + "<body>\n"
                                + "<p>This should be 16 pt.</p>\n"
                                + "</body>\n"
                                + "</html>";

            Document doc = kit.createDefaultDocument();
            htmlPane.setDocument(doc);
            htmlPane.setText(htmlString);

            frame.add(label, BorderLayout.NORTH);
            frame.add(htmlPane, BorderLayout.CENTER);
            frame.setUndecorated(true);
            frame.pack();
            frame.setVisible(true);
            frame.setLocationRelativeTo(null);

            if (w3ccheck) {
                framesize1 = frame.getBounds();
            } else {
                framesize2 = frame.getBounds();
            }
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            test(false);
            test(true);
        });
        System.out.println("frame height with W3C:" + framesize1);
        System.out.println("frame height without W3C:" + framesize2);

        float ratio = (float)framesize1.width/(float)framesize2.width;
        System.out.println("framesize1.width/framesize2.width " + ratio);

        String str = String.format("%.1g%n",ratio);
        if (str.compareTo(String.format("%.1g%n",1.3f)) != 0) {
            throw new RuntimeException("HTML font size too large with high-DPI scaling and W3C_UNIT_LENGTHS");
        }
    }
}
