/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.PrintJob;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/*
 * @test
 * @bug 4242308 4255603
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests printing of images
 * @run main/manual ImageTest
 */
public final class ImageTest {

    private static final class ImageFrame extends Frame {
        final Image img;
        PrintJob pjob;

        private ImageFrame() {
            super("ImageFrame");
            img = getToolkit().getImage("image.gif");
        }

        @Override
        public void paint(Graphics g) {
            int width = img.getWidth(this);
            int height = img.getHeight(this);
            if (pjob != null) {
                System.out.println("Size " + pjob.getPageDimension());
                Dimension dim = pjob.getPageDimension();
                if (width > dim.width) {
                    width = dim.width - 30; // take care of paper margin
                }
                if (height > dim.height) {
                    height = dim.height - 30;
                }
            }
            g.drawImage(img, 10, 75, width, height, this);
        }

        private void setPrintJob(PrintJob pj) {
            pjob = pj;
        }

        @Override
        public boolean imageUpdate(Image img, int infoflags,
                                   int x, int y, int w, int h) {
            if ((infoflags & ALLBITS) != 0) {
                repaint();
                return false;
            }
            return true;
        }
    }

    private static Frame init() {
        ImageFrame f = new ImageFrame();
        f.setLayout(new FlowLayout());
        Button b = new Button("Print");
        b.addActionListener(e -> {
            PrintJob pj = Toolkit.getDefaultToolkit()
                    .getPrintJob(f, "ImageTest", null);
            if (pj != null) {
                f.setPrintJob(pj);
                Graphics pg = pj.getGraphics();
                f.paint(pg);
                pg.dispose();
                pj.end();
            }
        });
        f.add(b);
        f.setSize(700, 350);

        return f;
    }

    private static void createImage() throws IOException {
        final BufferedImage bufferedImage =
                new BufferedImage(600, 230, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bufferedImage.createGraphics();

        g2d.setColor(new Color(0xE7E7E7));
        g2d.fillRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());

        g2d.setColor(Color.YELLOW);
        g2d.fillRect(0, 6, 336, 40);
        g2d.setColor(Color.BLACK);
        g2d.drawString("Yellow rectangle", 10, 30);

        g2d.setColor(Color.CYAN);
        g2d.fillRect(132, 85, 141, 138);
        g2d.setColor(Color.BLACK);
        g2d.drawString("Cyan rectangle", 142, 148);

        g2d.setColor(Color.MAGENTA);
        g2d.fillRect(432, 85, 141, 138);
        g2d.setColor(Color.BLACK);
        g2d.drawString("Magenta rectangle", 442, 148);

        g2d.dispose();

        ImageIO.write(bufferedImage, "gif", new File("image.gif"));
    }

    private static final String INSTRUCTIONS = """
             Click the Print button on the Frame. Select a printer from the
             print dialog and click 'OK'. Verify that the image displayed
             in the Frame is correctly printed. Test printing in both Color
             and Monochrome.
            """;

    public static void main(String[] args) throws Exception {

        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        createImage();

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .testUI(ImageTest::init)
                .build()
                .awaitAndCheck();
    }
}
