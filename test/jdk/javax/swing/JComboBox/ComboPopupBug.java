/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;

/*
 * @test
 * @bug 8322754
 * @summary Verifies clicking JComboBox during frame closure causes Exception
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ComboPopupBug
 */

public class ComboPopupBug {
    private static final String instructionsText = """
            This test is used to verify that clicking on JComboBox
            when frame containing it is about to close should not
            cause IllegalStateException.

            A JComboBox is shown with Close button at the bottom.
            Click on Close and then click on JComboBox arrow button
            to try to show combobox popup.
            If IllegalStateException is thrown, test will automatically Fail
            otherwise click Pass.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("ComboPopup Instructions")
                .instructions(instructionsText)
                .testTimeOut(5)
                .rows(10)
                .columns(35)
                .testUI(ComboPopupBug::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JFrame frame = new JFrame("ComboPopup");

        JComboBox<String> cb = new JComboBox<>();
        cb.setEditable(true);
        cb.addItem("test");
        cb.addItem("test2");
        cb.addItem("test3");

        JButton b = new JButton("Close");
        b.addActionListener(
                (e)->{
                    try {
                        Thread.sleep(3000);
                    } catch (Exception ignored) {
                    }
                    frame.setVisible(false);
                });

        frame.getContentPane().add(cb, "North");
        frame.getContentPane().add(b, "South");
        frame.setSize(200, 200);

        return frame;
    }
}
