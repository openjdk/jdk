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
 * @test
 * @bug 8016524
 * @requires (os.family=="mac")
 * @key headful
 * @summary Tests whether the bottom line of JTableHeader border is visible for MacOS default LAF
 * @run main JTHeaderBorderTest
 */

import java.awt.Graphics2D;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

public class JTHeaderBorderTest {

    private static JFrame frame;
    private static JTable table;
    private static JScrollPane scrollableTable;

    private static final int FRAME_HT = 300;
    private static final int FRAME_WT = 300;
    private static final int TABLE_COLS = 3;
    private static final int TABLE_ROWS = 2;
    private static final int Y_OFFSET_START = 30;
    private static final int Y_OFFSET_END = 55;
    private static final int X_OFFSET = 25;

    public static void main(String[] args) throws Exception {

        try {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                    | UnsupportedLookAndFeelException e) {
                throw new RuntimeException("Unsupported Look&Feel Class");
            }

            SwingUtilities.invokeAndWait(() -> {
                table = new JTable(TABLE_ROWS, TABLE_COLS);
                scrollableTable = new JScrollPane(table);

                frame = new JFrame();
                frame.getContentPane().add(scrollableTable);
                frame.setSize(FRAME_WT, FRAME_HT);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                // paint JFrame to BufferedImage
                BufferedImage image = new BufferedImage(FRAME_WT, FRAME_HT, TYPE_INT_ARGB);
                Graphics2D graphics2D = image.createGraphics();
                frame.paint(graphics2D);
                graphics2D.dispose();

                int tableColor = table.getBackground().getRGB();
                int headerColor = table.getTableHeader().getBackground().getRGB();
                //at start pixelColor initialized to table header background color
                int pixelColor = headerColor;
                boolean isBottomLineVisible = false;

                // scan table header region to check if bottom border of JTableHeader is visible
                for (int y = Y_OFFSET_START; y <= Y_OFFSET_END; y++) {
                    pixelColor = image.getRGB(X_OFFSET, y);
                    System.out.println("Y offset: "+ y + " Color: "+ (Integer.toHexString(image.getRGB(X_OFFSET, y))));
                    if (pixelColor != tableColor || pixelColor != headerColor) {
                        isBottomLineVisible = true;
                        break;
                    }
                }
                // throw Runtime Exception if border is not visible in the scanned region
                if (!isBottomLineVisible) {
                    saveImage(image, "JTableHeader.png");
                    throw new RuntimeException("JTableHeader Bottom Border not visible");
                }
            });
        } finally {
            if (frame != null) {
                SwingUtilities.invokeAndWait(()-> frame.dispose());
            }
        }
    }
    // to save the BufferedImage as .png in the event the test fails (for debugging purpose)
    private static void saveImage(BufferedImage image, String filename) {
        try {
            ImageIO.write(image, "png", new File(filename));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
