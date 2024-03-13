/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.TextArea;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * @test
 * @bug 8024926 8040279
 * @requires (os.family == "mac")
 * @summary [macosx] AquaIcon HiDPI support
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug8024926
 */
public class bug8024926 {

    private static final String INSTRUCTIONS = """
                Verify that high resolution system icons are used
                 in JOptionPane on HiDPI displays.
                1) Run the test on Retina display or enable the Quartz Debug
                 and select the screen resolution with (HiDPI) label.
                "2) Check that the error icon on the JOptionPane is smooth.
                "If so, press PASS, else press FAIL.""";

    public static void main(String[] args) throws Exception {
         PassFailJFrame.builder()
                .title("AquaIcon HIDPI Instructions")
                .instructions(INSTRUCTIONS)
                .rows(10)
                .columns(35)
                .testUI(bug8024926::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JDialog createTestUI() {
        JOptionPane optionPane = new JOptionPane("High resolution icon test");
        optionPane.setMessage("Icons should have high resolutions");
        optionPane.setMessageType(JOptionPane.ERROR_MESSAGE);
        JDialog dialog = new JDialog();
        dialog.setContentPane(optionPane);
        dialog.pack();
        return dialog;
    }
}
