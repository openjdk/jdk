/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5107379
 * @summary Component orientation in JOptionPane is not proper in Motif L&F.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestIconRTL
 */

import java.awt.ComponentOrientation;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TestIconRTL {

    private static JFrame frame;
    private static JOptionPane pane;

    static final String INSTRUCTIONS = """
        A JOptionPane is shown in Motif LookAndFeel with "Orientation" menu.
        Click on "Orientation" menu and
        test with "Left To Right" and "Right to Left" Orientation
        If JOptionPane is drawn properly in different orientation,
        then test passed, otherwise it failed.""";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        PassFailJFrame.builder()
                .title("TestIconRTL Instructions")
                .instructions(INSTRUCTIONS)
                .columns(60)
                .testUI(TestIconRTL::createTestUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createTestUI() {
        pane = new JOptionPane(new String("Testing CCC4265463"),
                JOptionPane.INFORMATION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION);
        pane.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        pane.setVisible(true);

        frame = new JFrame("TestIconRTL");

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(getOrientationJMenu());

        frame.setJMenuBar(menuBar);

        frame.getContentPane().add(pane);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        return frame;
    }

    public static void test() throws Exception {
        AtomicBoolean leftToRightOrientationFlag = new AtomicBoolean(false);
        SwingUtilities.invokeAndWait(() -> leftToRightOrientationFlag.set(pane.getComponentOrientation().isLeftToRight()));
        if (leftToRightOrientationFlag.get()) {
            System.out.println("LTR LOCATION ...");
        } else {
            System.out.println("RTL LOCATION ...");
        }
    }

    private static JMenu getOrientationJMenu() {
        JMenu lafMenu = new JMenu("Orientation");
        JMenuItem leftToRight = new JMenuItem("Left to Right");
        leftToRight.addActionListener(e -> {
            pane.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            pane.invalidate();
            SwingUtilities.updateComponentTreeUI(frame);
        });

        JMenuItem rightToLeft = new JMenuItem("Right to Left");
        rightToLeft.addActionListener(e -> {
            pane.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            pane.invalidate();
            SwingUtilities.updateComponentTreeUI(frame);
        });

        pane.invalidate();
        lafMenu.add(leftToRight);
        lafMenu.add(rightToLeft);
        return lafMenu;
    }


}
