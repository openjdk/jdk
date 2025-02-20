/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6425068 7157659 8029204 8132890 8148334 8344637
 * @key printer
 * @summary Confirm that text prints where we expect to the length we expect.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PrintTextTest
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;

public class PrintTextTest {

    static final String INSTRUCTIONS = """
        This tests that printed text renders similarly to on-screen under a variety
        of APIs and graphics and font transforms.
        1. Print to your preferred printer.
        2. Collect the output.
        3. Refer to the onscreen buttons to cycle through the on-screen content.
        4. For each page, confirm that the printed content corresponds to the
           on-screen rendering for that *same* page. Some cases may look odd but
           its intentional. Verify it looks the same on screen and on the printer.
        Note that text does not scale linearly from screen to printer so some
        differences are normal and not a bug.
        The easiest way to spot real problems is to check that any underlines are
        the same length as the underlined text and that any rotations are the same
        in each case.
        Note that each on-screen page is printed in both portrait and landscape mode.
        So for example, Page 1/Portrait, and Page 1/Landscape when rotated to view
        properly, should both match Page 1 on screen.
        """;

    public static void main(String[] args) throws Exception {

        PrinterJob pjob = PrinterJob.getPrinterJob();
        PageFormat portrait = pjob.defaultPage();
        portrait.setOrientation(PageFormat.PORTRAIT);
        int preferredSize = (int) portrait.getImageableWidth();

        PageFormat landscape = pjob.defaultPage();
        landscape.setOrientation(PageFormat.LANDSCAPE);

        Book book = new Book();

        JTabbedPane pane = new JTabbedPane();

        int page = 1;
        Font font = new Font(Font.DIALOG, Font.PLAIN, 18);
        String name = "Page " + page++;
        PrintText pt = new PrintText(name, font, null, false, preferredSize);
        pane.addTab(name, pt);
        book.append(pt, portrait);
        book.append(pt, landscape);

        font = new Font(Font.DIALOG, Font.PLAIN, 18);
        name = "Page " + page++;
        pt = new PrintText(name, font, null, true, preferredSize);
        pane.addTab(name, pt);
        book.append(pt, portrait);
        book.append(pt, landscape);

        font = getPhysicalFont();
        name = "Page " + page++;
        pt = new PrintText(name, font, null, false, preferredSize);
        pane.addTab(name, pt);
        book.append(pt, portrait);
        book.append(pt, landscape);

        font = getPhysicalFont();
        AffineTransform rotTx = AffineTransform.getRotateInstance(0.15);
        rotTx.translate(60, 0);
        name = "Page " + page++;
        pt = new PrintText(name, font, rotTx, false, preferredSize);
        pane.addTab(name, pt);
        book.append(pt, portrait);
        book.append(pt, landscape);

        font = new Font(Font.DIALOG, Font.PLAIN, 18);
        AffineTransform scaleTx = AffineTransform.getScaleInstance(1.25, 1.25);
        name = "Page " + page++;
        pt = new PrintText(name, font, scaleTx, false, preferredSize);
        pane.addTab(name, pt);
        book.append(pt, portrait);
        book.append(pt, landscape);

        font = new Font(Font.DIALOG, Font.PLAIN, 18);
        scaleTx = AffineTransform.getScaleInstance(-1.25, 1.25);
        scaleTx.translate(-preferredSize / 1.25, 0);
        name = "Page " + page++;
        pt = new PrintText(name, font, scaleTx, false, preferredSize);
        pane.addTab(name, pt);
        book.append(pt, portrait);
        book.append(pt, landscape);

        font = new Font(Font.DIALOG, Font.PLAIN, 18);
        scaleTx = AffineTransform.getScaleInstance(1.25, -1.25);
        scaleTx.translate(0, -preferredSize / 1.25);
        name = "Page " + page++;
        pt = new PrintText(name, font, scaleTx, false, preferredSize);
        pane.addTab(name, pt);
        book.append(pt, portrait);
        book.append(pt, landscape);

        font = font.deriveFont(rotTx);
        name = "Page " + page++;
        pt = new PrintText(name, font, null, false, preferredSize);
        pane.addTab(name, pt);
        book.append(pt, portrait);
        book.append(pt, landscape);

        font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        name = "Page " + page++;
        pt = new PrintText(name, font, null, false, preferredSize);
        pane.addTab(name, pt);
        book.append(pt, portrait);
        book.append(pt, landscape);

        Font xfont = font.deriveFont(AffineTransform.getScaleInstance(1.5, 1));
        name = "Page " + page++;
        pt = new PrintText(name, xfont, null, false, preferredSize);
        pane.addTab(name, pt);
        book.append(pt, portrait);
        book.append(pt, landscape);

        Font yfont = font.deriveFont(AffineTransform.getScaleInstance(1, 1.5));
        name = "Page " + page++;
        pt = new PrintText(name, yfont, null, false, preferredSize);
        pane.addTab(name, pt);
        book.append(pt, portrait);
        book.append(pt, landscape);

        if (System.getProperty("os.name").startsWith("Windows")) {
            font = new Font("MS Gothic", Font.PLAIN, 12);
            name = "Page " + page++;
            pt = new PrintJapaneseText(name, font, null, true, preferredSize);
            pane.addTab(name, pt);
            book.append(pt, portrait);
            book.append(pt, landscape);

            font = new Font("MS Gothic", Font.PLAIN, 12);
            name = "Page " + page++;
            rotTx = AffineTransform.getRotateInstance(0.15);
            pt = new PrintJapaneseText(name, font, rotTx, true, preferredSize);
            pane.addTab(name, pt);
            book.append(pt, portrait);
            book.append(pt, landscape);
        }

        pjob.setPageable(book);

        JButton printButton = new JButton("Print");
        printButton.addActionListener(event -> {
            try {
                if (pjob.printDialog()) {
                    pjob.print();
                }
            } catch (PrinterException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });

        JFrame f = new JFrame("PrintTextTest");
        f.add(BorderLayout.CENTER, pane);
        f.add(BorderLayout.SOUTH, printButton);
        f.pack();

        PassFailJFrame.builder()
            .title("PrintTextTest")
            .instructions(INSTRUCTIONS)
            .testTimeOut(10)
            .columns(60)
            .testUI(f)
            .build()
            .awaitAndCheck();
    }

    // The test needs a physical font that supports Latin.
    private static Font physicalFont;
    private static Font getPhysicalFont() {
        if (physicalFont != null) {
            return physicalFont;
        }
        GraphicsEnvironment ge =
            GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] names = ge.getAvailableFontFamilyNames();

        for (String n : names) {
            switch (n) {
                case Font.DIALOG:
                case Font.DIALOG_INPUT:
                case Font.SERIF:
                case Font.SANS_SERIF:
                case Font.MONOSPACED:
                     continue;
                default:
                    Font f = new Font(n, Font.PLAIN, 18);
                    if (f.canDisplayUpTo("AZaz09") == -1) {
                        physicalFont = f;
                        return f;
                    }
            }
        }
        physicalFont = new Font(Font.DIALOG, Font.PLAIN, 18);
        return physicalFont;
    }

    private static class PrintText extends Component implements Printable {

        protected final Font textFont;
        protected final AffineTransform gxTx;
        protected final String page;
        protected final boolean useFM;
        protected final int preferredSize;

        public PrintText(String page, Font font, AffineTransform gxTx, boolean fm, int size) {
            this.page = page;
            this.textFont = font;
            this.gxTx = gxTx;
            this.useFM = fm;
            this.preferredSize = size;
            setBackground(Color.WHITE);
        }

        private static AttributedCharacterIterator getIterator(String s) {
            return new AttributedString(s).getIterator();
        }

        private static String orient(PageFormat pf) {
            if (pf.getOrientation() == PageFormat.PORTRAIT) {
                return "Portrait";
            } else {
                return "Landscape";
            }
        }

        @Override
        public int print(Graphics g, PageFormat pf, int pageIndex) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.translate(pf.getImageableX(), pf.getImageableY());
            g.drawString(page + " " + orient(pf), 50, 20);
            g.translate(0, 25);
            paint(g);
            return PAGE_EXISTS;
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(preferredSize, preferredSize);
        }

        @Override
        public void paint(Graphics g) {

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, getSize().width, getSize().height);

            Graphics2D g2d = (Graphics2D) g;
            if (gxTx != null) {
                g2d.transform(gxTx);
            }
            if (useFM) {
                g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                                     RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            }

            g.setFont(textFont);
            FontMetrics fm = g.getFontMetrics();

            String s;
            int LS = 30;
            int ix = 10, iy = LS + 10;
            g.setColor(Color.BLACK);

            s = "drawString(String str, int x, int y)";
            g.drawString(s, ix, iy);
            if (!textFont.isTransformed()) {
                g.drawLine(ix, iy + 1, ix + fm.stringWidth(s), iy + 1);
            }

            iy += LS;
            s = "drawString(AttributedCharacterIterator iterator, int x, int y)";
            g.drawString(getIterator(s), ix, iy);

            iy += LS;
            s = "\tdrawChars(\t\r\nchar[], int off, int len, int x, int y\t)";
            g.drawChars(s.toCharArray(), 0, s.length(), ix, iy);
            if (!textFont.isTransformed()) {
                g.drawLine(ix, iy + 1, ix + fm.stringWidth(s), iy + 1);
            }

            iy += LS;
            s = "drawBytes(byte[], int off, int len, int x, int y)";
            byte[] data = new byte[s.length()];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) s.charAt(i);
            }
            g.drawBytes(data, 0, data.length, ix, iy);

            Font f = g2d.getFont();
            FontRenderContext frc = g2d.getFontRenderContext();

            iy += LS;
            s = "drawString(String s, float x, float y)";
            g2d.drawString(s, (float) ix, (float) iy);
            if (!textFont.isTransformed()) {
                g.drawLine(ix, iy + 1, ix + fm.stringWidth(s), iy + 1);
            }

            iy += LS;
            s = "drawString(AttributedCharacterIterator iterator, " +
                "float x, float y)";
            g2d.drawString(getIterator(s), (float) ix, (float) iy);

            iy += LS;
            s = "drawGlyphVector(GlyphVector g, float x, float y)";
            GlyphVector gv = f.createGlyphVector(frc, s);
            g2d.drawGlyphVector(gv, ix, iy);
            Point2D adv = gv.getGlyphPosition(gv.getNumGlyphs());
            if (!textFont.isTransformed()) {
                g.drawLine(ix, iy + 1, ix + (int) adv.getX(), iy + 1);
            }

            iy += LS;
            s = "GlyphVector with position adjustments";

            gv = f.createGlyphVector(frc, s);
            int ng = gv.getNumGlyphs();
            adv = gv.getGlyphPosition(ng);
            for (int i = 0; i < ng; i++) {
                Point2D gp = gv.getGlyphPosition(i);
                double gx = gp.getX();
                double gy = gp.getY();
                if (i % 2 == 0) {
                    gy += 5;
                } else {
                    gy -= 5;
                }
                gp.setLocation(gx, gy);
                gv.setGlyphPosition(i, gp);
            }
            g2d.drawGlyphVector(gv, ix, iy);
            if (!textFont.isTransformed()) {
                g.drawLine(ix, iy + 1, ix + (int) adv.getX(), iy + 1);
            }

            iy += LS;
            s = "drawString: \u0924\u094d\u0930 \u0915\u0948\u0930\u0947 End.";
            g.drawString(s, ix, iy);
            if (!textFont.isTransformed()) {
                g.drawLine(ix, iy + 1, ix + fm.stringWidth(s), iy + 1);
            }

            iy += LS;
            s = "TextLayout 1: \u0924\u094d\u0930 \u0915\u0948\u0930\u0947 End.";
            TextLayout tl = new TextLayout(s, new HashMap<>(), frc);
            tl.draw(g2d, ix, iy);

            iy += LS;
            s = "TextLayout 2: \u0924\u094d\u0930 \u0915\u0948\u0930\u0947 End.";
            tl = new TextLayout(s, f, frc);
            tl.draw(g2d, ix, iy);
        }
    }

    private static class PrintJapaneseText extends PrintText {

        public PrintJapaneseText(String page, Font font, AffineTransform gxTx, boolean fm, int size) {
            super(page, font, gxTx, fm, size);
        }

        private static final String TEXT =
            "\u3042\u3044\u3046\u3048\u304a\u30a4\u30ed\u30cf" +
            "\u30cb\u30db\u30d8\u30c8\u4e00\u4e01\u4e02\u4e05\uff08";

        @Override
        public void paint(Graphics g) {

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, getSize().width, getSize().height);

            Graphics2D g2d = (Graphics2D) g;
            if (gxTx != null) {
                g2d.transform(gxTx);
            }
            if (useFM) {
                g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                                     RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            }

            String text = TEXT + TEXT + TEXT;
            g.setColor(Color.BLACK);
            int y = 20;
            float origSize = 7f;
            for (int i = 0; i < 11; i++) {
                float size = origSize + (i * 0.1f);
                g2d.translate(0, size + 6);
                Font f = textFont.deriveFont(size);
                g2d.setFont(f);
                FontMetrics fontMetrics = g2d.getFontMetrics();
                int stringWidth = fontMetrics.stringWidth(text);
                g.drawLine(0, y + 1, stringWidth, y + 1);
                g.drawString(text, 0, y);
                y += 10;
            }
        }
    }
}
