/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Insets;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/* @test
 * @bug 4380543
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary setMargin() does not work for AbstractButton
 * @run main/manual bug4380543
 */
public class bug4380543 {
    static TestFrame testObj;
    static String instructions
            = """
            INSTRUCTIONS:
               1. Check if the Left inset(margin) is set visually
                  similar to other three sides around the Radio Button
                  and CheckBox (insets set to 20 on all 4 sides).
               2. Rendering depends on OS and supported Look and Feels.
                  Verify only with those L&F where margins are visible.
               3. If the Left inset(margin) appears too small, press Fail,
                  else press Pass.
            """;
    static PassFailJFrame passFailJFrame;

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeAndWait(() -> {
            try {
                passFailJFrame = new PassFailJFrame(instructions);
                testObj = new TestFrame();
                //Adding the Test Frame to handle dispose
                PassFailJFrame.addTestWindow(testObj);
                PassFailJFrame.positionTestWindow(testObj, PassFailJFrame.Position.HORIZONTAL);
                testObj.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        passFailJFrame.awaitAndCheck();
    }
}

class TestFrame extends JFrame implements ActionListener {
    public TestFrame() {
        initComponents();
    }

    public void initComponents() {
        JPanel p = new JPanel();
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));

        JRadioButton rb  = new JRadioButton("JRadioButton");
        rb.setMargin(new Insets(20, 20, 20, 20));
        rb.setBackground(Color.GREEN);
        rb.setAlignmentX(0.5f);
        buttonsPanel.add(rb);

        JCheckBox cb  = new JCheckBox("JCheckBox");
        cb.setMargin(new Insets(20, 20, 20, 20));
        cb.setBackground(Color.YELLOW);
        cb.setAlignmentX(0.5f);
        buttonsPanel.add(cb);

        getContentPane().add(buttonsPanel);
        UIManager.LookAndFeelInfo[] lookAndFeel = UIManager.getInstalledLookAndFeels();
        for (UIManager.LookAndFeelInfo look : lookAndFeel) {
            JButton btn = new JButton(look.getName());
            btn.setActionCommand(look.getClassName());
            btn.addActionListener(this);
            p.add(btn);
        }

        getContentPane().add(p,BorderLayout.SOUTH);
        setSize(500, 300);
    }

    private static void setLookAndFeel(String laf) {
        try {
            UIManager.setLookAndFeel(laf);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported L&F: " + laf);
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    //Changing the Look and Feel on user selection
    public void actionPerformed(ActionEvent e) {
        setLookAndFeel(e.getActionCommand());
        SwingUtilities.updateComponentTreeUI(this);
    }
}
