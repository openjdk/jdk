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
 * @bug 8139228 8358532
 * @summary JFileChooser should not render Directory names in HTML format
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual HTMLFileName system
 */

public class HTMLFileName {
    private static final String INSTRUCTIONS = """
            <html>
            <ol>
            <li><code>JFileChooser</code> shows a virtual directory.
                The first file in the list has the following name:
                <code>&lt;html&gt;&lt;h1 color=#ff00ff&gt;&lt;font
                face="Serif"&gt;Swing Rocks!</code>
                <br>
                <br>
            <li>In <b>HTML disabled</b> frame:
                <ol>
                  <li>Verify that the first file name displays
                      as <em>plain text</em>,
                      that is you see the HTML tags in the file name.
                  <li>If the file name in the file pane and
                      in the navigation combo box above is displayed
                      as HTML, that is in large font and magenta color,
                      then press <b>Fail</b>.
                </ol>

            <li>In <b>HTML enabled</b> frame:
                <ol>
                  <li>Verify that the first file name displays as <em>HTML</em>,
                      that is <code><font face="Serif"
                      color=#ff00ff>Swing Rocks!</code> in large font
                      and magenta color.<br>
                      <b>Note:</b> On macOS in Aqua L&amp;F, the file name with
                      HTML displays as an empty file name. It is not an error.
                  <li>If the file name in the file pane and
                      in the navigation combo box above is displayed
                      as HTML, then press <b>Pass</b>.<br>
                      If it is in plain text, then press <b>Fail</b>.
                </ol>
            </ol>
            </html>
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
                .rows(20)
                .testUI(HTMLFileName::initialize)
                .positionTestUIBottomRowCentered()
                .build()
                .awaitAndCheck();
        System.out.println("Test passed for LookAndFeel " + lafClassName);
    }

    private static List<JFrame> initialize() {
        return List.of(createFileChooser(true), createFileChooser(false));
    }

    private static JFrame createFileChooser(boolean htmlDisabled) {
        JFileChooser jfc = new JFileChooser(new VirtualFileSystemView());
        jfc.putClientProperty("html.disable", htmlDisabled);
        jfc.setControlButtonsAreShown(false);

        JFrame frame = new JFrame(htmlDisabled ? "HTML disabled" : "HTML enabled");
        frame.add(jfc);
        frame.pack();
        return frame;
    }

    private static class VirtualFileSystemView extends FileSystemView {
        private final File[] files = {
                new File("/", "<html><h1 color=#ff00ff><font " +
                         "face=\"Serif\">Swing Rocks!"),
                new File("/", "virtualFile1.txt"),
                new File("/", "virtualFile2.log")
        };

        @Override
        public File createNewFolder(File containingDir) {
            return null;
        }

        @Override
        public File[] getRoots() {
            return files;
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
            return files;
        }

        @Override
        public Icon getSystemIcon(File f) {
            return null;
        }
    }
}
