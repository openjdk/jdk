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
 * @bug 4227768
 * @requires (os.family == "windows")
 * @summary Tests Z-ordering of Windows Look-and-Feel JInternalFrames
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4227768
 */

import java.awt.Dimension;
import java.awt.Toolkit;
import java.beans.PropertyVetoException;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.UIManager;

public class bug4227768 {
    private static JDesktopPane desktop ;
    private static JFrame frame;
    private static int openFrameCount = 0;
    private static final int xOffset = 30;
    private static final int yOffset = 30;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel
                ("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException("Failed to set Windows LAF");
        }

        String INSTRUCTIONS = """
            Close the internal frame titled "Document #4". The internal frame
            titled "Document #3" should get active. Now close the internal
            frame titled "Document #2". The internal frame titled "Document #3"
            should remain active. If something is not like this, then test
            failed. Otherwise test succeeded.
            """;
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(50)
            .testUI(bug4227768::initialize)
            .build()
            .awaitAndCheck();
    }

    private static JFrame initialize() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame = new JFrame("bug4227768");
        frame.setSize(screenSize.width / 3, screenSize.height / 3);
        frame.add(desktop = new JDesktopPane());
        createFrame();
        createFrame();
        createFrame();
        createFrame();
        desktop.putClientProperty("JDesktopPane.dragMode", "outline");
        return frame;
    }

    protected static void createFrame() {
        JInternalFrame internalFrame = new JInternalFrame
            ("Document #" + (++openFrameCount), true, true, true, true);
        internalFrame.setSize(frame.getWidth() / 2, frame.getHeight() / 2);
        internalFrame.setLocation(xOffset * openFrameCount,
            yOffset * openFrameCount);
        desktop.add(internalFrame);
        internalFrame.setVisible(true);
        try {
            internalFrame.setSelected(true);
        } catch (PropertyVetoException e) {
            throw new RuntimeException(e);
        }
    }
}
