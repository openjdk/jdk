/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;

/* @test
 * @bug 8031573 8040279 8143064 8294427
 * @summary  Checkmarks of JCheckBoxMenuItems aren't rendered
 *           in high resolution on Retina
 * @requires (os.family != "linux")
 * @run main/manual bug8031573
 */

public class bug8031573 {

    private static volatile JFrame frame;
    private static volatile boolean passed = false;
    private static final CountDownLatch latch = new CountDownLatch(1);

    public static final String INSTRUCTIONS = "INSTRUCTIONS:\n\n"
            + "Verify that the check and radio-check icons are rendered smoothly\n"
            + "for both JCheckBoxMenuItem and JRadioButtonMenuItem.\n"
            + "1. Open the Menu.\n"
            + "2. Check that the icon on the JCheckBoxMenuItem is smooth.\n"
            + "3. Check that the icon on the JRadioButtonMenuItem is smooth.\n"
            + "4. If you're on Windows:\n"
            + "   Test the markers are still crisp after changing the scale in Windows settings.\n"
            + "   This could be done on same monitor by changing its scale or\n"
            + "   by moving the window to a secondary monitor with a different scale.\n"
            + "   Then go to step 6.\n"
            + "5. If you're on Mac OS:\n"
            + "   If you tested on a Retina display, go to step 6.\n"
            + "6. If both icons render smoothly, press PASS, otherwise press FAIL.\n";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        try {
            SwingUtilities.invokeAndWait(() -> createTestGUI());

            if (!latch.await(5, TimeUnit.MINUTES)) {
                throw new RuntimeException("Test has timed out!");
            }
            if (!passed) {
                throw new RuntimeException("Test failed!");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createTestGUI() {
        frame = new JFrame("bug8031573");
        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("Menu");
        JCheckBoxMenuItem checkBoxMenuItem = new JCheckBoxMenuItem("JCheckBoxMenuItem");
        checkBoxMenuItem.setSelected(true);
        menu.add(checkBoxMenuItem);
        JRadioButtonMenuItem radioButtonMenuItem = new JRadioButtonMenuItem("JRadioButtonMenuItem");
        radioButtonMenuItem.setSelected(true);
        menu.add(radioButtonMenuItem);
        bar.add(menu);
        frame.setJMenuBar(bar);

        JPanel panel = new JPanel(new BorderLayout());
        JTextComponent textComponent = new JTextArea(INSTRUCTIONS);
        textComponent.setEditable(false);
        panel.add(textComponent, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new FlowLayout());
        JButton passButton = new JButton("Pass");
        passButton.addActionListener((e) -> {
            System.out.println("Test passed!");
            passed = true;
            latch.countDown();
        });
        JButton failsButton = new JButton("Fail");
        failsButton.addActionListener((e) -> {
            passed = false;
            latch.countDown();
        });

        buttonsPanel.add(passButton);
        buttonsPanel.add(failsButton);
        panel.add(buttonsPanel, BorderLayout.SOUTH);

        frame.getContentPane().add(panel);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                latch.countDown();
            }
        });
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
