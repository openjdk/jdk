/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static jdk.test.lib.Platform.isWindows;

/*
 * @test
 * @bug 6415057
 * @summary Tests if toplevel's icons are updated in runtime
 * @key headful
 * @requires (os.family == "windows")
 * @modules java.desktop/sun.awt
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jdk.test.lib.Platform
 * @run main/manual IconChangingTest
 */

public class IconChangingTest {
    private static final int ICON_SIZE = 16;
    private static final int MARGIN = 2;
    private static final int STACK_SIZE = 4;
    // Number of windows per stack
    private static final int WIN_PER_STACK = 4;
    private static int windowPosX = 0;

    private static final int EXTRA_OFFSET = 50;

    private static ImageIcon ii1;
    private static ImageIcon ii2;
    private static ImageIcon ji;

    private static final Window[][] windowStack = new Window[STACK_SIZE][WIN_PER_STACK];
    private static final JLabel[][] labels = new JLabel[STACK_SIZE][WIN_PER_STACK];
    private static final boolean[][] isResizable = new boolean[][]{
            {true, true, false, true},   //stack 1
            {true, false, true, false},  //stack 2
            {true, false, true, true},   //stack 3
            {false, true, false, false}  //stack 4
    };

    private static final String INSTRUCTIONS = """
            The test is supposed to work on Windows.
            It may not work on other platforms.

            Icons and window decorations should change in windows
            (frames & dialogs) every 3 seconds.

            Notes:

              * Icons might appear in grayscale.
              * Default icon might be either Duke or Java Cup.

            Press PASS if the icons match the labels
            and are shown correctly, FAIL otherwise.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame passFailJFrame = new PassFailJFrame("Icon Changing " +
                "Test Instructions", INSTRUCTIONS, 5, 18, 40);
        SwingUtilities.invokeAndWait(() -> {
            try {
                createAndShowGUI();
            } catch (Exception e) {
                throw new RuntimeException("Error while running the test", e);
            }
        });
        passFailJFrame.awaitAndCheck();
    }

    private static void createAndShowGUI() throws InterruptedException,
                                                  InvocationTargetException {
        PassFailJFrame.positionTestWindow(null,
                PassFailJFrame.Position.TOP_LEFT_CORNER);
        Rectangle bounds = PassFailJFrame.getInstructionFrameBounds();
        windowPosX = bounds.x + bounds.width;

        ii1 = new ImageIcon(generateIcon(Color.RED));
        ii2 = new ImageIcon(generateIcon(Color.BLUE));
        ji = new ImageIcon(IconChangingTest.class.getResource("java-icon16.png"));

        // Creates STACK_SIZE different combinations of window stacks,
        // each stack contains WIN_PER_STACK windows (frame/dialog).
        for (int i = 0; i < STACK_SIZE; i++) {
            for (int j = 0; j < WIN_PER_STACK; j++) {
                createWindow(i, j);
            }
        }

        Thread thread = new Thread(new Runnable() {
            private final ImageIcon[][] icons = {
                    {null, ii1},
                    {ii2, null},
                    {ii1, ii2}
            };

            @Override
            public void run() {
                int index = 0;
                while (true) {
                    try {
                        setIcons(icons[index][0], icons[index][1]);
                        Thread.sleep(4000);
                        if (++index >= icons.length) {
                            index = 0;
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

            private static void setIcons(final ImageIcon icon1, final ImageIcon icon2) {
                Image i1 = (icon1 == null) ? null : icon1.getImage();
                Image i2 = (icon2 == null) ? null : icon2.getImage();
                ImageIcon li1 = (icon1 == null) ? ji : icon1;
                ImageIcon li2 = (icon2 == null) ? li1 : icon2;

                ImageIcon[][] iconList = new ImageIcon[][]{
                    {li1, li1, ((i2 == null && isWindows()) ? null : li2), li2},
                    {li1, (isWindows()) ? null : li1, li2, (isWindows()) ? null : li2},
                    {li1, (isWindows()) ? null : li1, li2, li2},
                    {li1, li1, (i2 == null && isWindows()) ? null : li2, (isWindows()) ? null : li2},
                };

                for (int i = 0; i < STACK_SIZE; i++) {
                    windowStack[i][0].setIconImage(i1);
                    windowStack[i][2].setIconImage(i2);
                    for (int j = 0; j < WIN_PER_STACK; j++) {
                        labels[i][j].setIcon(iconList[i][j]);
                    }
                }
            }
        });
        thread.start();
    }

    private static void createWindow(int i, int j) {
        boolean isFrame = (i == 0 && j == 0) || (i == 1 && j == 0);
        String title = (isFrame ? "Frame ": "Dialog ") + (i+1) + "." + (j+1);

        windowStack[i][j] = isFrame
                            ? createFrame(title, i, j)
                            : createDialog(title, i, j);

        labels[i][j]= new JLabel(title);
        windowStack[i][j].add(labels[i][j]);
        windowStack[i][j].setBounds(windowPosX + (i * 200), (j * 100) + EXTRA_OFFSET,
                             200, 100);
        windowStack[i][j].toFront();
        windowStack[i][j].setVisible(true);

        PassFailJFrame.addTestWindow(windowStack[i][j]);
    }

    private static Frame createFrame(String title, int i, int j) {
        Frame frame = new Frame(title);
        frame.setResizable(isResizable[i][j]);
        return frame;
    }

    private static Dialog createDialog(String title, int i, int j) {
        Dialog dialog = new Dialog((j == 0 ? null : windowStack[i][j-1]), title);
        dialog.setResizable(isResizable[i][j]);
        return dialog;
    }

    private static Image generateIcon(Color color) {
        BufferedImage bImg = new BufferedImage(ICON_SIZE, ICON_SIZE, TYPE_INT_ARGB);
        Graphics2D g2d = bImg.createGraphics();
        g2d.setColor(color);
        g2d.fillRect(MARGIN, MARGIN, ICON_SIZE - 2 * MARGIN, ICON_SIZE - 2 * MARGIN);
        g2d.dispose();
        return bImg;
    }
}
