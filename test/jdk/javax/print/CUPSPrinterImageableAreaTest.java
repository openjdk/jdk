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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.Size2DSyntax;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

/*
 * @test
 * @bug 8344119
 * @key printer
 * @requires (os.family == "linux" | os.family == "mac")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary CUPSPrinter imageable area
 * @run main/manual CUPSPrinterImageableAreaTest
 */

public class CUPSPrinterImageableAreaTest {

    private static final List<MediaSizeName> ALLOWED_MEDIA_LIST = List.of(MediaSizeName.ISO_A4, MediaSizeName.NA_LETTER);
    private static final double DPI = 72.0;
    private static final double MM_PER_INCH = 2.54;
    private static final String INSTRUCTIONS = """
            <html>
                <div>
                The test checks that the media margins fetched from the printer's PPD file are correct.<br>
                Press the '<b>Print sample</b>' button to print a test page.<br>
                Required paper size and expected margins will be shown on the print dialog.
                A passing test will print the page with a black rectangle along the printable area.
                Ensure that all sides of the rectangle are printed.<br>
                Click '<b>Pass</b>' button, or click '<b>Fail</b>' button if the test failed.
                </div>
            <html>
            """;
    private static final String PAPER_INSTRUCTIONS_FORMAT = """
            <html><body style='margin: 0; text-align:left;'>
            Required paper size: <ul><li>%s</li></ul>
            Expected margins: <ul><li>left: %.1f</li>
            <li>bottom: %.1f</li>
            <li>right: %.1f</li>
            <li>top: %.1f</li>
            </ul></body></html>
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testUI(createTestUI())
                .columns(55)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        final TestServiceData[] testServiceList = getTestServiceList();
        if (testServiceList.length == 0) {
            throw new RuntimeException("Print services support borderless print only");
        }

        final JFrame frame = new JFrame("CUPS Printer imageable area test");
        JPanel pnlRoot = new JPanel();
        JLabel lblPrintServices = new JLabel("Select a print service for the test");
        JComboBox<String> cbPrintServices = new JComboBox<>();
        JPanel pnlInstruction = new JPanel();
        JLabel lblInstruction = new JLabel();
        JButton btnPrint = new JButton("Print sample");

        lblPrintServices.setLabelFor(cbPrintServices);
        lblPrintServices.setAlignmentX(SwingConstants.LEFT);

        lblInstruction.setPreferredSize(new Dimension(250, 150));
        pnlInstruction.setBackground(Color.white);
        pnlInstruction.add(lblInstruction);

        cbPrintServices.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedIndex = cbPrintServices.getSelectedIndex();
                if (selectedIndex < 0) {
                    lblInstruction.setText("");
                    btnPrint.setEnabled(false);
                    return;
                }

                TestServiceData testServiceData = testServiceList[selectedIndex];
                PageFormat pageFormat = testServiceData.pageFormat;
                // margins: left, bottom, right, top
                double[] margins = new double[]{
                        pageFormat.getImageableX(),
                        pageFormat.getHeight() - pageFormat.getImageableHeight() - pageFormat.getImageableY(),
                        pageFormat.getWidth() - pageFormat.getImageableWidth() - pageFormat.getImageableX(),
                        pageFormat.getImageableY()
                };
                String printServiceInstructions = PAPER_INSTRUCTIONS_FORMAT.formatted(
                        testServiceData.mediaSizeName.toString(), inchesToMM(margins[0]),
                        inchesToMM(margins[1]), inchesToMM(margins[2]), inchesToMM(margins[3]));
                lblInstruction.setText(printServiceInstructions);
                btnPrint.setEnabled(true);
            }
        });

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for(TestServiceData tsd : testServiceList) {
            model.addElement(tsd.printService.getName());
        }
        cbPrintServices.setModel(model);
        cbPrintServices.setSelectedIndex(-1);
        PrintService defaultPrintService = PrintServiceLookup.lookupDefaultPrintService();
        if (defaultPrintService != null && model.getIndexOf(defaultPrintService.getName()) >= 0) {
            cbPrintServices.setSelectedItem(defaultPrintService.getName());
        } else {
            cbPrintServices.setSelectedIndex(0);
        }

        btnPrint.setPreferredSize(new Dimension(200, 80));
        btnPrint.addActionListener((e) -> {
            int selectedIndex = cbPrintServices.getSelectedIndex();
            if (selectedIndex < 0) {
                return;
            }
            btnPrint.setEnabled(false);
            cbPrintServices.setEnabled(false);
            TestServiceData testServiceData = testServiceList[selectedIndex];
            PrinterJob job = PrinterJob.getPrinterJob();
            try {
                job.setPrintService(testServiceData.printService);
                job.setPrintable(new RectPrintable(), testServiceData.pageFormat);
                job.print();
            } catch (PrinterException ex) {
                throw new RuntimeException(ex);
            }
        });

        LayoutManager layout = new GridBagLayout();
        pnlRoot.setLayout(layout);

        addGridBagComponent(pnlRoot, lblPrintServices, 0);
        addGridBagComponent(pnlRoot, cbPrintServices, 1);
        addGridBagComponent(pnlRoot, pnlInstruction, 2);
        addGridBagComponent(pnlRoot, btnPrint, 3);

        frame.add(pnlRoot);
        frame.pack();
        frame.setResizable(false);
        return frame;
    }

    private static TestServiceData[] getTestServiceList() {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        if (printServices == null || printServices.length == 0) {
            throw new RuntimeException("Print services not found");
        }

        List<TestServiceData> testServiceList = new ArrayList<>();
        for (PrintService ps : printServices) {
            try {
                MediaSizeName msn = getTestMediaSizeName(ps);
                PageFormat pf = createTestPageFormat(msn, ps);
                testServiceList.add(new TestServiceData(ps, msn, pf));
            } catch (Exception ignore) { //in case if can't create required PageFormat
            }
        }
        return testServiceList.toArray(TestServiceData[]::new);
    }

    private static MediaSizeName getTestMediaSizeName(PrintService printService) {
        //Use printer's default media or one of the alloed medias
        Media testMedia = (Media) printService.getDefaultAttributeValue(Media.class);
        if (testMedia == null) {
            Media[] medias = (Media[]) printService
                    .getSupportedAttributeValues(Media.class, null, null);
            if (medias == null || medias.length == 0) {
                throw new RuntimeException("Medias not found");
            }
            for (Media media : medias) {
                if (ALLOWED_MEDIA_LIST.contains(media)) {
                    testMedia = media;
                    break;
                }
            }
        }
        if (!(testMedia instanceof MediaSizeName)) {
            throw new RuntimeException("Test media not found");
        }
        return (MediaSizeName) testMedia;
    }

    private static PageFormat createTestPageFormat(MediaSizeName testMedia, PrintService printService) {
        MediaSize ms = MediaSize.getMediaSizeForName(testMedia);
        if (ms == null) {
            throw new RuntimeException("Media size not defined");
        }

        MediaPrintableArea mpa = getMaximumMediaPrintableArea(testMedia, ms, printService);
        if (mpa == null) {
            throw new RuntimeException("Media printable area not defined");
        }

        PageFormat pageFormat = new PageFormat();
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        Paper paper = new Paper();
        paper.setSize(ms.getX(MediaSize.INCH) * DPI, ms.getY(MediaSize.INCH) * DPI);
        paper.setImageableArea(mpa.getX(MediaPrintableArea.INCH) * DPI,
                mpa.getY(MediaPrintableArea.INCH) * DPI,
                mpa.getWidth(MediaPrintableArea.INCH) * DPI,
                mpa.getHeight(MediaPrintableArea.INCH) * DPI);
        pageFormat.setPaper(paper);
        return pageFormat;
    }

    private static MediaPrintableArea getMaximumMediaPrintableArea(MediaSizeName msn, MediaSize ms,
                                                                   PrintService printService) {
        final float paperSizeX = ms.getX(Size2DSyntax.MM);
        final float paperSizeY = ms.getY(Size2DSyntax.MM);
        final float sizeDev = 0.2f;

        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
        attrs.add(msn);
        MediaPrintableArea[] mpas = (MediaPrintableArea[]) printService
                .getSupportedAttributeValues(MediaPrintableArea.class, null, attrs);
        if (mpas == null || mpas.length == 0) {
            throw new RuntimeException("Printable area not found");
        }

        MediaPrintableArea mpa = null;
        for (MediaPrintableArea area : mpas) {
            float mpaSize = area.getWidth(MediaPrintableArea.MM) * area.getHeight(MediaPrintableArea.MM);
            //do not use borderless printable area
            if (sizeDev >= Math.abs(paperSizeX - area.getWidth(MediaPrintableArea.MM)) &&
                    sizeDev >= Math.abs(paperSizeY - area.getHeight(MediaPrintableArea.MM))) {
                continue;
            }
            if (mpa == null) {
                mpa = area;
            } else if (mpaSize > (area.getWidth(MediaPrintableArea.MM) * area.getHeight(MediaPrintableArea.MM))) {
                mpa = area;
            }
        }
        return mpa;
    }

    private static double inchesToMM(double inches) {
        return inches / MM_PER_INCH;
    }

    private static void addGridBagComponent(JPanel p, Component c, int y) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.gridx = 0;
        constraints.gridy = y;
        p.add(c, constraints);
    }

    private static class RectPrintable implements Printable {

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
            if (pageIndex == 0) {
                Graphics2D g = (Graphics2D) graphics;
                g.setStroke(new BasicStroke(3));
                g.drawRect((int) pageFormat.getImageableX(), (int) pageFormat.getImageableY(),
                        (int) pageFormat.getImageableWidth(), (int) pageFormat.getImageableHeight());
                return PAGE_EXISTS;
            }
            return NO_SUCH_PAGE;
        }
    }

    private static class TestServiceData {

        final PrintService printService;
        final MediaSizeName mediaSizeName;
        final PageFormat pageFormat;

        private TestServiceData(PrintService printService, MediaSizeName mediaSizeName, PageFormat pageFormat) {
            this.printService = printService;
            this.mediaSizeName = mediaSizeName;
            this.pageFormat = pageFormat;
        }
    }
}
