/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4130806
 * @summary JInternalFrame's setIcon(true) works correctly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4130806
 */

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import java.beans.PropertyVetoException;

public class bug4130806 {

    private static final String INSTRUCTIONS = """
        If an icon is visible for the iconified internalframe, the test passes.
        Otherwise, the test fails.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4130806 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug4130806::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4130806");
        JDesktopPane mDesktop = new JDesktopPane();
        frame.add(mDesktop);
        frame.pack();
        JInternalFrame jif = new JInternalFrame("My Frame");
        jif.setIconifiable(true);
        mDesktop.add(jif);
        jif.setBounds(50,50,100,100);
        try {
            jif.setIcon(true);
        } catch (PropertyVetoException e) {
            throw new RuntimeException("PropertyVetoException received");
        }
        jif.setVisible(true);
        frame.setSize(200, 200);
        return frame;
    }
}
