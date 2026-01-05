/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4614881
 * @summary Ensure that client decorated frames can be brought to the front
 *          via mouse click on the title pane.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4614881
 */

import java.awt.FlowLayout;
import java.awt.Toolkit;
import javax.swing.JDialog;
import javax.swing.JFrame;

public class bug4614881 {

    private static final String INSTRUCTIONS = """
        Select a native window so that it obscures the client decorated frame.
        Select the decorated frame by clicking on the title pane.
        If the decorated frame is brought to the front, then test passes else fails.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4614881 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug4614881::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        Toolkit.getDefaultToolkit().setDynamicLayout(true);
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        final JFrame frame = new JFrame("4614881 - Decorated Frame");
        frame.setSize(600, 400);
        frame.setResizable(false);
        frame.getContentPane().setLayout(new FlowLayout());
        return frame;
    }
}
