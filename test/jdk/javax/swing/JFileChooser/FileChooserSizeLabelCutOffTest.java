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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Robot;

import javax.swing.AbstractButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import java.util.function.Predicate;

/*
 * @test
 * @bug 8301606
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test to check if the Size label in details view,
 * doesn't cut off Metal Look&Feel
 * @run main/manual/othervm -Dsun.java2d.uiScale=2.25 FileChooserSizeLabelCutOffTest
 */

public class FileChooserSizeLabelCutOffTest {
    static JFrame frame;
    static JFileChooser jfc;
    static PassFailJFrame passFailJFrame;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        Robot robot = new Robot();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    initialize();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        robot.delay(500);
        robot.waitForIdle();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                switchToDetailsView();
            }
        });
        robot.delay(500);
        robot.waitForIdle();
        passFailJFrame.awaitAndCheck();
    }

    static void initialize() throws Exception {
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. Verify that the UI scaling is set to 225%.
                2. If Size label is cut off with table
                 header cell, test FAIL else test is PASS.
                """;
        frame = new JFrame("JFileChooser Size Label test");
        jfc = new JFileChooser();
        passFailJFrame = new PassFailJFrame("Test Instructions", INSTRUCTIONS,
                5L, 8, 25);

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.TOP_LEFT_CORNER);

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jfc.setControlButtonsAreShown(false);
        jfc.setDialogType(JFileChooser.CUSTOM_DIALOG);

        frame.add(jfc, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }
    private static void switchToDetailsView() {

        AbstractButton detailsBtn = findDetailsButton(jfc);
        if (detailsBtn == null) {
            throw new Error("'Details' button not found in JFileChooser");
        }
        detailsBtn.doClick();
    }
    private static AbstractButton findDetailsButton(final Container container) {
        Component result = findComponent(container,
                c -> c instanceof JToggleButton button
                        && "Details".equals(button.getToolTipText()));
        return (AbstractButton) result;
    }

    private static JTable findTable(final Container container) {
        Component result = findComponent(container,
                c -> c instanceof JTable);
        return (JTable) result;
    }

    private static Component findComponent(final Container container,
                                           final Predicate<Component> predicate) {
        for (Component child : container.getComponents()) {
            if (predicate.test(child)) {
                return child;
            }
            if (child instanceof Container cont && cont.getComponentCount() > 0) {
                Component result = findComponent(cont, predicate);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
