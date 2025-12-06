/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4926884
 * @requires (os.family == "windows")
 * @summary Win L&F: JFileChooser problems with "My Documents" folder
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4926884
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.swing.JFileChooser;
import javax.swing.UIManager;

public class bug4926884 {
    private static final String INSTRUCTIONS = """
            Validate next statements step by step:

            1. In the file list there are several dirs and files (like "ski",
               "Snowboard" etc.)
            2. Select "Details" view mode.
            3. Make file list in focus (e.g. by pressing mouse button)
            4. Press key "w" several times with delay LESS than 1 second.
              Selection should be changed across files started with letter "w"
              (without case sensitive).
            5. Press key "w" several times with delay MORE than 1 second.
              Selection should be changed across files started with letter "w"
              (without case sensitive).
            6. Type "winnt" (with delay less than 1 second between letters) -
               directory "winnt" should be selected.
            7. Change conditions:
              - Move column "Name" to the second position
              - Change sort mode by clicking column "Size"
            8. Repeat items 4-6

            If above is true press PASS else FAIL
            """;

    private static final String[] DIRS = {"www", "winnt", "ski"};
    private static final String[] FILES = {"Window", "weel", "mice",
                                           "Wall", "Snowboard", "wood"};
    private static final File testDir = new File(".");

    public static void main(String[] argv) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        try {
            createTestDir();
            PassFailJFrame.builder()
                    .instructions(INSTRUCTIONS)
                    .columns(40)
                    .testUI(() -> new JFileChooser(testDir))
                    .build()
                    .awaitAndCheck();
        } finally {
            deleteTempDir();
        }
    }

    private static void createTestDir() throws IOException {
        testDir.mkdir();

        for (String dir : DIRS) {
            new File(testDir, dir).mkdir();
        }

        for (int i = 0; i < FILES.length; i++) {

            try (OutputStream outputStream = new FileOutputStream(
                    new File(testDir, FILES[i]))) {
                for (int j = 0; j < i * 1024; j++) {
                    outputStream.write('#');
                }
            }
        }
    }

    private static void deleteTempDir() {
        File[] files = testDir.listFiles();

        for (File file : files) {
            if (file != null) {
                file.delete();
            }
        }

        testDir.delete();
    }
}
