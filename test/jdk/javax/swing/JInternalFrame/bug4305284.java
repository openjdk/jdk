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
 * @bug 4305284
 * @summary JInternalFrames can't be sized off of the desktop
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4305284
 */

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;

public class bug4305284 {

    private static final String INSTRUCTIONS = """
        Try to resize the shown internal frame.
        If it can't be sized of the desktop bounds,
        then test passes, else test fails.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4305284 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(25)
                .testUI(bug4305284::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4305284");
        JInternalFrame jif = new JInternalFrame("Test",
                                 true, true, true, true);
        JDesktopPane dp = new JDesktopPane();
        frame.setContentPane(dp);
        dp.add(jif);

        try {
            jif.setBounds(50, 50, 200, 200);
            jif.setMaximum(false);
            jif.setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        frame.setSize(300, 300);
        return frame;
    }

}
