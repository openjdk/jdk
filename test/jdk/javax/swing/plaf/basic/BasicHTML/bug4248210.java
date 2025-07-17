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

import java.io.File;
import java.io.IOException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;

/*
 * @test
 * @bug 4248210
 * @key headful
 * @summary Tests that HTML in JLabel is painted using LAF-defined
            foreground color
 * @run main bug4248210
 */

public class bug4248210 {
    private static final Color labelColor = Color.red;

    public static void main(String[] args) throws Exception {
        for (UIManager.LookAndFeelInfo laf :
                UIManager.getInstalledLookAndFeels()) {
            if (!(laf.getName().contains("Motif") || laf.getName().contains("GTK"))) {
                System.out.println("Testing LAF: " + laf.getName());
                SwingUtilities.invokeAndWait(() -> test(laf));
            }
        }
    }

    private static void test(UIManager.LookAndFeelInfo laf) {
        setLookAndFeel(laf);
        if (UIManager.getLookAndFeel() instanceof NimbusLookAndFeel) {
            // reset "basic" properties
            UIManager.getDefaults().put("Label.foreground", null);
            // set "synth - nimbus" properties
            UIManager.getDefaults().put("Label[Enabled].textForeground", labelColor);
        } else {
            // reset "synth - nimbus" properties
            UIManager.getDefaults().put("Label[Enabled].textForeground", null);
            // set "basic" properties
            UIManager.getDefaults().put("Label.foreground", labelColor);
        }

        JLabel label = new JLabel("<html><body>Can You Read This?</body></html>");
        label.setSize(150, 30);

        BufferedImage img = paintToImage(label);
        if (!chkImgForegroundColor(img)) {
            try {
                ImageIO.write(img, "png", new File("Label_" + laf.getName() + ".png"));
            } catch (IOException ignored) {}
            throw new RuntimeException("JLabel not painted with LAF defined " +
                    "foreground color");
        }
        System.out.println("Test Passed");
    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported LAF: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static BufferedImage paintToImage(JComponent content) {
        BufferedImage im = new BufferedImage(content.getWidth(), content.getHeight(),
                TYPE_INT_RGB);
        Graphics2D g = (Graphics2D) im.getGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, content.getWidth(), content.getHeight());
        content.paint(g);
        g.dispose();
        return im;
    }

    private static boolean chkImgForegroundColor(BufferedImage img) {
        for (int x = 0; x < img.getWidth(); ++x) {
            for (int y = 0; y < img.getHeight(); ++y) {
                if (img.getRGB(x, y) == labelColor.getRGB()) {
                    return true;
                }
            }
        }
        return false;
    }
}
