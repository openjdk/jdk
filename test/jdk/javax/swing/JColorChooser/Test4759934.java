/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JFrame;

/*
 * @test
 * @bug 4759934
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests windows activation problem
 * @run main/manual Test4759934
 */
public class Test4759934 {
    private static final String CMD_DIALOG = "Show Dialog"; // NON-NLS: first button
    private static final String CMD_CHOOSER = "Show ColorChooser"; // NON-NLS: second button

    private static JFrame frame;

    private static ActionListener al = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
            String command = event.getActionCommand();
            if (CMD_DIALOG.equals(command)) {
                JDialog dialog = new JDialog(frame, "Dialog"); // NON-NLS: dialog title
                dialog.setLocation(200, 0);
                show(dialog, CMD_CHOOSER, true);
            }
            else if (CMD_CHOOSER.equals(command)) {
                Object source = event.getSource();
                Component component = (source instanceof Component)
                        ? (Component) source
                        : null;

                JColorChooser.showDialog(component, "ColorChooser", Color.BLUE); // NON-NLS: title
            }
        }
    };

    public static void main(String[] args) throws Exception {
        String instructions = "1. Press button \"Show Dialog\" at the frame \"Test\" and\n" +
                "   the dialog with button \"Show ColorChooser\" should appears.\n" +
                "2. Press button \"Show ColorChooser\" at the dialog \"Dialog\" and\n" +
                "   the colorchooser should appears.\n" +
                "3. Press the button \"Cancel\" of colorchooser.\n" +
                "   If the focus will come to the frame \"Test\" then test fails.\n" +
                "   If the focus will come to the dialog \"Dialog\" then test passes.";

        PassFailJFrame.builder()
                .title("Test4759934")
                .instructions(instructions)
                .rows(5)
                .columns(40)
                .testTimeOut(10)
                .testUI(Test4759934::test)
                .build()
                .awaitAndCheck();
    }

    public static JFrame test() {
        frame = new JFrame("ColorChooser dialog on button press test");
        show(frame, CMD_DIALOG, false);
        return frame;
    }

    private static void show(Window window, String command, boolean setVisible) {
        JButton button = new JButton(command);
        button.setActionCommand(command);
        button.addActionListener(al);

        button.setFont(button.getFont().deriveFont(64.0f));

        window.add(button);
        window.pack();
        if (setVisible) {
            window.setVisible(true);
        }
    }
}
