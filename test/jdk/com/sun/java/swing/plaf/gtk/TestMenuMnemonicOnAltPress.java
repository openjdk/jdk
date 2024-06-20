/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8326458
 * @key headful
 * @requires (os.family == "linux")
 * @library /javax/swing/regtesthelpers
 * @build Util
 * @summary Verifies if menu mnemonic toggle on Alt press in GTK LAF
 * @run main TestMenuMnemonicOnAltPress
 */

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.synth.SynthLookAndFeel;

public class TestMenuMnemonicOnAltPress {

    private static JFrame frame;
    private static JMenu fileMenu;
    private static volatile Point pt;
    private static volatile int fileMenuWidth;
    private static volatile int fileMenuHeight;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        Robot robot = new Robot();
        robot.setAutoDelay(200);

        try {
            SwingUtilities.invokeAndWait(TestMenuMnemonicOnAltPress::createAndShowUI);
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                pt = fileMenu.getLocationOnScreen();
                fileMenuWidth = fileMenu.getWidth();
                fileMenuHeight = fileMenu.getHeight();
            });

            robot.keyPress(KeyEvent.VK_ALT);
            robot.waitForIdle();

            BufferedImage img1 = robot.createScreenCapture(new Rectangle(pt.x, pt.y,
                    fileMenuWidth, fileMenuHeight));

            robot.keyRelease(KeyEvent.VK_ALT);
            robot.waitForIdle();

            BufferedImage img2 = robot.createScreenCapture(new Rectangle(pt.x, pt.y,
                    fileMenuWidth, fileMenuHeight));

            if (Util.compareBufferedImages(img1, img2)) {
                try {
                    ImageIO.write(img1, "png", new File("img1.png"));
                    ImageIO.write(img2, "png", new File("img2.png"));
                } catch (IOException ignored) {}
                throw new RuntimeException("Mismatch in mnemonic show/hide on Alt press");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("Test Menu Mnemonic Show/Hide");
        JMenuBar menuBar  = new JMenuBar();
        fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        JMenuItem item1 = new JMenuItem("Item-1");
        JMenuItem item2 = new JMenuItem("Item-2");
        fileMenu.add(item1);
        fileMenu.add(item2);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);
        frame.setSize(250, 200);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
