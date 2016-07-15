/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 8057791
   @summary Selection in JList is drawn with wrong colors in Nimbus L&F
   @author Anton Litvinov
   @run main bug8057791
 */

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

public class bug8057791 {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new NimbusLookAndFeel());

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    final int listWidth = 50;
                    final int listHeight = 50;
                    final int selCellIndex = 0;

                    JList<String> list = new JList<String>();
                    list.setSize(listWidth, listHeight);
                    DefaultListModel<String> listModel = new DefaultListModel<String>();
                    listModel.add(selCellIndex, "E");
                    list.setModel(listModel);
                    list.setSelectedIndex(selCellIndex);

                    BufferedImage img = new BufferedImage(listWidth, listHeight,
                        BufferedImage.TYPE_INT_ARGB);
                    Graphics g = img.getGraphics();
                    list.paint(g);
                    g.dispose();

                    Rectangle cellRect = list.getCellBounds(selCellIndex, selCellIndex);
                    HashSet<Color> cellColors = new HashSet<Color>();
                    int uniqueColorIndex = 0;
                    for (int x = cellRect.x; x < (cellRect.x + cellRect.width); x++) {
                        for (int y = cellRect.y; y < (cellRect.y + cellRect.height); y++) {
                            Color cellColor = new Color(img.getRGB(x, y), true);
                            if (cellColors.add(cellColor)) {
                                System.err.println(String.format("Cell color #%d: %s",
                                    uniqueColorIndex++, cellColor));
                            }
                        }
                    }

                    Color selForegroundColor = list.getSelectionForeground();
                    Color selBackgroundColor = list.getSelectionBackground();
                    if (!cellColors.contains(new Color(selForegroundColor.getRGB(), true))) {
                        throw new RuntimeException(String.format(
                            "Selected cell is drawn without selection foreground color '%s'.",
                            selForegroundColor));
                    }
                    if (!cellColors.contains(new Color(selBackgroundColor.getRGB(), true))) {
                        throw new RuntimeException(String.format(
                            "Selected cell is drawn without selection background color '%s'.",
                            selBackgroundColor));
                    }
                }
            });
        } catch (UnsupportedLookAndFeelException | InterruptedException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
