/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @requires (os.family == "mac")
 * @bug 8268084
 * @summary  Verifies disabled JMenuItem arrow and check is disabled
 * @run main/manual JMenuItemDisableArrowButtonTest
 */
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.awt.Color;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class JMenuItemDisableArrowButtonTest {

    private static JFrame frame;
    private static volatile CountDownLatch countDownLatch;
    private static volatile boolean testResult;

    private static final String INSTRUCTIONS = "INSTRUCTIONS:\n\n"
            + "Click on \"SubMenuTest\" menu.\n "
            + "If in 1st menuitem\n"
            + "arrow icon is disabled along with \"Submenu\" menuitem\n"
            + "and in 2nd menuitem\n"
            + " If selected arrow icon is disabled along with \"Submenu\" menuitem\n"
            + "and in 3rd menuitem\n"
            + "If checkmark icon is disabled along with \"Submenu\" CheckBox menuitem\n"
            + "then press Pass \n"
            + "otherwise if arrow or checkmark icon is not disabled, press Fail.";

    public static void main(String args[]) throws Exception{
        countDownLatch = new CountDownLatch(1);

        SwingUtilities.invokeAndWait(JMenuItemDisableArrowButtonTest::createUI);
        countDownLatch.await(5, TimeUnit.MINUTES);

        if (!testResult) {
            throw new RuntimeException(
                    "Disabled JMenuItem arrow or checkmark icon is not disabled!");
        }
    }

    private static void createUI() {
        try {
            UIManager.setLookAndFeel("com.apple.laf.AquaLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException("Cannot initialize Aqua L&F");
        }

        JFrame mainFrame = new JFrame();
        GridBagLayout layout = new GridBagLayout();
        JPanel mainControlPanel = new JPanel(layout);
        JPanel resultButtonPanel = new JPanel(layout);

        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(5, 15, 5, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainControlPanel.add(createComponent(), gbc);

        JTextArea instructionTextArea = new JTextArea();
        instructionTextArea.setText(INSTRUCTIONS);
        instructionTextArea.setEditable(false);
        instructionTextArea.setBackground(Color.white);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainControlPanel.add(instructionTextArea, gbc);

        JButton passButton = new JButton("Pass");
        passButton.setActionCommand("Pass");
        passButton.addActionListener((ActionEvent e) -> {
            testResult = true;
            mainFrame.dispose();
            countDownLatch.countDown();

        });

        JButton failButton = new JButton("Fail");
        failButton.setActionCommand("Fail");
        failButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mainFrame.dispose();
                countDownLatch.countDown();
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 0;

        resultButtonPanel.add(passButton, gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        resultButtonPanel.add(failButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        mainControlPanel.add(resultButtonPanel, gbc);

        mainFrame.add(mainControlPanel);
        mainFrame.pack();

        mainFrame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                mainFrame.dispose();
                countDownLatch.countDown();
            }
        });
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }

    private static JComponent createComponent() {
        final JMenuBar menuBar = new JMenuBar();
        JMenu subMenuTestmenu = new JMenu("SubMenuTest");
        JMenu disabledSubmenu = new JMenu("Submenu");
        disabledSubmenu.setEnabled(false);

        JMenu disabledSubmenu1 = new JMenu("Submenu");
        disabledSubmenu1.setSelected(true);
        disabledSubmenu1.setEnabled(false);
        subMenuTestmenu.add(disabledSubmenu);
        subMenuTestmenu.add(disabledSubmenu1);

        JCheckBoxMenuItem myItem = new JCheckBoxMenuItem("Submenu");
        myItem.setSelected(true);
        myItem.setEnabled(false);
        subMenuTestmenu.add(myItem);

        menuBar.add(subMenuTestmenu);
        return menuBar;
    }
}

