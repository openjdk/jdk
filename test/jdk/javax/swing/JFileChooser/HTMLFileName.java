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

import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.util.List;

/*
 * @test
 * @bug 8139228
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
                          a. Verify that the folder name must be plain text.
                          b. If the folder name in file pane window and also in directory
                             ComboBox remains in plain text, then test PASS.
                             If it appears to be in HTML format with Pink color, then test FAILS
                             (Verify for all Look and Feel).
                    3. On "HTML enabled" frame :
                          a. Verify that the folder name remains in HTML
                             format with name "Testing Name" pink in color.
                          b. If the folder name in file pane window and also in directory
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
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        File homeDir = FileSystemView.getFileSystemView().getHomeDirectory();
        String fileName = homeDir + File.separator +
                "<html><h1 color=#ff00ff><font face=\"Comic Sans MS\">Testing Name";
        directory = new File(fileName);
        directory.mkdir();
        JFileChooser jfc;
        JFileChooser jfc_HTML_Enabled;
        JFrame frame_HTML_Enabled = new JFrame("HTML enabled");
        JFrame frame = new JFrame("HTML disabled");
        if (isWindows) {
            jfc = new JFileChooser(new VirtualFileSystemView());
            jfc_HTML_Enabled = new JFileChooser(new VirtualFileSystemView());
        } else {
            jfc = new JFileChooser(homeDir);
            jfc_HTML_Enabled = new JFileChooser(homeDir);
        }
        jfc_HTML_Enabled.putClientProperty("html.disable", false);
        frame.setLocation(600, 50);
        frame.add(jfc);
        frame.pack();
        frame_HTML_Enabled.setLocation(600, frame.getHeight() + 100);
        frame_HTML_Enabled.add(jfc_HTML_Enabled);
        frame_HTML_Enabled.pack();
        return List.of(frame, frame_HTML_Enabled);
    }

    static class VirtualFileSystemView extends FileSystemView {
        @Override
        public File createNewFolder(File containingDir) {
            return null;
        }

        @Override
        public File[] getRoots() {
            return new File[]{
                    new File("/", "<html><h1 color=#ff00ff><font face=\"Comic Sans MS\">SWING ROCKS!!!111"),
                    new File("/", "virtualFile2.txt"),
                    new File("/", "virtualFolder")
            };
        }

        @Override
        public File getHomeDirectory() {
            return new File("/");
        }

        @Override
        public File getDefaultDirectory() {
            return new File("/");
        }

        @Override
        public File[] getFiles(File dir, boolean useFileHiding) {
            // Simulate a virtual folder structure
            return new File[]{
                    new File("/", "<html><h1 color=#ff00ff><font face=\"Comic Sans MS\">SWING ROCKS!!!111"),
                    new File(dir, "virtualFile2.txt"),
                    new File(dir, "virtualFolder")
            };
        }

        @Override
        public Icon getSystemIcon(File f) {
            return null;
        }
    }
}
