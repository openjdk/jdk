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

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.UIManager;

/*
 * @test
 * @bug 4913368
 * @requires (os.family == "linux")
 * @summary Test repainting when entering an empty directory w/ GTK LAF
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual EnterEmptyDirectory
 */

public class EnterEmptyDirectory {

    private static final String INSTRUCTIONS = """
        This test is only for the GTK Look & Feel.

        Step 1:
        Find or create an empty directory. This directory should
        be in a directory with other files and directories, such that
        there are items in both the Folders and Files lists of the
        JFileChooser.

        Step 2:
        Click the "Show JFileChooser" button and enter the empty directory.
        If both lists are correctly repainted such that they are both empty
        (except for the ./ and ../) then the test passes.

        If the contents of the Folders or Files lists are unchanged, test FAILS. """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        PassFailJFrame.builder()
                .title("JFileChooser Instructions")
                .instructions(INSTRUCTIONS)
                .rows(15)
                .columns(40)
                .splitUI(EnterEmptyDirectory::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    public static JPanel createAndShowUI() {
        JButton button = new JButton("Show JFileChooser");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser jfc = new JFileChooser();
                jfc.setMultiSelectionEnabled(true);
                jfc.showOpenDialog(null);
            }
        });
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        p.setSize(200, 200);
        p.add(button);
        return p;
    }
}
