/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 8309460
 * @summary Verifies clicking JComboBox during frame closure causes Exception
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual JScrollBarArtifactTest
 */

public class JScrollBarArtifactTest {
    private static final String instructionsText = """
            This test is used to verify that dragging scrollbar or clicking
            on right scrollbar thumb does nto leave behing lines or artifacts.

            A horizontal JScrollBar is shown.
            Drag the scrollbar without releasing mouse.
            Additionally, keep right arrow button pressed to move the scrollbar.
            If lines are left behind in both cases, please click Fail
            otherwise click Pass.  """;

    public static void main(String[] args) throws Exception {
        System.setProperty("sun.java2d.uiScale", "2.3");
        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                .title("ComboPopup Instructions")
                .instructions(instructionsText)
                .testTimeOut(5)
                .rows(10)
                .columns(35)
                .build();

        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            JPanel panel = new JPanel(new BorderLayout());
            JScrollBar sb = new JScrollBar(JScrollBar.HORIZONTAL);
            panel.add(sb, "South");
            frame.setContentPane(panel);
            frame.setSize(500, 200);

            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame.positionTestWindow(frame,
                    PassFailJFrame.Position.VERTICAL);

            frame.setVisible(true);
        });

        passFailJFrame.awaitAndCheck();
    }
}

