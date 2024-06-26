/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4203039
 * @summary JToolBar needs a way to limit docking to a particular orientation
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4203039
 */

public class bug4203039 {
    private static final String instructionsText = """
            This test is used to verify that application-installed
            components prevent the toolbar from docking in
            those locations.

            This test has installed components on the SOUTH
            and EAST, so verify the toolbar cannot dock in those
            locations but can dock on the NORTH and WEST""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .title("bug4203039 Instructions")
                .instructions(instructionsText)
                .testTimeOut(5)
                .rows(10)
                .columns(35)
                .build();

        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("bug4203039");
            frame.setSize(300, 200);

            JToolBar toolbar = new JToolBar();
            JLabel label = new JLabel("This is the toolbar");
            toolbar.add(label);

            frame.add(toolbar, BorderLayout.NORTH);

            frame.add(new JComponent(){}, BorderLayout.SOUTH);
            frame.add(new JComponent(){}, BorderLayout.EAST);

            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame.positionTestWindow(frame,
                    PassFailJFrame.Position.HORIZONTAL);

            frame.setVisible(true);
        });

        passFailJFrame.awaitAndCheck();
    }
}
