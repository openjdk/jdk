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
 * @bug 4190516
 * @summary JInternalFrame should be maximized when Desktop resized
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4190516
 */

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;

public class bug4190516 {

    private static final String INSTRUCTIONS = """
        Try to resize frame "bug4190516 Frame".
        If the internal frame remains maximized
        inside this frame then test passes, else test fails.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4190516 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(25)
                .testUI(bug4190516::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame fr = new JFrame("bug4190516 Frame");
        JDesktopPane jdp = new JDesktopPane();
        fr.getContentPane().add(jdp);

        JInternalFrame jif = new JInternalFrame("Title", true, true, true, true);
        jdp.add(jif);
        jif.setSize(150, 150);
        jif.setVisible(true);

        fr.setSize(300, 200);
        try {
            jif.setMaximum(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        SwingUtilities.updateComponentTreeUI(fr);
        return fr;
    }
}
