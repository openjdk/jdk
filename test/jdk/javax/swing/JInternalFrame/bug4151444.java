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
 * @bug 4151444
 * @summary The maximize button acts like the restore button
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4151444
*/

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.UIManager;


public class bug4151444 {

    private static JFrame frame;
    private static JInternalFrame interFrame;

    private static final String INSTRUCTIONS = """
       - maximize the internal frame
       - then minimize the internal frame
       - then maximize the internal frame again
       - Check whether internal frame is maximized
       - Test will fail automatically even if "Pass" is pressed
         if internal frame is not maximized.""";

    public static void main(String[] args) throws Exception {

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        PassFailJFrame pfj = PassFailJFrame.builder()
                .title("bug4151444 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(bug4151444::createTestUI)
                .build();
        try {
            pfj.awaitAndCheck();
        } finally {
            if (!interFrame.isMaximum()) {
                throw new RuntimeException ("Test failed. The maximize button acts like the restore button");
            }
        }
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4151444 frame");
        JDesktopPane desktop = new JDesktopPane();
        frame.setContentPane(desktop);
        interFrame = new JInternalFrame(
            "Internal frame", true, true, true, true);
        desktop.add(interFrame, JLayeredPane.DEFAULT_LAYER);
        interFrame.setBounds(0, 0, 200, 100);
        interFrame.setVisible(true);
        frame.setSize(300, 200);
        return frame;
    }
}
