/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4945795
 * @summary With mnemonic hiding turned on, Java does not display all mnemonics with ALT key
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4945795
 * */

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.UIManager;

public class bug4945795 {

    static final String INSTRUCTIONS = """
        This test is for the Swing Windows Look And Feel.
        A test window will be displayed with the label 'Mnemonic Test'
        Click the mouse in the test window to make sure it has keyboard focus.
        Now press and hold the 'Alt' key.
        An underline should be displayed below the initial 'M' character.
        If it is press PASS, else press FAIL.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(40)
            .testUI(bug4945795::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame mainFrame = new JFrame("Bug4945795");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            throw new RuntimeException("Can not set system look and feel");
        }
        mainFrame.setLayout(new BorderLayout());
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JLabel label = new JLabel("Mnemonic test");
        label.setDisplayedMnemonic('M');
        mainFrame.add(label, BorderLayout.NORTH);
        mainFrame.setSize(400, 300);
        return mainFrame;
    }
}
