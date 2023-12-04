/*
 * Copyright (c) 2006, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4521945 7006865
 * @summary Test printing images of different types.
 * @key printer
 * @run main/manual ImageTypes
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;

import static java.awt.Color.*;
import static java.awt.image.BufferedImage.*;

public class ImageTypes {

    private static Frame testFrame;
    private static ImageCanvas imageCanvas;
    private static volatile boolean testResult;
    private static final CountDownLatch countDownLatch = new CountDownLatch(1);

    public static void main(String[] args) throws InterruptedException {
        createTestUI();
        if (!countDownLatch.await(10, TimeUnit.MINUTES)) {
            throw new RuntimeException("Timeout : No action was performed on the test UI.");
        }
        if (!testResult) {
            throw new RuntimeException("Test failed!");
        }
    }

    public static void createTestUI() {
        testFrame = new Frame("Image Types Printing Test");
        imageCanvas = new ImageCanvas();
        testFrame.add("Center", imageCanvas);

        Button printButton = new Button("Print");
        printButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PrinterJob pj = PrinterJob.getPrinterJob();
                if (pj.getPrintService() == null) {
                    System.out.println("No printers. Test cannot continue. Install " +
                            "printer and restart the test.");
                    return;
                }
                PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
                if (pj != null && pj.printDialog(attrs)) {
                    pj.setPrintable(imageCanvas);
                    try {
                        pj.print(attrs);
                    } catch (PrinterException pe) {
                        pe.printStackTrace();
                        throw new RuntimeException("Exception whilst printing.");
                    } finally {
                        System.out.println("PRINT RETURNED OK.");
                    }
                }
            }
        });

        Button passButton = new Button("Pass");
        passButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                testResult = true;
                countDownLatch.countDown();
                testFrame.dispose();
            }
        });

        Button failButton = new Button("Fail");
        failButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                testResult = false;
                countDownLatch.countDown();
                testFrame.dispose();
            }
        });

        Panel buttonPanel = new Panel(new GridLayout(1,3));
        buttonPanel.add(printButton);
        buttonPanel.add(passButton);
        buttonPanel.add(failButton);
        testFrame.add("South", buttonPanel);
        testFrame.add("North", getInstructions());
        testFrame.pack();
        testFrame.setVisible(true);
    }

    private static TextArea getInstructions() {
        String testInstruction = "This is a manual test as it requires that you compare "+
                "the on-screen rendering with the printed output.\n"+
                "Select the 'Print' button to print out the test.\n"+
                "For each image compare the printed one to the on-screen one.\n"+
                "Press Pass button if the onscreen and printed rendering " +
                "match else Press fail button";
        TextArea ta = new TextArea(testInstruction,7, 60,
                TextArea.SCROLLBARS_NONE);
        ta.setFont(new Font("Dialog", Font.PLAIN, 11));
        return ta;
    }
}

class ImageCanvas extends Component implements Printable {

    IndexColorModel icm2 = null;
    IndexColorModel icm4 = null;
    BufferedImage opaqueImg = null;
    BufferedImage transImg = null;
    int sw=99, sh=99;

    void paintImage(BufferedImage bi, Color c1, Color c2) {
        GradientPaint tp= new GradientPaint(0.0f, 0.0f, c1, 10f, 8f, c2, true);
        Graphics2D g2d = (Graphics2D)bi.getGraphics();
        g2d.setPaint(tp);
        g2d.fillRect(0, 0, sw, sh);
        g2d.setColor(gray);
        int cnt=0;
        Font font = new Font("Serif", Font.PLAIN, 11);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        for (int y=12;y<sh;y+=12) {
            int x = 0;
            while (x < sw) {
                String s = (new Integer(++cnt)).toString();
                g2d.drawString(s, x, y);
                x+= fm.stringWidth(s);
            }
        }
    }

    ImageCanvas() {
        opaqueImg = new BufferedImage(sw, sh, TYPE_INT_RGB);
        Color o1 = new Color(0, 0, 0);
        Color o2 = new Color(255, 255, 255);
        paintImage(opaqueImg, o1, o2);

        transImg = new BufferedImage(sw, sh, TYPE_INT_ARGB);
        Color t1 = new Color(255, 255, 255, 220);
        Color t2 = new Color(255, 200, 0, 220);
        paintImage(transImg, t1, t2);

        /* greyscale 2bpp */
        byte[] arr2bpp =  {(byte)0, (byte)0x55, (byte)0xaa, (byte)0xff};
        icm2 = new IndexColorModel(2, 4, arr2bpp, arr2bpp, arr2bpp);

        /* color 4bpp */
        int[] cmap = new int[16];
        cmap[0] = black.getRGB();
        cmap[1] = white.getRGB();
        cmap[2] = gray.getRGB();
        cmap[3] = lightGray.getRGB();
        cmap[4] = red.getRGB();
        cmap[5] = green.getRGB();
        cmap[6] = blue.getRGB();
        cmap[7] = yellow.getRGB();
        cmap[8] = cyan.getRGB();
        cmap[9] = magenta.getRGB();
        cmap[10] = orange.getRGB();
        cmap[11] = pink.getRGB();
        cmap[12] = darkGray.getRGB();
        cmap[13] = 192 << 16 ; // dark red.
        cmap[14] = 192 << 8; // dark green
        cmap[15] = 192; // dark blue

        icm4 = new IndexColorModel(4, 16, cmap, 0, false, -1,
                                   DataBuffer.TYPE_BYTE);

    }

    public int print(Graphics g, PageFormat pgFmt, int pgIndex) {
        if (pgIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }
        Graphics2D g2d = (Graphics2D)g;
        g2d.translate(pgFmt.getImageableX(), pgFmt.getImageableY());
        paint(g2d);
        return Printable.PAGE_EXISTS;
    }

    private void drawImage(Graphics g, int biType, IndexColorModel icm) {
        BufferedImage bi;
        if (icm != null) {
            bi = new BufferedImage(sw, sh, biType, icm);
        } else {
            bi = new BufferedImage(sw, sh, biType);
        }

        Graphics big = bi.getGraphics();
        if (bi.getColorModel().getPixelSize() <=2) {
            big.drawImage(opaqueImg, 0, 0, null);
        } else {
            big.drawImage(transImg, 0, 0, null);
        }
        g.drawImage(bi, 0, 0, null);
    }

    public void paint(Graphics g) {
        int incX = sw+10, incY = sh+10;

        g.translate(10, 10);

        drawImage(g, TYPE_INT_RGB, null);
        g.translate(incX, 0);

        drawImage(g, TYPE_INT_BGR, null);
        g.translate(incX, 0);

        drawImage(g, TYPE_INT_ARGB, null);
        g.translate(incX, 0);

        drawImage(g, TYPE_INT_ARGB_PRE, null);
        g.translate(-3*incX, incY);

        drawImage(g, TYPE_3BYTE_BGR, null);
        g.translate(incX, 0);

        drawImage(g, TYPE_4BYTE_ABGR, null);
        g.translate(incX, 0);

        drawImage(g, TYPE_4BYTE_ABGR_PRE, null);
        g.translate(incX, 0);

        drawImage(g, TYPE_USHORT_555_RGB, null);
        g.translate(-3*incX, incY);

        drawImage(g, TYPE_USHORT_555_RGB, null);
        g.translate(incX, 0);

        drawImage(g, TYPE_USHORT_GRAY, null);
        g.translate(incX, 0);

        drawImage(g, TYPE_BYTE_GRAY, null);
        g.translate(incX, 0);

        drawImage(g, TYPE_BYTE_INDEXED, null);
        g.translate(-3*incX, incY);

        drawImage(g, TYPE_BYTE_BINARY, null);
        g.translate(incX, 0);

        drawImage(g, TYPE_BYTE_BINARY, icm2);
        g.translate(incX, 0);

        drawImage(g, TYPE_BYTE_BINARY, icm4);
        g.translate(incX, 0);

        drawImage(g, TYPE_BYTE_INDEXED, icm2);
        g.translate(-3*incX, incY);

        drawImage(g, TYPE_BYTE_INDEXED, icm4);
        g.translate(incX, 0);
    }

    /* Size is chosen to match default imageable width of a NA letter
     * page. This means there will be clipping, what is clipped will
     * depend on PageFormat orientation.
     */
    public Dimension getPreferredSize() {
        return new Dimension(468, 600);
    }
}
