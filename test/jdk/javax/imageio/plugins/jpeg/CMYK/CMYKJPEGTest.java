/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 * This test verifies that using the built-in ImageI/O JPEG plugin that JPEG images
 * that are in a CMYK ColorSpace can be read into a BufferedImage using the convemience
 * APIS and that and the colours are properly interpreted.
 * Since there is no standard JDK CMYK ColorSpace, this requires that either the image
 * contain an ICC_Profile which can be used by the plugin to create an ICC_ColorSpace
 * or that the plugin provides a suitable default CMYK ColorSpace instance by some other means.
 *
 * The test further verifies that the resultant BufferedImage will be re-written as a CMYK
 * BufferedImage. It can do this so long as the BufferedImage has that CMYK ColorSpace
 * used by its ColorModel.
 *
 * The verification requires re-reading again the re-written image and checking the
 * re-read image still has a CMYK ColorSpace and the same colours.
 *
 * Optionally - not for use in the test harness - the test can be passed a parameter
 * -display to create a UI which renders all the images the test is
 * verifying so it can be manually verified
 */

/*
 * @test
 * @bug 8274735
 * @summary Verify CMYK JPEGs can be read and written
 */

import java.awt.Color;
import static java.awt.Color.*;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class CMYKJPEGTest {

    static String[] fileNames = {
        "black_cmyk.jpg",
        "white_cmyk.jpg",
        "gray_cmyk.jpg",
        "red_cmyk.jpg",
        "blue_cmyk.jpg",
        "green_cmyk.jpg",
        "cyan_cmyk.jpg",
        "magenta_cmyk.jpg",
        "yellow_cmyk.jpg",
    };

    static Color[] colors = {
         black,
         white,
         gray,
         red,
         blue,
         green,
         cyan,
         magenta,
         yellow,
    };

    static boolean display;

    static BufferedImage[] readImages;
    static BufferedImage[] writtenImages;
    static int imageIndex = 0;

    public static void main(String[] args) throws Exception {

        if (args.length > 0) {
            display = "-display".equals(args[0]);
        }

        String sep = System.getProperty("file.separator");
        String dir = System.getProperty("test.src", ".");
        String prefix = dir+sep;

        readImages = new BufferedImage[fileNames.length];
        writtenImages = new BufferedImage[fileNames.length];

        for (String fileName : fileNames) {
            String color = fileName.replace("_cmyk.jpg", "");
            test(prefix+fileName, color, imageIndex++);
        }
        if (display) {
            SwingUtilities.invokeAndWait(() -> createUI());
        }
    }

    static void test(String fileName, String color, int index)
                 throws IOException {

        readImages[index] = ImageIO.read(new File(fileName));
        verify(readImages[index], color, colors[index]);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(readImages[index], "jpg", baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        writtenImages[index] = ImageIO.read(bais);
        verify(writtenImages[index], color, colors[index]);
    }

    static void verify(BufferedImage img, String colorName, Color c) {
        ColorModel cm = img.getColorModel();
        int tc = cm.getNumComponents();
        int cc = cm.getNumColorComponents();
        if (cc != 4 || tc != 4) {
            throw new RuntimeException("Unexpected num comp for " + img);
        }

        int rgb = img.getRGB(0,0);
        int c_red = c.getRed();
        int c_green = c.getGreen();
        int c_blue = c.getBlue();
        int i_red =   (rgb & 0x0ff0000) >> 16;
        int i_green = (rgb & 0x000ff00) >> 8;
        int i_blue =  (rgb & 0x00000ff);
        int tol = 16;
        if ((Math.abs(i_red - c_red) > tol) ||
            (Math.abs(i_green - c_green) > tol) ||
            (Math.abs(i_blue - c_blue) > tol))
        {
           System.err.println("red="+i_red+" green="+i_green+" blue="+i_blue);
           throw new RuntimeException("Too different " + img + " " + colorName + " " + c);
        }
    }

    static class ImageComp extends JComponent {

        BufferedImage img;

        ImageComp(BufferedImage img) {
           this.img = img;
        }

        public Dimension getPreferredSize() {
            return new Dimension(img.getWidth(), img.getHeight());
        }

        public Dimension getMinimumSize() {
           return getPreferredSize();
        }

        public void paintComponent(Graphics g) {
           super.paintComponent(g);
           g.drawImage(img, 0, 0, null);
        }
    }

    static void createUI() {
        JFrame f = new JFrame("CMYK JPEG Test");
        JPanel p = new JPanel();
        p.setLayout(new GridLayout(3, colors.length, 10, 10));
        for (String s :  fileNames) {
           p.add(new JLabel(s.replace("_cmyk.jpg", "")));
        }
        for (BufferedImage i : readImages) {
            p.add(new ImageComp(i));
        }
        for (BufferedImage i : writtenImages) {
            p.add(new ImageComp(i));
        }
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(p);
        f.pack();
        f.setVisible(true);
    }
}
