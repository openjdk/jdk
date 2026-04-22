/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4943900
 * @summary Tests that FileFilter combo box is shown in FileChooser
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4943900
 */

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;

public class bug4943900 {
    private static final String INSTRUCTIONS = """
        <html>
        <ol>
        <li>When the test runs, a <code>JFileChooser</code> will be displayed.
        <li>Ensure that there is a filter combo box with these two items:
          <ul>
          <li><b>Text Files (*.txt)</b>
              &mdash; <em>[must be selected when the dialog opens]</em>
          <li><b>All Files</b>
          </ul>
        <li>Leave the <b>Text files</b> item selected and check that the
        filter works: only <code>*.txt</code> files can appear in the file list.
        You can navigate directories in the file chooser and find one
        that contains some <code>*.txt</code> files to ensure they are shown in
        the file list. On macOS when the text filter is applied verify
        that the non-text files are greyed out.
        <li>Try switching the filters and ensure that the file list
        is updated properly.
        <li>If the <code>FileFilter</code> works correctly,
            press <b>Pass</b> else press <b>Fail</b>.
        </ol>
        </html>
        """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        PassFailJFrame.builder()
                .title("bug4943900 Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows(14)
                .columns(50)
                .testUI(bug4943900::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createAndShowUI() {
        JFileChooser fc = new JFileChooser();
        fc.setControlButtonsAreShown(false);
        TextFileFilter filter = new TextFileFilter();
        fc.setFileFilter(filter);

        JFrame frame = new JFrame("bug4943900 - JFileChooser");
        frame.add(fc);
        frame.pack();
        return frame;
    }

    private static final class TextFileFilter extends FileFilter {
        @Override
        public boolean accept(File f) {
            if (f != null) {
                if (f.isDirectory()) {
                    return true;
                }
                String extension = getExtension(f);
                return extension != null && extension.equals("txt");
            }
            return false;
        }

        @Override
        public String getDescription() {
            return "Text Files (*.txt)";
        }

        private static String getExtension(File f) {
            if (f != null) {
                String filename = f.getName();
                int i = filename.lastIndexOf('.');
                if (i > 0 && i < filename.length() - 1) {
                    return filename.substring(i + 1).toLowerCase();
                }
            }
            return null;
        }
    }
}
