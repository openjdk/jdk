/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.FlowLayout;
import java.awt.event.ItemListener;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPasswordField;

/*
 * @test
 * @bug 4382819
 * @summary Tests the correctness of color used for the disabled passwordField.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4382819
 */

public class bug4382819 {
    static JCheckBox enabledCheckBox;
    static JPasswordField passwordField;

    private static final String INSTRUCTIONS = """
            Clear the "enabled" checkbox.
            If the JPasswordField's foreground color changes to
            light gray press Pass. If it stays black press Fail.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4382819::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame mainFrame = new JFrame("bug4382819");
        enabledCheckBox = new javax.swing.JCheckBox();
        enabledCheckBox.setSelected(true);
        enabledCheckBox.setText("enabled");
        enabledCheckBox.setActionCommand("enabled");
        mainFrame.add(enabledCheckBox);

        passwordField = new javax.swing.JPasswordField();
        passwordField.setText("blahblahblah");
        mainFrame.add(passwordField);
        SymItem lSymItem = new SymItem();
        enabledCheckBox.addItemListener(lSymItem);

        mainFrame.setSize(300, 100);
        mainFrame.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        return mainFrame;
    }

    static class SymItem implements ItemListener {
        public void itemStateChanged(java.awt.event.ItemEvent event) {
            Object object = event.getSource();
            if (object == enabledCheckBox) {
                passwordField.setEnabled(enabledCheckBox.isSelected());
            }
        }
    }
}

