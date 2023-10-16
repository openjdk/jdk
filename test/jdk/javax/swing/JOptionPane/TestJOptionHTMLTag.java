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
/* @test
 * @bug 5074006
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Swing JOptionPane shows <html> tag as a string after newline
 * @run main/manual TestJOptionHTMLTag
*/

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class TestJOptionHTMLTag {
    static String instructions
            = """
            INSTRUCTIONS:
                A dialog will be shown.
                If it does not contain </html> string, press Pass else press Fail.
            """;
    static PassFailJFrame passFailJFrame;

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeAndWait(() -> {
            try {
                String message = "<html>" + "This is a test\n" + "</html>";
                JOptionPane optionPane = new JOptionPane();
                optionPane.setMessage(message);
                optionPane.setMessageType(JOptionPane.INFORMATION_MESSAGE);
                JDialog dialog = new JDialog();
                dialog.setContentPane(optionPane);
                dialog.pack();
                dialog.setVisible(true);

                passFailJFrame = new PassFailJFrame(instructions);
                PassFailJFrame.addTestWindow(dialog);
                PassFailJFrame.positionTestWindow(dialog, PassFailJFrame.Position.HORIZONTAL);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        passFailJFrame.awaitAndCheck();
    }
}

