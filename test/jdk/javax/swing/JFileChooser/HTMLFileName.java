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

import java.io.File;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;

/*
 * @test id=metal
 * @bug 8139228
 * @summary JFileChooser should not render Directory names in HTML format
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual HTMLFileName metal
 */

/*
 * @test id=system
 * @bug 8139228
 * @summary JFileChooser should not render Directory names in HTML format
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual HTMLFileName system
 */

public class HTMLFileName {
    private static final String INSTRUCTIONS = """
            1. FileChooser shows up a virtual directory and file with name
               "<html><h1 color=#ff00ff><font face="Comic Sans MS">Swing Rocks!".
            2. On "HTML disabled" frame :
                  a. Verify that the folder and file name must be plain text.
                  b. If the name in file pane window and also in directory
                     ComboBox remains in plain text, then test passes.
                     If it appears to be in HTML format with Pink color,
                     then test fails.
                     (Verify for all Look and Feel).
            3. On "HTML enabled" frame :
                  a. Verify that the folder and file name remains in HTML
                     format with name "Testing Name" pink in color.
                  b. If the name in file pane window and also in directory
                     ComboBox remains in HTML format string, then test passes.
                     If it appears to be in plain text, then test fails.
                     (Verify for all Look and Feel).
            """;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Look-and-Feel keyword is required");
        }

        final String lafClassName;
        switch (args[0]) {
            case "metal" -> lafClassName = UIManager.getCrossPlatformLookAndFeelClassName();
            case "system" -> lafClassName = UIManager.getSystemLookAndFeelClassName();
            default -> throw new IllegalArgumentException("Unsupported Look-and-Feel keyword: " + args[0]);
        }

        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(lafClassName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        System.out.println("Test for LookAndFeel " + lafClassName);
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(HTMLFileName::initialize)
                .positionTestUIBottomRowCentered()
                .build()
                .awaitAndCheck();
        System.out.println("Test passed for LookAndFeel " + lafClassName);
    }

    private static List<JFrame> initialize() {
        return List.of(createFileChooser(true), createFileChooser(false));
    }

    private static JFrame createFileChooser(boolean htmlEnabled) {
        JFileChooser jfc = new JFileChooser(new VirtualFileSystemView());
        jfc.putClientProperty("html.disable", htmlEnabled);
        jfc.setControlButtonsAreShown(false);
        JFrame frame = new JFrame((htmlEnabled) ? "HTML enabled" : "HTML disabled");
        frame.add(jfc);
        frame.pack();
        return frame;
    }

    private static class VirtualFileSystemView extends FileSystemView {
        @Override
        public File createNewFolder(File containingDir) {
            return null;
        }

        @Override
        public File[] getRoots() {
            return new File[]{
                    new File("/", "<html><h1 color=#ff00ff><font " +
                            "face=\"Comic Sans MS\">Swing Rocks!!!!111"),
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
                    new File("/", "<html><h1 color=#ff00ff><font " +
                            "face=\"Comic Sans MS\">Swing Rocks!"),
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
