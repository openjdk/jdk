/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, BELLSOFT. All rights reserved.
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

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.DialogTypeSelection;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.*;
import java.lang.Override;
import java.lang.reflect.InvocationTargetException;


/*
 * @test
 * @bug 8372952
 * @key printer
 * @requires (os.family == "mac")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary verifies printing margins .
 * @run main/manual MarginsTest
 */

public class MarginsTest {

    private static final float leftMargin = 0.15f;
    private static final float rightMargin = 0.5f;
    private static final float topMargin = 0.3f;
    private static final float bottomMargin = 0.9f;

    private static final String INSTRUCTIONS = """
        This test checks margins correctness.
        Portrait-oriented and landscape-oriented papers should be supported by the test printer,
        a PDF printer is acceptable.

        1. Read an instruction in the bottom of this window.
        2. Press "Print page ..." button.
        3. Check a printed rectangle along the borders and margins' size. The margins should
        equal the printed values, all rectangle's sides should be visible.
        4. Repeat steps 1-3 until instructions are visible.
        5. Press "Pass" button if all printed pages are correct, else press "Fail" button.
        """;

    private static final String[] WINDOW_INSTRUCTIONS = new String[]{
            """
            Choose portrait-oriented paper (Tabloid, ISO-A4, ...), printing orientation - Portrait.
            Check the actual size of margins, and the printed rectangle.
            """
            , """
            Choose portrait-oriented paper (Tabloid, ISO-A4, ...), printing orientation - Landscape.
            Check actual size of the margins, and a printed rectangle.
            """
            , """
            Choose landscape-oriented paper (Ledger, 80x40, ...), printing orientation - Portrait.
            Check actual size of the margins, and a printed rectangle.
            """
            , """
            Choose landscape-oriented paper (Ledger, 80x40, ...), printing orientation - Landscape.
            Check actual size of the margins, and a printed rectangle.
            """
    };


    public static void main(String[] args) throws InterruptedException, InvocationTargetException, PrinterException {
        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .title("Margin test Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .splitUIBottom(new PassFailJFrame.PanelCreator() {

                    int index = 0;

                    @Override
                    public JComponent createUIPanel() {
                        JPanel panel = new JPanel();
                        JTextArea textArea = new JTextArea();
                        textArea.setText(WINDOW_INSTRUCTIONS[index]);
                        textArea.setMargin(new Insets(5, 5, 5, 5));
                        JButton button = new JButton("Print page " + (index+1) + " of 4");
                        button.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                button.setEnabled(false);
                                PrinterJob printerJob = PrinterJob.getPrinterJob();
                                PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
                                attrs.add(DialogTypeSelection.COMMON);
                                printerJob.setPrintable(new PrintableRect());
                                if (printerJob.printDialog(attrs)) {
                                    try {
                                        MediaPrintableArea mpa = (MediaPrintableArea) attrs.get(MediaPrintableArea.class);
                                        if (mpa == null) {
                                            throw new RuntimeException("No margins found");
                                        }
                                        final int units = MediaPrintableArea.INCH;
                                        attrs.add(new MediaPrintableArea(mpa.getX(units) + leftMargin,
                                                        mpa.getY(units) + topMargin,
                                                        mpa.getWidth(units) - rightMargin,
                                                        mpa.getHeight(units) - bottomMargin,
                                                        units));
                                        printerJob.print(attrs);
                                    } catch (PrinterException ex) {
                                        throw new RuntimeException(ex);
                                    }
                                }
                                if (index >= 3) {
                                    panel.setVisible(false);
                                    return;
                                }
                                index++;
                                button.setText("Print page " + (index+1) + " of 4");
                                button.setEnabled(true);
                                textArea.setText(WINDOW_INSTRUCTIONS[index]);
                            }
                        });
                        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                        panel.add(textArea);
                        JPanel splitter = new JPanel();
                        splitter.getInsets().set(2, 0, 2, 0);
                        panel.add(splitter);
                        panel.add(button);
                        splitter = new JPanel();
                        splitter.getInsets().set(2, 0, 2, 0);
                        panel.add(splitter);
                        panel.setSize(180, 80);
                        return panel;
                    }
                })
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(40)
                .build();

        passFailJFrame.awaitAndCheck();
    }

    private static class PrintableRect implements Printable {

        public PrintableRect() {

        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
            if (pageIndex == 0) {
                final int left = (int) pageFormat.getImageableX()+1;
                final int top = (int) pageFormat.getImageableY()+1;
                final int width = (int) pageFormat.getImageableWidth()-1;
                final int height = (int) pageFormat.getImageableHeight()-1;
                final int right = (int) pageFormat.getWidth() - left - width;
                final int bottom = (int) pageFormat.getHeight() - top - height;

                final int fontHeight = graphics.getFontMetrics().getHeight();
                final double pointsPerMM = 72/25.4;

                graphics.drawRect(left, top, width, height);
                graphics.drawString(String.format("%.2fmm", left/pointsPerMM), left + 5, top + height / 2);
                graphics.drawString(String.format("%.2fmm", top/pointsPerMM), left + width / 2, top + fontHeight + 5);
                String rightStr = String.format("%.2fmm", right/pointsPerMM);
                graphics.drawString(rightStr, (left + width) - (graphics.getFontMetrics().stringWidth(rightStr) + 5),
                        (top + height)/2);
                String bottomStr = String.format("%.2fmm", bottom/pointsPerMM);
                graphics.drawString(bottomStr, left + (width/2 - (graphics.getFontMetrics().stringWidth(bottomStr))/2),
                        (top + height) - fontHeight - 5);

                return PAGE_EXISTS;
            }
            return NO_SUCH_PAGE;
        }
    }

}
