/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4174551
 * @summary JOptionPane should allow custom buttons
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4174551
 */
import java.awt.Font;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

public class bug4174551 {
    private static final String INSTRUCTIONS = """
          Message Dialog should pop up with a button font size 10
          and message font size 24.
          It should be true even on OS X.
          If it is not so press "Fail" else press "Pass". """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("JOptionPane Instructions")
            .instructions(INSTRUCTIONS)
            .rows(5)
            .columns(35)
            .testUI(() -> createTestUI())
            .build()
            .awaitAndCheck();
    }

    private static JDialog createTestUI() {
        UIManager.getDefaults().put("OptionPane.buttonFont", new Font("Dialog", Font.PLAIN, 10));
        UIManager.getDefaults().put("OptionPane.messageFont", new Font("Dialog", Font.PLAIN, 24));
        JOptionPane optionPane = new JOptionPane();
        optionPane.setMessage("HI 24!");
        optionPane.setMessageType(JOptionPane.INFORMATION_MESSAGE);
        JDialog dialog = new JDialog();
        dialog.setContentPane(optionPane);
        dialog.pack();

        System.out.println(UIManager.getDefaults().get("OptionPane.buttonFont"));
        System.out.println(UIManager.getDefaults().get("OptionPane.messageFont"));
        return dialog;
    }
}
