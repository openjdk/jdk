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
 * @bug 7093691
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests if Nimbus JComboBox has correct font
 * @run main/manual DisabledComboBoxFontTest
 */

import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class DisabledComboBoxFontTest {
    private static final String instructionsText = "Pass if you can see two " +
            "editable JComboBoxes and two JLists displayed correctly when " +
            "enabled and disabled. Fail if they don't. Toggle UI enabled " +
            "status with the button on the right of the frame.";

    private static JFrame frame;

    public static void createAndShowGUI() throws InterruptedException,
            InvocationTargetException {
        final int[] lafIndex = {0};

        List<String> lafs = new ArrayList<>();
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            System.out.println(info.getClassName());
            lafs.add(info.getClassName());
        }

        SwingUtilities.invokeAndWait(() -> {

            JComboBox combo = new JComboBox();
            combo.addItem("Simple JComboBox");
            combo.addItem("Simple JComboBox2");
            combo.setEnabled(false);

            JComboBox customCombo = new JComboBox();
            customCombo.setRenderer(new DefaultListCellRenderer());
            customCombo.addItem("JComboBox with DefaultListCellRenderer");
            customCombo.addItem("JComboBox with DefaultListCellRenderer2");
            customCombo.setEnabled(false);

            String[] s = {"one", "two", "three"};
            JList list = new JList(s);
            list.setEnabled(false);
            JList list2 = new JList(s);
            list2.setCellRenderer(new DefaultListCellRenderer());
            list2.setEnabled(false);

            JButton btn = new JButton("Enable/Disable");
            ActionListener actionListener1 = event -> {
                combo.setEnabled(!combo.isEnabled());
                customCombo.setEnabled(!customCombo.isEnabled());
                list.setEnabled(!list.isEnabled());
                list2.setEnabled(!list2.isEnabled());
                String str = event.getActionCommand();
                System.out.println("Clicked = " + str + " " + customCombo.isEnabled());
            };
            btn.addActionListener(actionListener1);

            JButton lafBtn = new JButton ("Cycle L&F");
            ActionListener actionListener2 = event -> {
                if (lafIndex[0] < lafs.size()) {
                    try {
                        System.out.println("Setting L&F to: " + lafs.get(lafIndex[0]));
                        UIManager.setLookAndFeel(lafs.get(lafIndex[0]));
                    }
                    catch (Exception ex) {
                        System.err.println("Failed to set L&F");
                    }
                    SwingUtilities.updateComponentTreeUI(frame);
                    lafIndex[0]++;
                    if (lafIndex[0] >= lafs.size()) {
                        lafIndex[0] = 0;
                    }
                }
            };
            lafBtn.addActionListener(actionListener2);

            FlowLayout layout = new FlowLayout(FlowLayout.LEADING);
            JPanel panel = new JPanel(layout);
            panel.add(combo);
            panel.add(customCombo);
            panel.add(list);
            panel.add(list2);
            panel.add(btn);
            panel.add(lafBtn);
            System.out.println("RENDERER1: " + combo.getRenderer());
            System.out.println("RENDERER2: " + customCombo.getRenderer());
            System.out.println("RENDERER3: " + list.getCellRenderer());
            System.out.println("RENDERER4: " + list2.getCellRenderer());

            frame = new JFrame();
            frame.getContentPane().add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);

            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame.positionTestWindow(frame,
                    PassFailJFrame.Position.HORIZONTAL);

            frame.setVisible(true);
        });
    }

    public static void main(String[] args) throws Exception {

        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            System.err.println("Nimbus L&F not found");
            return;
        }

        PassFailJFrame pfjFrame = new PassFailJFrame("Disabled Nimbus "
                + "CustomComboBox Test Instructions", instructionsText, 5);

        createAndShowGUI();

        pfjFrame.awaitAndCheck();
    }
}