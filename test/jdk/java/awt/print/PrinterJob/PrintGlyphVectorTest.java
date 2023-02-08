/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8029204
 * @library ../../regtesthelpers
 * @build PassFailJFrame
 * @summary Tests GlyphVector is printed in the correct location
 * @run main/manual PrintGlyphVectorTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

public class PrintGlyphVectorTest extends Component implements Printable {
    private static Frame f;

    private static final String INSTRUCTIONS = """
            Note: You must have a printer installed for this test.
            If printer is not available, the test passes automatically.

            Press the PRINT button on the 'Test PrintGlyphVector' frame
            and press OK/print button on the print dialog.

            Retrieve the output and compare the printed and on-screen
            text to confirm that in both cases the text is aligned and
            the boxes are around the text, not offset from the text.
            """;

    public void drawGVs(Graphics g) {

        String testString = "0123456789abcdefghijklm";
        Graphics2D g2d = (Graphics2D)g;
        g2d.setColor(Color.black);
        Font font = new Font("SansSerif", Font.PLAIN, 30);
        FontRenderContext frc = g2d.getFontRenderContext();
        GlyphVector v = font.createGlyphVector(frc, testString);

        float x = 50f;
        float y = 50f;

        g2d.drawGlyphVector(v, x, y);
        Rectangle2D r = v.getVisualBounds();
        r.setRect(r.getX()+x, r.getY()+y, r.getWidth(), r.getHeight());
        g2d.draw(r);

        Point2D p; // .Float p = new Point2D.Float();
        for (int i = 0; i < v.getNumGlyphs(); i++) {
            p = v.getGlyphPosition(i);
            p.setLocation(p.getX()+50, p.getY());
            v.setGlyphPosition(i, p);
        }

        x = 0;
        y+= 50;

        g2d.drawGlyphVector(v, x, y);
        r = v.getVisualBounds();
        r.setRect(r.getX()+x, r.getY()+y, r.getWidth(), r.getHeight());
        g2d.draw(r);
    }

    public void paint(Graphics g) {
        g.setColor(Color.white);
        g.fillRect(0, 0, getSize().width, getSize().height);
        drawGVs(g);
    }

    public Dimension getPreferredSize() {
        return new Dimension(600,200);
    }

    public int print(Graphics g, PageFormat pf, int pageIndex) {

        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D)g;
        g2d.translate(pf.getImageableX(), pf.getImageableY());
        drawGVs(g2d);

        return Printable.PAGE_EXISTS;
    }

    private static void createTestUI() {
        PrinterJob pj = PrinterJob.getPrinterJob();
        if (pj.getPrintService() == null) {
            System.out.println("Printer not configured or available."
                    + " Test cannot continue.");
            PassFailJFrame.forcePass();
        }

        f = new Frame("Test PrintGlyphVector");
        PrintGlyphVectorTest pvt = new PrintGlyphVectorTest();
        f.add(pvt, BorderLayout.CENTER);

        Button printButton = new Button("PRINT");
        printButton.addActionListener((e) -> {
            pj.setPrintable(new PrintGlyphVectorTest());
            if (pj.printDialog()) {
                try {
                    pj.print();
                } catch (PrinterException ex) {
                    throw new RuntimeException(ex.getMessage());
                }
            } else {
                throw new RuntimeException("Test failed : "
                        + "User selected 'Cancel' button on the print dialog");
            }
        });

        f.add(printButton, BorderLayout.SOUTH);
        f.pack();

        // add the test frame to dispose
        PassFailJFrame.addTestWindow(f);

        // Arrange the test instruction frame and test frame side by side
        PassFailJFrame.positionTestWindow(f, PassFailJFrame.Position.HORIZONTAL);
        f.setVisible(true);
    }

    public static void main(String[] arg) throws Exception {
        PassFailJFrame passFailJFrame = new PassFailJFrame(INSTRUCTIONS);
        createTestUI();
        passFailJFrame.awaitAndCheck();
    }
}

