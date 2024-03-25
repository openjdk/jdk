/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import javax.swing.JFileChooser;
import javax.swing.UIManager;

import jdk.test.lib.Platform;

/*
 * @test
 * @bug 4150029 8006087
 * @summary BackSpace keyboard button does not lead to parent directory
 * @library /test/lib /java/awt/regtesthelpers
 * @build jdk.test.lib.Platform PassFailJFrame
 * @run main/manual bug4150029
 */

public class bug4150029 {
    private static boolean res;

    public static void main(String[] args) throws Exception {
        String instructions = """
                Follow the instructions below.
                1.Go into 'subDir' folder.
                2.Press BACKSPACE key.
                3.Push OPEN button.
                4.Push PASS button.""";

        PassFailJFrame pfframe = PassFailJFrame.builder()
                .title("bug4150029")
                .instructions(instructions)
                .rows(5)
                .columns(40)
                .testTimeOut(10)
                .build();

        try {
            if (Platform.isOSX()) {
                try {
                    UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            String tmpDir = System.getProperty("java.io.tmpdir");

            //'java.io.tmpdir' isn't guaranteed to be defined
            if (tmpDir.length() == 0) {
                tmpDir = System.getProperty("user.home");
            }

            System.out.println("Temp directory: " + tmpDir);

            File testDir = new File(tmpDir, "testDir");

            testDir.mkdir();

            File subDir = new File(testDir, "subDir");

            subDir.mkdir();

            System.out.println("Created directory: " + testDir);
            System.out.println("Created sub-directory: " + subDir);

            JFileChooser fileChooser = new JFileChooser(testDir);

            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            try {
                res = fileChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION ||
                        testDir.getCanonicalPath().equals(fileChooser.getSelectedFile().getCanonicalPath());
            } catch (IOException e) {
                res = false;

                e.printStackTrace();
            }

            try {
                subDir.delete();
                testDir.delete();
            } catch (SecurityException e) {
                e.printStackTrace();
            }

            pfframe.awaitAndCheck();
        } finally {
            if (!res) {
                PassFailJFrame.forceFail("BackSpace keyboard button does not lead to parent directory");
            }
        }
    }
}
