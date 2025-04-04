/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
   @bug 4522756
   @requires (os.family == "windows")
   @summary To verify that if for the first time JFileChooser is opened,
            the icon for Desktop is not missing.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4522756
 */

import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.UIManager;

public class bug4522756 {
    private static final String INSTRUCTIONS = """
            Click on "Show JFileChooser" button below and verify the following:

            1. If Desktop icon image is present on the Desktop button
               on the left panel of JFileChooser.
            2. Press Desktop button. Check that you actually
               go up to the desktop.

            If the above is true press PASS else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        PassFailJFrame.builder()
                .title("Instructions")
                .instructions(INSTRUCTIONS)
                .rows(12)
                .columns(50)
                .splitUIBottom(bug4522756::createAndShowUI)
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
        p.setSize(200, 200);
        p.add(button);
        return p;
    }
}
