/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import javax.print.attribute.ResolutionSyntax;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.PrinterResolution;
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
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.GeneralPath;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

/*
 * @test
 * @bug 8251928 8375221
 * @key printer
 * @summary Printable.print method should reflect printer's DPI
 * @library /java/awt/regtesthelpers
 * @requires os.family == "mac"
 * @build PassFailJFrame
 * @run main/manual PrintablePrintDPI
 */

public class PrintablePrintDPI implements Printable {

    private static final double PAPER_DPI = 72.0;
    private static final int DEFAULT_DOCUMENT_DPI = 300;
    private static final int UNITS = ResolutionSyntax.DPI;

    private static final String INSTRUCTIONS = """
           This test checks document DPI.
           To be able to run this test it is required to have a
           printer configured in your user environment.
           Test's steps:
             - Choose a printer.
             - Choose a printer resolution.
             - Press 'Print' button.
           Visual inspection of the printed pages is needed.
           A passing test will print chosen DPI on the printed page,
           2 vertical and 2 horizontal lines.
           """;

    private final PrintService printService;
    private final PrinterResolution printerResolution;

    public static void main(String[] args) throws Exception {
        PrintService[] availablePrintServices = PrintServiceLookup.lookupPrintServices(null, null);
        if (availablePrintServices.length == 0) {
            System.out.println("Available print services not found");
            return;
        }
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testTimeOut(300)
                .title("Document DPI test")
                .testUI(createTestWindow(availablePrintServices))
                .build()
                .awaitAndCheck();
    }

    private static Window createTestWindow(final PrintService[] availablePrintServices) {
        final Window frame = new JFrame("Choose service to test");
        JPanel pnlMain = new JPanel();
        pnlMain.setBorder(new EmptyBorder(5, 5, 5, 5));
        pnlMain.setLayout(new GridLayout(3, 1, 5, 5));
        JLabel lblServices = new JLabel("Available services");
        JLabel lblResolutions = new JLabel("Available resolutions");
        JButton btnPrint = new JButton("Print");
        btnPrint.setEnabled(false);
        JComboBox<PrinterResolution> cbResolutions = new JComboBox<>();
        cbResolutions.setRenderer(new ListCellRenderer<PrinterResolution>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends PrinterResolution> list,
                                                          PrinterResolution value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                String str = value == null ? "" :
                        String.format("%dx%d DPI",
                                value.getCrossFeedResolution(UNITS), value.getFeedResolution(UNITS));
                return new JLabel(str);
            }
        });
        cbResolutions.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                btnPrint.setEnabled(cbResolutions.getSelectedItem() != null);
            }
        });

        JComboBox<PrintService> cbServices = new JComboBox<>();
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
                PrintService ps = (PrintService) cbServices.getSelectedItem();
                cbResolutions.removeAllItems();
                btnPrint.setEnabled(ps != null);
                if (ps != null) {
                    PrinterResolution[] supportedResolutions = (PrinterResolution[])ps
                            .getSupportedAttributeValues(PrinterResolution.class, null, null);
                    if (supportedResolutions == null || supportedResolutions.length == 0) {
                        cbResolutions.addItem(new PrinterResolution(DEFAULT_DOCUMENT_DPI, DEFAULT_DOCUMENT_DPI, UNITS));
                    } else {
                        for (PrinterResolution pr : supportedResolutions) {
                            cbResolutions.addItem(pr);
                        }
                    }
                }
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
                PrinterResolution resolution = (PrinterResolution) cbResolutions.getSelectedItem();
                if (printService != null && resolution != null) {
                    cbServices.setEnabled(false);
                    cbResolutions.setEnabled(false);
                    btnPrint.setEnabled(false);
                    frame.setVisible(false);
                    new PrintablePrintDPI(printService, resolution).test();
                }
            }
        });
        pnlMain.add(lblServices);
        pnlMain.add(cbServices);
        pnlMain.add(lblResolutions);
        pnlMain.add(cbResolutions);
        pnlMain.add(btnPrint);
        frame.add(pnlMain);
        frame.pack();
        return frame;
    }

    private PrintablePrintDPI(PrintService printService, PrinterResolution printerResolution) {
        this.printService = printService;
        this.printerResolution = printerResolution;
    }

    private void test() {
        System.out.printf("Perform test using %s and %dx%d DPI\n",
                printService.getName(),
                printerResolution.getCrossFeedResolution(UNITS),
                printerResolution.getFeedResolution(UNITS));

        PrinterJob job = PrinterJob.getPrinterJob();
        try {
            PrintRequestAttributeSet attributeSet = new HashPrintRequestAttributeSet();
            attributeSet.add(printerResolution);
            attributeSet.add(OrientationRequested.PORTRAIT);
            job.setPrintService(printService);
            job.setPrintable(this);
            if (job.printDialog(attributeSet)) {
                job.print(attributeSet);
            }
        } catch (PrinterException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        if (pageIndex > 0) {
            return NO_SUCH_PAGE;
        }
        final int[] deviceRes = printerResolution.getResolution(ResolutionSyntax.DPI);

        Graphics2D g2 = (Graphics2D)graphics;

        double hRes = g2.getTransform().getScaleX() * PAPER_DPI;
        double vRes = g2.getTransform().getScaleY() * PAPER_DPI;

        // Horizontal and vertical document resolution
        g2.drawLine((int)pageFormat.getImageableX() + 5, (int)pageFormat.getImageableY() + 5,
                (int)pageFormat.getImageableX() + 50, (int)pageFormat.getImageableY() + 5);
        g2.drawString(Integer.toString((int)hRes),
                (int)pageFormat.getImageableX() + 60,
                (int)pageFormat.getImageableY() + 5 + g2.getFontMetrics().getHeight() / 2);

        g2.drawLine((int)pageFormat.getImageableX() + 5, (int)pageFormat.getImageableY() + 5,
                (int)pageFormat.getImageableX() + 5, (int)pageFormat.getImageableY() + 50);
        g2.drawString(Integer.toString((int)vRes),
                (int)pageFormat.getImageableX() + 5, (int)pageFormat.getImageableY() + 60);

        String msg = String.format(
                "Expected DPI: %dx%d, actual: %dx%d.\n",
                deviceRes[0], deviceRes[1], (int)hRes, (int)vRes);
        System.out.println(msg);

        int msgX = (int)pageFormat.getImageableX() +
                g2.getFontMetrics().stringWidth(Integer.toString((int)vRes)) + 20;
        int msgY = (int)pageFormat.getImageableY() +
                g2.getFontMetrics().getHeight() + 20;

        g2.drawString(msg, msgX, msgY);
        msgY += 20;
        g2.drawString("ScaleX: " + g2.getTransform().getScaleX(), msgX, msgY);
        msgY += 20;
        g2.drawString("ScaleY: " + g2.getTransform().getScaleY(), msgX, msgY);

        final float lineWidth = 0.2f;
        double pageWidth = pageFormat.getWidth();
        double xLeft = pageWidth / 10;
        double yBase = pageFormat.getHeight() / 2;
        double xBase = pageFormat.getWidth() / 2;
        double yTop = yBase + 40;
        double yBottom = pageFormat.getHeight() - pageFormat.getHeight() / 10;

        g2.setStroke(new BasicStroke(lineWidth));

        double xRight = pageWidth - xLeft;
        g2.drawLine((int) xLeft, (int) yBase + 80,
                (int) (xRight),(int) yBase + 80);
        g2.drawLine((int) xBase, (int) yTop,
                (int) (xBase),(int) yBottom);

        GeneralPath line = new GeneralPath();
        double halfLineWidth = lineWidth / 2.0f;
        double yLine = yBase + 100;
        double xLine = xBase + 20;
        line.moveTo(xLeft, yLine);
        line.lineTo(xLeft, yLine - halfLineWidth);
        line.lineTo(xLine - halfLineWidth, yLine - halfLineWidth);
        line.lineTo(xLine - halfLineWidth, yTop);
        line.lineTo(xLine + halfLineWidth, yTop);
        line.lineTo(xLine + halfLineWidth, yLine - halfLineWidth);
        line.lineTo(xRight, yLine - halfLineWidth);
        line.lineTo(xRight, yLine + halfLineWidth);
        line.lineTo(xLine + halfLineWidth, yLine + halfLineWidth);
        line.lineTo(xLine + halfLineWidth, yBottom);
        line.lineTo(xLine - halfLineWidth, yBottom);
        line.lineTo(xLine - halfLineWidth, yLine + halfLineWidth);
        line.lineTo(xLeft, yLine + halfLineWidth );
        line.closePath();
        g2.clip(line);

        g2.setColor(Color.RED);

        line.reset();
        line.moveTo(xLeft, yLine);
        line.lineTo(xRight, yLine);
        g2.draw(line);

        line.reset();
        line.moveTo(xBase + 20, yTop);
        line.lineTo(xBase + 20, yBottom);
        g2.draw(line);

        return PAGE_EXISTS;
    }
}
