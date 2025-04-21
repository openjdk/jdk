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

/*
 * @test
 * @bug 4194862
 * @summary Tests that internal frame-based dialogs are centered relative
            to their parents
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4194862
 */

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;

public class bug4194862 {
    private static final String INSTRUCTIONS = """
            In the internal frame titled "Main",
            click the "Show JOptionPane Dialog" button.
            A dialog will appear. It should be centered with
            respect to the JInternalFrame - "Main".

            If the above is true then click on JOptionPane's "YES" button
            to PASS else click JOptionPane's "NO" button to FAIL the test.
            """;

    public static void main(String[] args) throws Exception{
        PassFailJFrame.builder()
                .title("Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4194862::createAndShowUI)
                .screenCapture()
                .build()
                .awaitAndCheck();
    }

    private static JFrame createAndShowUI() {
        JFrame frame = new JFrame("bug4194862 - JInternalFrame JOptionPane");
        JDesktopPane desktop = new JDesktopPane();
        frame.add(desktop);
        JInternalFrame jInternalFrame = new JInternalFrame("Main", true);
        desktop.add(jInternalFrame);
        jInternalFrame.setBounds(5, 30, 390, 240);
        jInternalFrame.setVisible(true);

        JButton b = new JButton("Show JOptionPane Dialog");
        b.addActionListener(e -> {
            int retVal = JOptionPane.showInternalConfirmDialog(
                                      jInternalFrame, "Am I centered?",
                                      "bug4194862 JOptionPane", JOptionPane.YES_NO_OPTION);
            switch (retVal) {
                case JOptionPane.YES_OPTION -> PassFailJFrame.forcePass();
                case JOptionPane.NO_OPTION ->
                        PassFailJFrame.forceFail("JOptionPane isn't centered"
                                + " within JInternalFrame \"Main\"");
            }
        });
        jInternalFrame.add(b);

        for (int i = 0; i < 4; i++) {
            JInternalFrame f = new JInternalFrame("JIF: "+ i);
            f.setBounds(i * 50, i * 33, 120, 120);
            f.setVisible(true);
            desktop.add(f);
        }
        frame.setSize(450, 400);
        return frame;
    }
}
