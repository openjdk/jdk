/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8290399
 * @requires (os.family == "mac")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests if AquaL&F fire actionevent if combobox menu is displayed.
 * @run main/manual JComboBoxActionEvent
 */

import java.awt.FlowLayout;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class JComboBoxActionEvent {
    private static final String instructionsText = " Click the arrow to display the menu.\n" +
         "While the menu is displayed, edit the text to create a new value.\n" +
         "Type return.\n" +
         "If a dialog appears with \"ActionCommand received\"\n" +
         "press Pass, else Fail";

    private static JFrame frame;

    public static void createAndShowGUI() throws Exception {
        SwingUtilities.invokeAndWait(() -> {

            JComboBox<String> comboBox = new JComboBox<>(new String[]
                    { "Apple", "Orange", "Pear" });
            comboBox.setEditable(true);
            comboBox.addActionListener(e -> {
               System.out.println("Action Listener called: " + e.getActionCommand());
               if (e.getActionCommand().contains("comboBoxEdited")) {
                   JOptionPane.showMessageDialog(null, "ActionCommand received");
               }
            });

            FlowLayout layout = new FlowLayout();
            JPanel panel = new JPanel(layout);
            panel.add(comboBox);
            frame = new JFrame("Test Editable Combo Box");
            frame.getContentPane().add(panel);
            frame.setVisible(true);
            frame.pack();
            frame.setLocationRelativeTo(null);

            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame.positionTestWindow(frame,
                    PassFailJFrame.Position.HORIZONTAL);
        });
    }

    public static void main(String[] args) throws Exception {

        UIManager.setLookAndFeel("com.apple.laf.AquaLookAndFeel");

        PassFailJFrame pfjFrame = new PassFailJFrame("JScrollPane "
                + "Test Instructions", instructionsText, 5);

        createAndShowGUI();

        pfjFrame.awaitAndCheck();
    }
}
