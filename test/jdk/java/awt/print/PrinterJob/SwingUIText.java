/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6488219 6560738 7158350 8017469
 * @key printer
 * @summary Test that text printed in Swing UI measures and looks OK.
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @run main/manual SwingUIText
 */

import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import jtreg.SkippedException;

public class SwingUIText implements Printable {
    private static JFrame frame;
    private static final String INSTRUCTIONS = """
            This test checks that when a Swing UI is printed,
            the text in each component aligns with the component’s length as seen on-screen.
            It also ensures the text spacing is reasonably even, though this is subjective.
            The comparison should be made with JDK 1.5 GA or JDK 1.6 GA.

            Steps:
            1. Press the "Print" or "OK" button on the Print dialog.
                This will print the content of the "Swing UI Text Printing Test" JFrame.
            2. Compare the printout with the content of the JFrame.
            3. If they match, press Pass; otherwise, press Fail.
            """;

    public static void main(String args[])  throws Exception {
        PrinterJob job = PrinterJob.getPrinterJob();
        if (job.getPrintService() == null) {
            throw new SkippedException("Printer not configured or available.");
        }

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(SwingUIText::createTestUI)
                .build();

        job.setPrintable(new SwingUIText());
        if (job.printDialog()) {
            job.print();
        }

        passFailJFrame.awaitAndCheck();
    }

    public static JFrame createTestUI() {
        frame = new JFrame();
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(4, 1));

        String text = "marvelous suspicious solving";
        displayText(panel, text);

        String itext = "\u0641\u0642\u0643 \u0644\u0627\u064b";
        itext = itext+itext+itext+itext+itext+itext+itext;
        displayText(panel, itext);

        String itext2 = "\u0641"+text;
        displayText(panel, itext2);

        JEditorPane editor = new JEditorPane();
        editor.setContentType("text/html");
        String CELL = "<TD align=\"center\"><font style=\"font-size: 18;\">Text</font></TD>";
        String TABLE_BEGIN = "<TABLE BORDER=1 cellpadding=1 cellspacing=0 width=100%>";
        String TABLE_END = "</TABLE>";
        StringBuffer buffer = new StringBuffer();
        buffer.append("<html><body>").append(TABLE_BEGIN);
        for (int j = 0; j < 15; j++) {
            buffer.append(CELL);
        }
        buffer.append("</tr>");
        buffer.append(TABLE_END).append("</body></html>");
        editor.setText(buffer.toString());

        panel.add(editor);

        frame = new JFrame("Swing UI Text Printing Test");
        frame.getContentPane().add(panel);
        frame.pack();
        return frame;
    }

    static void displayText(JPanel p, String text) {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 1));
        JPanel row = new JPanel();
        Font font = new Font("Dialog", Font.PLAIN, 12);

        JLabel label = new JLabel(text);
        label.setFont(font);
        row.add(label);

        JButton button = new JButton("Print " + text);
        button.setMnemonic('P');
        button.setFont(font);
        row.add(button);

        panel.add(row);

        row = new JPanel();
        JTextField textField = new JTextField(text);
        row.add(textField);

        JTextArea textArea = new JTextArea();
        textArea.setText(text);
        row.add(textArea);

        panel.add(row);
        p.add(panel);
    }

    public int print(Graphics g, PageFormat pf, int pageIndex) {
        if (pageIndex >= 1) {
            return Printable.NO_SUCH_PAGE;
        }

        g.translate((int)pf.getImageableX(), (int)pf.getImageableY());
        frame.printAll(g);
        return Printable.PAGE_EXISTS;
    }

}
