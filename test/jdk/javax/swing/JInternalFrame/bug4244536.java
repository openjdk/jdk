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
 * @bug 4244536
 * @summary Tests that Motif JInternalFrame can be maximized
 *          after it was iconified.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4244536
 */

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.UIManager;

public class bug4244536 {

    private static final String INSTRUCTIONS = """
        Minimize the internal frame using the minimize button.
        Then double-click on it to restore its size.
        Then press the maximize button.
        If the frame gets maximized, test passes.
        If its size don't change, test fails.""";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(
                  "com.sun.java.swing.plaf.motif.MotifLookAndFeel");

        PassFailJFrame.builder()
                .title("bug4244536 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4244536::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4244536");
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame jif = new JInternalFrame("Internal Frame");
        jif.setSize(150, 150);
        jif.setMaximizable(true);
        jif.setIconifiable(true);
        jif.setVisible(true);
        desktop.add(jif);
        frame.add("Center", desktop);
        frame.setSize(300, 300);
        return frame;
    }

}
