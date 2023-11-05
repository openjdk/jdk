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
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static jdk.test.lib.Platform.isWindows;

/*
 * @test
 * @bug 6233560 6280303 6292933
 * @summary Tests if toplevel's icons are shown correctly
 * @key headful
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jdk.test.lib.Platform
 * @run main/manual IconShowingTest
 */

public class IconShowingTest {
    private static final int EXTRA_OFFSET = 50;

    private static final String INSTRUCTIONS =
            "Look at the icons shown on frames and dialogs, icons of minimized frames\n"
            + (isWindows() ? "are displayed in ALT+TAB window\n" : "") + "\n"+
            """
            Alpha-channel (transparency) should be supported
            by Windows and may not be supported by other platforms.

            Notes:
              * Icons might appear in grayscale.
              * Default icon might be either Duke or Java Cup.

            Press PASS if the icons match label description in windows
            and are shown correctly, FAIL otherwise.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame passFailJFrame = new PassFailJFrame("Icon Showing " +
                "Test Instructions", INSTRUCTIONS, 5, 18, 48);
        SwingUtilities.invokeAndWait(() -> {
            try {
                createAndShowGUI();
            } catch (Exception e) {
               throw new RuntimeException("Error while running the test", e);
            }
        });
        passFailJFrame.awaitAndCheck();
    }

    public static void createAndShowGUI()
            throws InterruptedException, InvocationTargetException {
        Image i_16 = createIcon(16, 8, "16");
        Image i_32 = createIcon(32, 14, "32");
        Image i_48 = createIcon(48, 24, "48");
        Image i_64 = createIcon(64, 30, "64");

        ImageIcon ji_16 = new ImageIcon(IconShowingTest.class.getResource(
                "java-icon16.png"));

        Image[] images = new Image[] {i_16, i_32, i_48, i_64};
        List<Image> imageList = Arrays.asList(images);
        ImageIcon icon = new ImageIcon(new MRImage(images));

        Frame f1 = new Frame("Frame 1");
        f1.setIconImages(imageList);
        f1.setLayout(new GridLayout(0, 1));

        f1.add(new JLabel("Icon 16x16", new ImageIcon(i_16), JLabel.CENTER));
        f1.add(new JLabel("Icon 32x32", new ImageIcon(i_32), JLabel.CENTER));
        f1.add(new JLabel("Icon 48x48", new ImageIcon(i_48), JLabel.CENTER));
        f1.add(new JLabel("Icon 64x64", new ImageIcon(i_64), JLabel.CENTER));

        PassFailJFrame.positionTestWindow(null,
                PassFailJFrame.Position.TOP_LEFT_CORNER);
        Rectangle bounds = PassFailJFrame.getInstructionFrameBounds();

        int windowPosX = bounds.x + bounds.width + 5;
        f1.setBounds(windowPosX, EXTRA_OFFSET, 200, 300);
        f1.setVisible(true);
        f1.toFront();
        PassFailJFrame.addTestWindow(f1);
        int windowPosY = f1.getY() + f1.getHeight();


        Dialog d11 = new Dialog(f1, "Dialog 1.1");
        d11.setResizable(false);
        addIconAndLabelToWindow(d11, windowPosX, windowPosY - EXTRA_OFFSET,
                (isWindows() ? "No icon, non-resizable dialog"
                             : "Inherited icon, non-resizable dialog"),
                (isWindows() ? null : icon));

        Dialog d12 = new Dialog(d11, "Dialog 1.2");
        addIconAndLabelToWindow(d12, windowPosX, windowPosY + EXTRA_OFFSET,
                "Inherited icon, resizable dialog", icon);

        Frame f2 = new Frame("Frame 2");
        addIconAndLabelToWindow(f2, windowPosX + 200, 0,
                "Default Icon", ji_16);

        Dialog d21 = new Dialog(f2, "Dialog 2.1");
        d21.setResizable(false);
        addIconAndLabelToWindow(d21, windowPosX + 200, 100,
                (isWindows() ? "No icon, non-resizable dialog"
                             : "Inherited default Icon, non-resizable dialog"),
                (isWindows() ? null : ji_16));

        Dialog d22 = new Dialog(f2, "Dialog 2.2");
        addIconAndLabelToWindow(d22, windowPosX + 200, 200,
                "Inherited default Icon, resizable dialog", ji_16);

        Dialog d23 = new Dialog(f2, "Dialog 2.3");
        d23.setIconImages(imageList);
        d23.setResizable(false);
        addIconAndLabelToWindow(d23, windowPosX + 200, 300,
                "Modified Icon, non-resizable dialog", icon);

        Dialog d24 = new Dialog(f2, "Dialog 2.4");
        d24.setIconImages(imageList);
        addIconAndLabelToWindow(d24, windowPosX + 200, 400,
                "Modified Icon, resizable dialog", icon);

        Dialog d31 = new Dialog((Frame)null, "Dialog 3.1");
        addIconAndLabelToWindow(d31, windowPosX + 400, 100,
                "Default icon, resizable dialog", ji_16);

        Dialog d32 = new Dialog(d31, "Dialog 3.2");
        d32.setResizable(false);
        addIconAndLabelToWindow(d32, windowPosX + 400, 200,
                (isWindows() ? "No icon, non-resizable dialog"
                             : "Default Icon, non-resizable dialog"),
                (isWindows() ? null : ji_16));

        Dialog d33 = new Dialog(d31, "Dialog 3.3");
        d33.setIconImages(imageList);
        d33.setResizable(false);
        addIconAndLabelToWindow(d33, windowPosX + 400, 300,
                "Modified icon, non-resizable dialog", icon);


        Dialog d34 = new Dialog(d33, "Dialog 3.4");
        d34.setResizable(false);
        addIconAndLabelToWindow(d34, windowPosX + 400, 400,
                (isWindows() ? "No icon, non-resizable dialog"
                             : "Inherited modified icon, non-resizable dialog"),
                (isWindows() ? null : icon));


        Dialog d41 = new Dialog((Frame) null, "Dialog 4.1");
        d41.setResizable(false);
        addIconAndLabelToWindow(d41, windowPosX + 600, 100,
                "Default icon, non-resizable dialog", ji_16);


        Dialog d42 = new Dialog(d41, "Dialog 4.2");
        addIconAndLabelToWindow(d42, windowPosX + 600, 200,
                "Inherited default icon, resizable dialog", ji_16);

        Dialog d43 = new Dialog(d41, "Dialog 4.3");
        d43.setIconImages(imageList);
        addIconAndLabelToWindow(d43, windowPosX + 600, 300,
                "Modified icon, resizable dialog", icon);

        Dialog d44 = new Dialog(d43, "Dialog 4.4");
        addIconAndLabelToWindow(d44, windowPosX + 600, 400,
                "Inherited modified icon, resizable dialog", icon);
    }

    private static void addIconAndLabelToWindow(Window win, int x, int y,
                                                String title, ImageIcon icon) {
        win.setBounds(x, (y + EXTRA_OFFSET), 200, 100);
        win.add(new JLabel(title, icon, JLabel.CENTER));
        win.setVisible(true);
        win.toFront();
        PassFailJFrame.addTestWindow(win);
    }

    public static Image createIcon(int size, int fontSize, String value) {
        BufferedImage bImg = new BufferedImage(size, size, TYPE_INT_ARGB);
        Graphics2D g2d = bImg.createGraphics();

        int half = size / 2;
        for (int i = 0; i < half - 1; i += 2) {
            g2d.setComposite(AlphaComposite.Src);
            g2d.setColor(Color.RED);
            g2d.fillRect(0, i, half, 1);
            g2d.setComposite(AlphaComposite.Clear);
            g2d.fillRect(0, i + 1, half, 1);
        }
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(half, 0, half, half);
        g2d.setComposite(AlphaComposite.Src);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setFont(new Font("Dialog", Font.PLAIN, fontSize));
        g2d.setColor(Color.BLUE);
        g2d.drawString(value, half - 1, half - 2);

        int height = (half + 1) / 3;
        // Green
        GradientPaint greenGradient = new GradientPaint(0, half - 1, Color.GREEN,
                size, half - 1, new Color(0, 255, 0, 0));
        g2d.setPaint(greenGradient);
        g2d.fillRect(0, half - 1, size, height);

        // Blue
        GradientPaint blueGradient = new GradientPaint(0, (half - 1) + height, Color.BLUE,
                size, (half - 1) + height, new Color(0, 0, 255, 0));
        g2d.setPaint(blueGradient);
        g2d.fillRect(0, (half - 1) + height, size, height);

        // Red
        GradientPaint redGradient = new GradientPaint(0, (half - 1) + height * 2, Color.RED,
                size, (half - 1) + height * 2, new Color(255, 0, 0, 0));
        g2d.setPaint(redGradient);
        g2d.fillRect(0, (half - 1) + height * 2, size, height);
        g2d.dispose();

        return bImg;
    }

    private static class MRImage extends BaseMultiResolutionImage {
        public MRImage(Image... resolutionVariants) {
            super(resolutionVariants);
        }

        @Override
        public Image getResolutionVariant(double expectedSize, double unused) {
            final int size = (int) Math.round(expectedSize / 16.0) * 16;
            List<Image> imageList = getResolutionVariants();
            for (int i = 0; i < imageList.size(); i++) {
                if (size == imageList.get(i).getWidth(null)) {
                    return imageList.get(i);
                } else if (imageList.get(i).getWidth(null) > size) {
                    return imageList.get(i > 0 ? i - 1 : i);
                }
            }
            return imageList.get(0); //default/base image
        }
    }
}
