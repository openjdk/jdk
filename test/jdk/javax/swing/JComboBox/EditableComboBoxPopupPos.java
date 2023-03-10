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

/*
 * @test
 * @key headful
 * @bug 8302558
 * @summary Tests if the Popup from an editable ComboBox with a border
 *          is in the correct position
 * @run main EditableComboBoxPopupPos
 */

import java.awt.AWTException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class EditableComboBoxPopupPos {
    private static Robot robot;
    private static JFrame frame;
    private static JPanel panel;
    private static JComboBox cb1, cb2;
    private static String lafName, cb1Str, cb2Str;
    private static Point cb1Point, cb2Point;
    private static int cb1Width, cb1Height, cb2Width, cb2Height;

    private static Path testDir;
    private static BufferedImage image1, image2;

    private static final int BUTTON_OFFSET = 8;
    private static final int POPUP_OFFSET = 6;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException, IOException {
        robot = new Robot();
        robot.setAutoDelay(100);
        robot.setAutoWaitForIdle(true);
        testDir = Path.of(System.getProperty("test.classes", "."));

        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
        lafName = laf.getName();
        SwingUtilities.invokeAndWait(() -> {
            setLookAndFeel(laf);
            panel = new JPanel();
            String[] comboStrings = {"One", "Two", "Three"};
            cb1 = new JComboBox(comboStrings);
            cb1.setEditable(true);
            cb1.setBorder(BorderFactory.createTitledBorder(
                    "Editable JComboBox"));

            cb2 = new JComboBox(comboStrings);
            cb2.setEditable(true);

            panel.add(cb1);
            panel.add(cb2);

            // Change starting selection to check if the position of the
            // first selection item is in the correct position on screen.
            cb1.setSelectedIndex(1);
            cb2.setSelectedIndex(1);

            frame = new JFrame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        robot.delay(1000);
        robot.waitForIdle();

        SwingUtilities.invokeAndWait(() -> {
            cb1Point = cb1.getLocationOnScreen();
            cb1Width = cb1.getWidth();
            cb1Height = cb1.getHeight();
            cb2Point = cb2.getLocationOnScreen();
            cb2Width = cb2.getWidth();
            cb2Height = cb2.getHeight();
        });

        runTestOnComboBox(cb1Point, cb1Width, cb1Height);
        runTestOnComboBox(cb2Point, cb2Width, cb2Height);

        SwingUtilities.invokeAndWait(() -> {
            cb1Str = cb1.getSelectedItem().toString();
            cb2Str = cb2.getSelectedItem().toString();
        });

        checkSelection(cb1Str, cb2Str);

        SwingUtilities.invokeAndWait(() -> frame.dispose());
        }
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

    private static void runTestOnComboBox(Point p, int width, int height) throws IOException {
        robot.mouseMove(p.x + width - BUTTON_OFFSET, p.y + (height / 2) + POPUP_OFFSET);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        image1 = robot.createScreenCapture(screenRect);
        ImageIO.write(image1, "png", new File(testDir + "/image1.png"));

        robot.mouseMove(p.x + (width / 2) - BUTTON_OFFSET,
                p.y + height + POPUP_OFFSET);

        image2 = robot.createScreenCapture(screenRect);
        ImageIO.write(image2, "png", new File(testDir + "/image2.png"));

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void checkSelection(String s1, String s2) {
        if (s1.equals("One") && s2.equals("One")) {
            System.out.println(lafName + " Passed");
        } else {
            throw new RuntimeException(lafName + " Failed");
        }
    }
}
