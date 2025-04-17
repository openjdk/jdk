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
 * @bug 4357012
 * @requires (os.family == "windows")
 * @summary JFileChooser.showSaveDialog inconsistent with Windows Save Dialog
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4357012
 */

import java.io.File;
import java.io.IOException;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.UIManager;

public class bug4357012 {
    private static File workDir = null;
    private static File dir = null;
    private static File file = null;
    private static final String INSTRUCTIONS = """
            <html>
            Test is for Windows LAF only
            <p>In JFileChooser's files list :
            <ol>
            <li>Select directory. Verify that the directory name doesn't
            appear in "file name" field.</li>
            <li>Select file. Verify that the file name appears in
            "file name" field.</li>
            <li>Select directory again. Verify that the previous file name
            remains in file name field.</li>
            </ol>
            </p>
            </html>
            """;

    public static void main(String[] argv) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            createTestDir();
            PassFailJFrame.builder()
                    .instructions(INSTRUCTIONS)
                    .rows(10)
                    .columns(40)
                    .testUI(bug4357012::createTestUI)
                    .build()
                    .awaitAndCheck();
        } finally {
            if (workDir != null) {
                System.out.println("Deleting '" + file + "': " + file.delete());
                System.out.println("Deleting '" + dir + "': " + dir.delete());
                System.out.println("Deleting '" + workDir + "': " + workDir.delete());
            }
        }
    }

    private static void createTestDir() throws IOException {
        String tempDir = ".";
        String fs = System.getProperty("file.separator");

        workDir = new File(tempDir + fs + "bug4357012");
        System.out.println("Creating '" + workDir + "': " + workDir.mkdir());

        dir = new File(workDir + fs + "Directory");
        System.out.println("Creating '" + dir + "': " + dir.mkdir());

        file = new File(workDir + fs + "File.txt");
        System.out.println("Creating '" + file + "': " + file.createNewFile());
    }

    private static JComponent createTestUI() {
        JFileChooser fc = new JFileChooser(workDir);
        fc.setDialogType(JFileChooser.SAVE_DIALOG);
        return fc;
    }
}
