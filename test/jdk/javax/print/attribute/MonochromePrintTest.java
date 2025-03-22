/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, BELLSOFT. All rights reserved.
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


import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.Size2DSyntax;
import javax.print.attribute.standard.Chromaticity;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @bug 8315113
 * @key printer
 * @requires (os.family == "mac")
 * @summary javax.print: Support monochrome printing
 * @run main/manual MonochromePrintTest
 */

public class MonochromePrintTest {

    private static final String INSTRUCTIONS = """
           This test checks availability of the monochrome printing
           on color printers.
           To be able to run this test it is required to have a color
           printer configured in your user environment.
           Test's steps:
             - Choose a printer.
             - Press 'Print' button.
           Visual inspection of the printed pages is needed.
           A passing test will print two pages with
           color and grayscale appearances
           """;

    public static void main(String[] args) throws Exception {
        PrintService[] availablePrintServices = getTestablePrintServices();
        if (availablePrintServices.length == 0) {
            System.out.println("Available print services not found");
            return;
        }
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testTimeOut(300)
                .title("Monochrome printing")
                .testUI(createTestWindow(availablePrintServices))
                .build()
                .awaitAndCheck();
    }

    private static Window createTestWindow(final PrintService[] availablePrintServices) {
        Window frame = new JFrame("Choose service to test");
        JPanel pnlMain = new JPanel();
        pnlMain.setBorder(new EmptyBorder(5,5,5,5));
        pnlMain.setLayout(new GridLayout(3, 1, 5, 5));
        JLabel lblServices = new JLabel("Available services");
        JComboBox<PrintService> cbServices = new JComboBox<>();
        JButton btnPrint = new JButton("Print");
        btnPrint.setEnabled(false);
        cbServices.setRenderer(new ListCellRenderer<PrintService>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends PrintService> list, PrintService value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                return new JLabel(value == null ? "" : value.getName());
            }
        });
        cbServices.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                btnPrint.setEnabled(cbServices.getSelectedItem() != null);
            }
        });
        for (PrintService ps : availablePrintServices) {
            cbServices.addItem(ps);
        }
        lblServices.setLabelFor(cbServices);
        btnPrint.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PrintService printService = (PrintService) cbServices.getSelectedItem();
                if (printService != null) {
                    cbServices.setEnabled(false);
                    btnPrint.setEnabled(false);
                    test(printService);
                }
            }
        });
        pnlMain.add(lblServices);
        pnlMain.add(cbServices);
        pnlMain.add(btnPrint);
        frame.add(pnlMain);
        frame.pack();
        return frame;
    }

    private static PrintService[] getTestablePrintServices() {
        List<PrintService> testablePrintServices = new ArrayList<>();
        for (PrintService ps : PrintServiceLookup.lookupPrintServices(null,null)) {
            if (ps.isAttributeValueSupported(Chromaticity.MONOCHROME, null, null) &&
                    ps.isAttributeValueSupported(Chromaticity.COLOR, null, null)) {
                testablePrintServices.add(ps);
            }
        }
        return testablePrintServices.toArray(new PrintService[0]);
    }

    private static void test(PrintService printService) {
        try {
            print(printService, Chromaticity.COLOR);
            print(printService, Chromaticity.MONOCHROME);
        } catch (PrinterException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void print(PrintService printService, Chromaticity chromaticity)
            throws PrinterException {
        PrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();
        attr.add(chromaticity);
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(printService);
        job.setJobName("Print with " + chromaticity);
        job.setPrintable(new ChromaticityAttributePrintable(chromaticity));
        job.print(attr);
    }

    private static class ChromaticityAttributePrintable implements Printable {

        private final Chromaticity chromaticity;

        public ChromaticityAttributePrintable(Chromaticity chromaticity) {
            this.chromaticity = chromaticity;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {

            if (pageIndex != 0) {
                return NO_SUCH_PAGE;
            }

            final int sx = (int) Math.ceil(pageFormat.getImageableX());
            final int sy = (int) Math.ceil(pageFormat.getImageableY());

            Graphics2D g = (Graphics2D) graphics;

            BufferedImage bufferdImage = getBufferedImage((int) Math.ceil(pageFormat.getImageableWidth() / 3),
                    (int) Math.ceil(pageFormat.getImageableHeight() / 7));
            g.drawImage(bufferdImage, null, sx, sy);

            double defaultMediaSizeWidth = MediaSize.getMediaSizeForName(MediaSizeName.ISO_A4)
                    .getX(Size2DSyntax.INCH) * 72;
            double scale = pageFormat.getWidth() / defaultMediaSizeWidth;

            final int squareSideLenngth = (int)(50 * scale);
            final int offset = (int)(10 * scale);
            int imh = sy + (int) Math.ceil(pageFormat.getImageableHeight() / 7) + offset;

            g.setColor(Color.ORANGE);
            g.drawRect(sx, imh, squareSideLenngth, squareSideLenngth);
            imh = imh + squareSideLenngth + offset;

            g.setColor(Color.BLUE);
            g.fillOval(sx, imh, squareSideLenngth, squareSideLenngth);
            imh = imh + squareSideLenngth + offset;

            Paint paint = new LinearGradientPaint(0, 0,
                    squareSideLenngth>>1, offset>>1, new float[]{0.0f, 0.2f, 1.0f},
                    new Color[]{Color.RED, Color.GREEN, Color.CYAN}, MultipleGradientPaint.CycleMethod.REPEAT);
            g.setPaint(paint);
            g.setStroke(new BasicStroke(squareSideLenngth));
            g.fillRect(sx, imh + offset, squareSideLenngth, squareSideLenngth);
            imh = imh + squareSideLenngth + offset;

            paint = new RadialGradientPaint(offset, offset, offset>>1, new float[]{0.0f, 0.5f, 1.0f},
                    new Color[]{Color.RED, Color.GREEN, Color.CYAN}, MultipleGradientPaint.CycleMethod.REPEAT);
            g.setPaint(paint);
            g.fillRect(sx, imh + offset, squareSideLenngth, squareSideLenngth);
            imh = imh + squareSideLenngth + offset;

            g.setStroke(new BasicStroke(offset>>1));
            g.setColor(Color.PINK);
            g.drawString("This page should be " + chromaticity, sx, imh + squareSideLenngth);

            return PAGE_EXISTS;
        }

        private BufferedImage getBufferedImage(int width, int height) {
            Color[] colors = new Color[]{
                    Color.RED, Color.ORANGE, Color.BLUE,
                    Color.CYAN, Color.MAGENTA, Color.GREEN
            };
            BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            final int secondSquareOffset = width / 3;
            final int thirdSquareOffset = secondSquareOffset * 2;
            final int squareHeight = height / 2;

            int offset = 0;
            Color color;
            for (int y = 0; y < height; y++) {
                if (y > squareHeight) {
                    offset = 3;
                }
                for (int x = 0; x < width; x++) {
                    if (x >= thirdSquareOffset) {
                        color = colors[offset + 2];
                    } else if (x >= secondSquareOffset) {
                        color = colors[offset + 1];
                    } else {
                        color = colors[offset];
                    }
                    bufferedImage.setRGB(x, y, color.getRGB());
                }
            }
            return bufferedImage;
        }
    }

}