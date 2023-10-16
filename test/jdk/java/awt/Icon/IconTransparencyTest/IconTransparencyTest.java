/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

/*
 * @test
 * @bug 4987171
 * @key headful
 * @summary GIF transparency in frame icons not work with Metacity/GNOME
 * @requires (os.family != "mac")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual IconTransparencyTest
 */

public class IconTransparencyTest {
    private static final String INSTRUCTIONS = """
            The icon of the frame and the resized icon in the label should be transparent.
            Transparency can be verified by checking if the background color (pink)
            is visible in and around icon within the JLabel.

            Press continue to view next icon (6 total).
            Icon might be presented as grayscale image.

            For the 3rd icon in JLabel, the 2nd vertical slot is transparent, hence
            the background color (pink) should be visible at the 2nd vertical slot.

            For the 4th icon in JLabel, the 5th vertical slot is transparent, hence
            the background color (pink) should be visible at the 5th vertical slot.

            Press Pass or Fail at the end of test.
            """;

    static class TestLabel extends JLabel {
        public void paint(Graphics g) {
            Dimension d = getSize();
            g.setColor(Color.PINK);
            g.fillRect(0, 0, d.width, d.height);
            Icon icon = getIcon();
            if (icon != null) {
                icon.paintIcon(this, g, 0, 0);
            }
            int iw = (icon != null) ? icon.getIconWidth() + 3 : 3;
            if (d.width - iw > 0) {
                g.setColor(Color.BLACK);
                g.drawString(getText(), iw, 16);
            }
        }
    }

    static class TestFrame implements ActionListener {
        static final int TEST_CNT = 6;
        int currTest = 0;
        static ImageIcon[] testIcon;

        TestLabel label;
        JButton button;
        static JFrame frame;

        final String[] testText = {
                "1st Icon: Size 16x16, GIF",
                "2nd Icon: Size 48x48, GIF",
                "3rd Icon: Size 64x64, GIF",
                "4th Icon: Size 64x64, GIF",
                "5th Icon: Size 64x64, PNG",
                "No Icon (system default)"
        };

        TestFrame() throws IOException {

            generateIcon(16, "img_16.gif", 13, 15, 1, "gif");
            generateIcon(48, "img_48.gif", 36, 40, 4, "gif");
            generateIcon(64, "img_64.png", 50, 58, 4, "png");

            // gif created with GREEN selected as transparent color index in IndexColorModel
            generateGIFWithIndexColorModel(64, "greenTransparent.gif", 1);
            // gif created with BLACK selected as transparent color index in IndexColorModel
            generateGIFWithIndexColorModel(64, "blackTransparent.gif", 4);

            testIcon = new ImageIcon[] {
                    new ImageIcon("img_16.gif"),
                    new ImageIcon("img_48.gif"),
                    new ImageIcon("greenTransparent.gif"),
                    new ImageIcon("blackTransparent.gif"),
                    new ImageIcon("img_64.png"),
                    null
            };
        }

        public void createAndShowGUI() {
            frame = new JFrame();
            //create hint label
            label = new TestLabel();
            label.setVisible(true);
            frame.add(label, BorderLayout.WEST);

            //create button
            button = new JButton("Continue");
            button.setVisible(true);
            button.addActionListener(this);
            frame.add(button, BorderLayout.EAST);

            //show first sample
            frame.setIconImage(testIcon[0].getImage());
            label.setIcon(testIcon[0]);
            label.setText(testText[0]);
            frame.pack();

            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame.positionTestWindow(frame,
                    PassFailJFrame.Position.HORIZONTAL);
            frame.setVisible(true);
        }

        public void actionPerformed(ActionEvent event) {
            currTest++;
            if (currTest < TEST_CNT) {
                if (testIcon[currTest] != null) {
                    frame.setIconImage(testIcon[currTest].getImage());
                } else {
                    frame.setIconImage(null);
                }

                label.setIcon(testIcon[currTest]);
                label.setText(testText[currTest]);
            } else {
                button.setEnabled(false);
                button.setText("No more icons left.");
            }
            frame.revalidate();
            frame.pack();
        }
    }

    public static void main(String[] args) throws Exception {
        TestFrame testFrame = new TestFrame();
        PassFailJFrame passFailJFrame = new PassFailJFrame("Icon Transparency " +
                "Test Instructions", INSTRUCTIONS, 5, 16, 46);
        SwingUtilities.invokeAndWait(testFrame::createAndShowGUI);
        passFailJFrame.awaitAndCheck();
    }

    public static void generateIcon(int size, String filename, int fontSize,
                                    int yText, int lnHeight, String type) throws IOException {
        BufferedImage bImg = new BufferedImage(size, size, TYPE_INT_ARGB);
        Graphics2D g2d = bImg.createGraphics();
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, size, size);

        g2d.setComposite(AlphaComposite.Src);
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, size, lnHeight);
        g2d.setColor(Color.GREEN);
        g2d.fillRect(0, lnHeight * 2, size, lnHeight);

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g2d.setFont(new Font("Dialog", Font.PLAIN, fontSize));
        g2d.setColor(Color.RED);
        g2d.drawString("TR", 0, yText);
        g2d.dispose();

        ImageIO.write(bImg, type, new File(filename));
    }

    protected static void generateGIFWithIndexColorModel(int size, String filename,
                                        int transparentColorIndex) throws IOException {
        IndexColorModel icm = createIndexedBitmaskColorModel(transparentColorIndex);
        BufferedImage img = new BufferedImage(size, size,
                BufferedImage.TYPE_BYTE_INDEXED, icm);
        int mapSize = icm.getMapSize();
        int width = 64 / mapSize;

        WritableRaster wr = img.getRaster();
        for (int i = 0; i < mapSize; i++) {
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < width; x++) {
                    wr.setSample(i * width + x, y, 0, i);
                }
            }
        }
        ImageIO.write(img, "gif", new File(filename));
    }

    protected static IndexColorModel createIndexedBitmaskColorModel(int transparentColorIndex) {
        int paletteSize = 8;
        byte[] red = new byte[paletteSize];
        byte[] green = new byte[paletteSize];
        byte[] blue = new byte[paletteSize];

        red[0] = (byte)0xff; green[0] = (byte)0x00; blue[0] = (byte)0x00; //red
        red[1] = (byte)0x00; green[1] = (byte)0xff; blue[1] = (byte)0x00; //green
        red[2] = (byte)0x00; green[2] = (byte)0x00; blue[2] = (byte)0xff; //blue
        red[3] = (byte)0xff; green[3] = (byte)0xff; blue[3] = (byte)0xff; //white
        red[4] = (byte)0x00; green[4] = (byte)0x00; blue[4] = (byte)0x00; //black
        red[5] = (byte)0x80; green[5] = (byte)0x80; blue[5] = (byte)0x80; //grey
        red[6] = (byte)0xff; green[6] = (byte)0xff; blue[6] = (byte)0x00; //yellow
        red[7] = (byte)0x00; green[7] = (byte)0xff; blue[7] = (byte)0xff; //cyan

        int numBits = 3;

        return new IndexColorModel(numBits, paletteSize,
                red, green, blue, transparentColorIndex);
    }
}

