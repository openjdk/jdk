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
 * @bug 4131008
 * @summary JInternalFrame should refresh title after it changing
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4131008
*/

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.UIManager;

public class bug4131008 {

    private static final String INSTRUCTIONS = """
        Press button "Change title" at the internal frame "Old".
        If title of this frame will replaced by "New",
        then test passed, else test fails.""";

    public static void main(String[] args) throws Exception {

        UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");

        PassFailJFrame.builder()
            .title("bug4131008 Instructions")
            .instructions(INSTRUCTIONS)
            .columns(50)
            .testUI(bug4131008::createTestUI)
            .build()
            .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4131008");
        JInternalFrame jif = new JInternalFrame("Old");
        JDesktopPane jdp = new JDesktopPane();
        frame.setContentPane(jdp);

        jif.setSize(150, 100);
        jif.setVisible(true);
        JButton bt = new JButton("Change title");
        bt.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                jif.setTitle("New");
            }
        });
        jif.getContentPane().add(bt);
        jdp.add(jif);
        try {
            jif.setSelected(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        frame.setSize(300, 200);
        return frame;
    }
}
