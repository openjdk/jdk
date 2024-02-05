/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.imageio.ImageIO;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.JTableHeader;
import javax.swing.UIManager;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.IOException;

/*
 * @test
 * @bug 8311031
 * @summary Test to validate JTable header border vertical line is
 *          aligned with data grid lines (Metal L&F).
 * @run main TableHeaderBorderPositionTest
 */
public class TableHeaderBorderPositionTest {
    private static final double SCALE = 2.25;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        String[][] data = {
                {"1", "1"}
        };

        String[] columnNames = {"Size", "Size"};

        JTable table = new JTable(data, columnNames);
        Dimension tableSize = table.getPreferredSize();
        table.setSize(tableSize);

        final JTableHeader header = table.getTableHeader();
        Dimension headerSize = header.getPreferredSize();
        header.setSize(headerSize);

        Dimension size = new Dimension(Math.max(headerSize.width, tableSize.width),
                headerSize.height + tableSize.height);

        BufferedImage bufferedImage =
                new BufferedImage((int)Math.ceil(size.width * SCALE),
                        (int)Math.ceil(size.height * SCALE),
                        BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.scale(SCALE, SCALE);

        try {
            header.paint(g2d);
            g2d.translate(0, header.getHeight());
            table.paint(g2d);
        } finally {
            g2d.dispose();
        }

        int verticalLineCol = (int)(table.getTableHeader()
                                         .getColumnModel()
                                         .getColumn(0)
                                         .getWidth() * SCALE) - 2;
        int expectedRGB = bufferedImage.getRGB(verticalLineCol, 0);
        int maxHeight = (int)(((double)table.getTableHeader().getHeight() * SCALE)
                + ((double)table.getRowHeight() * SCALE));

        for (int y = 0; y < maxHeight; y++) {
            for (int x = verticalLineCol; x < verticalLineCol + 3; x++) {
                if (expectedRGB != bufferedImage.getRGB(x, y)) {
                    saveImage(bufferedImage, "failureImage.png");
                    throw new RuntimeException("Test Failed at <" + x + ", " + y + ">");
                }
            }
        }
        System.out.println("Test Passed");
    }

    private static void saveImage(BufferedImage image, String fileName) {
        try {
            ImageIO.write(image, "png", new File(fileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
