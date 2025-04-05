/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4464774
 * @requires (os.family == "windows")
 * @summary JFileChooser: selection of left-side folder buttons shown incorrectly
            in Windows L&F
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4464774
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.UIManager;

public class bug4464774 {
    private static final String INSTRUCTIONS = """
            Click on "Show JFileChooser" button below to display
            a JFileChooser dialog.
            Click any button from the buttons to the left
            ("Documents", "Desktop", "My Computer" etc.) in FileChooser dialog.
            When the button is toggled, it should be lowered and
            should not have focus painted inside it (black dotted frame).

            If the above is true, press PASS else FAIL.
            """;

    public static void main(String[] argv) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        PassFailJFrame.builder()
                .title("JFileChooser Instructions")
                .instructions(INSTRUCTIONS)
                .rows(10)
                .columns(65)
                .splitUIBottom(bug4464774::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    public static JPanel createAndShowUI() {
        JButton button = new JButton("Show JFileChooser");
        button.addActionListener(e -> {
            JFileChooser jfc = new JFileChooser();
            jfc.showOpenDialog(null);
        });
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        p.setPreferredSize(new Dimension(200, 50));
        p.add(button);
        return p;
    }
}
