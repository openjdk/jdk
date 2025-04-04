/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.util.List;

/*
 * @test
 * @bug 8139228
 * @requires (os.family == "linux") | (os.family == "mac")
 * @summary JFileChooser should not render Directory names in HTML format
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual HTMLFileName
 */

public class HTMLFileName {
    static File directory;
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                    1. New Directory is created in current directory with name
                       "<html><h1 color=#ff00ff><font face="Comic Sans MS">Testing Name".
                    2. On "HTML disabled" frame :
                          a. Navigate to the folder.
                          b. Verify that the folder name must be plain text.
                          c. If the folder name in file pane window and also in directory
                             ComboBox remains in plain text, then test PASS.
                             If it appears to be in HTML format with Pink color, then test FAILS
                             (Verify for all Look and Feel).
                    3. On "HTML enabled" frame :
                          a. Navigate to the folder.
                          b. Verify that the folder name remains in HTML
                             format with name "Testing Name" pink in color.
                          c. If the folder name in file pane window and also in directory
                             ComboBox remains in HTML format string, then test PASS.
                             If it appears to be in plain text, then test FAILS.
                             (Verify for all Look and Feel).
                """;

        try {
            PassFailJFrame.builder()
                    .title("Test Instructions")
                    .instructions(INSTRUCTIONS)
                    .columns(45)
                    .testUI(initialize())
                    .position(PassFailJFrame.Position.TOP_LEFT_CORNER)
                    .build()
                    .awaitAndCheck();
        } finally {
            if (directory != null) {
                directory.delete();
            }
        }
    }

    public static List<JFrame> initialize() {
        File homeDir = FileSystemView.getFileSystemView().getHomeDirectory();
        String fileName = homeDir + File.separator +
                "<html><h1 color=#ff00ff><font face=\"Comic Sans MS\">Testing Name";
        directory = new File(fileName);

        directory.mkdir();
        JFileChooser jfc = new JFileChooser(homeDir);
        JFrame frame = new JFrame("HTML disabled");
        JFileChooser jfc_HTML_Enabled = new JFileChooser(homeDir);
        jfc_HTML_Enabled.putClientProperty("html.disable", false);
        JFrame frame_HTML_Enabled = new JFrame("HTML enabled");
        frame.setLocation(600, 50);
        frame.add(jfc);
        frame.pack();
        frame_HTML_Enabled.setLocation(600, frame.getHeight() + 100);
        frame_HTML_Enabled.add(jfc_HTML_Enabled);
        frame_HTML_Enabled.pack();
        return List.of(frame, frame_HTML_Enabled);
    }
}
