/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8081507
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "linux" | os.family == "freebsd" | os.family == "netbsd" | os.family == "openbsd")
 * @summary Verifies if Open/Save button shows text as Open/Save.
 * @run main/manual TestFileChooserOpenSaveButtonText
 */

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TestFileChooserOpenSaveButtonText {
    private static JFrame frame;
    private static final String INSTRUCTIONS =
                    "Instructions: \n\n" +
                      "1. Check the text on button in bottom panel is Open.\n" +
                      "2. Press Cancel button.\n" +
                      "3. Check the text on button in bottom panel is Save.\n" +
                      "4. Press Cancel button.\n" +
                      "5. If above instructions are confirmed, " +
                            "Press Pass else Fail.";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        PassFailJFrame passFailJFrame = new PassFailJFrame(
                "JFileChooser Test Instructions", INSTRUCTIONS, 5, 8, 35);
        try {
            SwingUtilities.invokeAndWait(
                    TestFileChooserOpenSaveButtonText::createAndShowUI);
            passFailJFrame.awaitAndCheck();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("Test File Chooser Open/Save button text");
        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(
                frame, PassFailJFrame.Position.HORIZONTAL);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.showOpenDialog(null);
        fileChooser.showSaveDialog(null);
    }
}
