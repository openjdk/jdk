/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

/*
 * @test
 * @bug 7093691 8310072
 * @summary Tests if JComboBox has correct font color when disabled/enabled
 * @key headful
 * @run main/othervm -Dsun.java2d.uiScale=1 DisabledComboBoxFontTestAuto
 */

public class DisabledComboBoxFontTestAuto {
    private static JComboBox combo, combo2;
    private static BufferedImage enabledImage, disabledImage, enabledImage2, disabledImage2;
    private static String lafName;
    private static StringBuffer failingLafs;
    private static int COMBO_HEIGHT, COMBO_WIDTH, COMBO2_HEIGHT, COMBO2_WIDTH;

    private static void createCombo() {
        combo = new JComboBox();
        combo.addItem("\u2588".repeat(5));
        combo.setFont(combo.getFont().deriveFont(50.0f));
        combo.setRenderer(new DefaultListCellRenderer());
        combo2 = new JComboBox();
        combo2.addItem("\u2588".repeat(5));
        combo2.setFont(combo2.getFont().deriveFont(50.0f));
        COMBO_WIDTH = (int) combo.getPreferredSize().getWidth();
        COMBO_HEIGHT = (int) combo.getPreferredSize().getHeight();
        COMBO2_WIDTH = (int) combo2.getPreferredSize().getWidth();
        COMBO2_HEIGHT = (int) combo2.getPreferredSize().getHeight();
        combo.setSize(COMBO_WIDTH, COMBO_HEIGHT);
        combo2.setSize(COMBO2_WIDTH, COMBO2_HEIGHT);
    }

    private static void paintCombo() {
        combo.setEnabled(true);
        enabledImage = new BufferedImage(COMBO_WIDTH, COMBO_HEIGHT, TYPE_INT_ARGB);
        Graphics2D graphics2D = enabledImage.createGraphics();
        combo.paint(graphics2D);
        graphics2D.dispose();
        combo.setEnabled(false);
        disabledImage = new BufferedImage(COMBO_WIDTH, COMBO_HEIGHT, TYPE_INT_ARGB);
        graphics2D = disabledImage.createGraphics();
        combo.paint(graphics2D);
        graphics2D.dispose();
        combo2.setEnabled(true);
        enabledImage2 = new BufferedImage(COMBO2_WIDTH, COMBO2_HEIGHT, TYPE_INT_ARGB);
        graphics2D = enabledImage2.createGraphics();
        combo2.paint(graphics2D);
        graphics2D.dispose();
        combo2.setEnabled(false);
        disabledImage2 = new BufferedImage(COMBO2_WIDTH, COMBO2_HEIGHT, TYPE_INT_ARGB);
        graphics2D = disabledImage2.createGraphics();
        combo2.paint(graphics2D);
        graphics2D.dispose();
    }

    private static void testMethod() throws IOException {
        Color eColor1, eColor2, dColor1, dColor2;
        Path testDir = Path.of(System.getProperty("test.classes", "."));

        // Use center line to compare RGB values
        int y = enabledImage.getHeight() / 2;
        for (int x = (enabledImage.getWidth() / 2) - 20;
             x < (enabledImage.getWidth() / 2) + 20; x++) {
            eColor1 = new Color(enabledImage.getRGB(x, y));
            eColor2 = new Color(enabledImage2.getRGB(x, y));
            dColor1 = new Color(disabledImage.getRGB(x, y));
            dColor2 = new Color(disabledImage2.getRGB(x, y));

            if ((!isColorMatching(eColor1, eColor2)) || (!isColorMatching(dColor1, dColor2))) {
                failingLafs.append(lafName + ", ");
                ImageIO.write(enabledImage, "png", new File(testDir
                        + "/" + lafName + "Enabled.png"));
                ImageIO.write(disabledImage, "png", new File(testDir
                        + "/" + lafName + "Disabled.png"));
                ImageIO.write(enabledImage2, "png", new File(testDir
                        + "/" + lafName + "EnabledDLCR.png"));
                ImageIO.write(disabledImage2, "png", new File(testDir
                        + "/" + lafName + "DisabledDLCR.png"));
                return;
            }
        }
        System.out.println("Test Passed: " + lafName);
    }

    private static boolean isColorMatching(Color c1, Color c2) {
        if ((c1.getRed() != c2.getRed())
            || (c1.getBlue() != c2.getBlue())
            || (c1.getGreen() != c2.getGreen())) {
            System.out.println(lafName + " Enabled RGB failure: "
                    + c1.getRed() + ", "
                    + c1.getBlue() + ", "
                    + c1.getGreen() + " vs "
                    + c2.getRed() + ", "
                    + c2.getBlue() + ", "
                    + c2.getGreen());
            return false;
        }
        return true;
    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored){
            System.out.println("Unsupported LookAndFeel: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException |
                IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        lafName = "null";
        failingLafs = new StringBuffer();
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            // Change Motif LAF name to avoid using slash in saved image file path
            lafName = laf.getName().equals("CDE/Motif") ? "Motif" : laf.getName();
            SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
            SwingUtilities.invokeAndWait(DisabledComboBoxFontTestAuto::createCombo);
            SwingUtilities.invokeAndWait(DisabledComboBoxFontTestAuto::paintCombo);
            testMethod();
        }
        if (!failingLafs.isEmpty()) {
            // Remove trailing comma and whitespace
            failingLafs.setLength(failingLafs.length() - 2);
            throw new RuntimeException("FAIL - Enabled and disabled ComboBox " +
                    "does not match in these LAFs: " + failingLafs);
        }
    }
}
