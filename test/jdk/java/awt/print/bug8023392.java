/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8023392 8259232
 * @key printer
 * @modules java.desktop/sun.swing
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Swing text components printed with spaces between chars
 * @run main/manual bug8023392
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.font.TextAttribute;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.LineBorder;

import sun.swing.SwingUtilities2;

public class bug8023392 {
    private static final String INSTRUCTIONS =
            """
             A Frame containing several pairs of labels (a) and (b) is displayed.
             Labels of each pair look the same and are left-aligned (with spaces
             between chars).
             1. Hit the print button.
             2. Select any available printer (printing to file is also fine).
             3. Look at the printing result (paper, PDF, PS, etc.):
                 The (a) and (b) labels should look almost the same and the (a)
                 labels shouldn't appear as if they are stretched along X axis.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame
                .builder()
                .title("bug8023392 Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(bug8023392::init)
                .build()
                .awaitAndCheck();
    }

    public static JFrame init() {
        JFrame frame = new JFrame("Test Window");
        frame.setLayout(new BorderLayout());
        frame.add(new SimplePrint2(), BorderLayout.CENTER);
        frame.pack();
        return frame;
    }

    public static class SimplePrint2 extends JPanel
            implements ActionListener, Printable {
        JLabel label1;
        JLabel label2;
        JButton printButton;

        public SimplePrint2() {
            setLayout(new BorderLayout());
            label1 = new JLabel("2a) a b c d e" +
                    "                         ");
            label2 = new JLabel("2b) a b c d e");

            Box p1 = new Box(BoxLayout.Y_AXIS);
            p1.add(label1);
            p1.add(label2);
            p1.add(new JLabel("wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww") {
                String s = "3a) a b c d e                                     ";
                @Override
                protected void paintComponent(Graphics g) {
                    SwingUtilities2.drawChars(this, g, s.toCharArray(),
                            0, s.length(), 0, 15);
                }
            });
            p1.add(new JLabel("wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww") {
                String s = "3b) a b c d e";
                @Override
                protected void paintComponent(Graphics g) {
                    SwingUtilities2.drawChars(this, g, s.toCharArray(),
                            0, s.length(), 0, 15);
                }
            });
            p1.add(new JLabel("wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww") {
                String s = "4a) a b c d e                                     ";
                AttributedCharacterIterator it;
                {
                    AttributedString as = new AttributedString(s);
                    as.addAttribute(TextAttribute.FONT, getFont());
                    as.addAttribute(TextAttribute.FOREGROUND, Color.RED, 3, 8);
                    it = as.getIterator();
                }
                @Override
                protected void paintComponent(Graphics g) {
                    SwingUtilities2.drawString(this, g, it, 0, 15);
                }
            });

            p1.add(new JLabel("wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww") {
                String s = "4b) a b c d e";
                AttributedCharacterIterator it;
                {
                    AttributedString as = new AttributedString(s);
                    as.addAttribute(TextAttribute.FONT, getFont());
                    as.addAttribute(TextAttribute.FOREGROUND, Color.RED, 3, 8);
                    it = as.getIterator();
                }
                @Override
                protected void paintComponent(Graphics g) {
                    SwingUtilities2.drawString(this, g, it, 0, 15);
                }
            });

            JPanel p2 = new JPanel();
            printButton = new JButton("Print");
            printButton.addActionListener(this);
            p2.add(printButton);

            Container c = this;
            c.add(p1, BorderLayout.CENTER);
            c.add(p2, BorderLayout.SOUTH);

            String[] data = {
                    "1a) \u30aa\u30f3\u30e9\u30a4\u30f3\u6d88\u8fbc" +
                    "                                              ",
                    "1b) \u30aa\u30f3\u30e9\u30a4\u30f3\u6d88\u8fbc"
            };
            JList l0 = new JList(data);
            l0.setVisibleRowCount(l0.getModel().getSize());
            JScrollPane jsp = new JScrollPane(l0);
            l0.setBorder(new LineBorder(Color.GRAY));
            c.add(jsp, BorderLayout.NORTH);

            for (Component comp : new Component[]{label1, label2, printButton}) {
                comp.setFont(new Font("Monospaced", 0, 16));
            }
        }

        public void actionPerformed(ActionEvent e) {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintable(this);
            if (job.printDialog()) {
                try {
                    job.print();
                } catch (PrinterException ex) {
                    ex.printStackTrace();
                    String msg = "PrinterException: " + ex.getMessage();
                    PassFailJFrame.forceFail(msg);
                }
            }
        }

        public int print(Graphics graphics,
                         PageFormat pageFormat,
                         int pageIndex)
                throws PrinterException {
            if (pageIndex >= 1) {
                return Printable.NO_SUCH_PAGE;
            }
            double imgX = pageFormat.getImageableX();
            double imgY = pageFormat.getImageableY();
            ((Graphics2D)graphics).translate(imgX, imgY);
            this.paint(graphics);
            return Printable.PAGE_EXISTS;
        }
    }
}
