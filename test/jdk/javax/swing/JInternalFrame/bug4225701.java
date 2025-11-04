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
 * @bug 4225701
 * @summary Verifies MetalInternalFrameUI.installKeyboardActions
 *          doesn't install listener
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4225701
 */

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;

public class bug4225701 {

    private static final String INSTRUCTIONS = """
        Give a focus to the internal frame "Frame 4" and press Ctrl-F4.
        The "Frame 4" should be closed. Give a focus to the internal
        frame "Frame 1" and press Ctrl-F4.
        If "Frame 4" and "Frame 1" is not closed, then press Fail else press Pass.""";

    public static void main(String[] args) throws Exception {
         PassFailJFrame.builder()
                .title("bug4225701 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug4225701::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {

        JFrame frame = new JFrame("bug4225701");
        JInternalFrame jif1 = new JInternalFrame("Frame 1", true, true, true, true);
        JInternalFrame jif2 = new JInternalFrame("Frame 2", false);
        JInternalFrame jif3 = new JInternalFrame("Frame 3", false);
        JInternalFrame jif4 = new JInternalFrame("Frame 4", true, true, true, true);
        JDesktopPane jdp = new JDesktopPane();

        frame.setContentPane(jdp);

        jdp.add(jif1);
        jif1.setBounds(0, 150, 150, 150);
        jif1.setVisible(true);

        jdp.add(jif2);
        jif2.setBounds(100, 100, 150, 150);
        jif2.setVisible(true);

        jdp.add(jif3);
        jif3.setBounds(200, 50, 150, 150);
        jif3.setVisible(true);

        jdp.add(jif4);
        jif4.setBounds(300, 0, 150, 150);
        jif4.setVisible(true);

        frame.setSize(500, 500);
        return frame;
    }

}
