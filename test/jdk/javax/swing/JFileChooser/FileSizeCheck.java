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
 * @bug 8288882
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "linux")
 * @summary To test if the 1-Empty-File size shows 0.0 KB and other files show correct size.
 * @run main/manual FileSizeCheck
 */

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class FileSizeCheck {
    private static Path[] tempFilePaths;
    private static final String INSTRUCTIONS =
            "Click on the \"Details\" button in right-top corner.\n\n" +
                    "Scroll Down if required. \n\n" +
                    "Test 1: If the size of 1st-Empty-File shows 0.0 KB\n" +
                    "Test 2: If the size of 2nd-File-1-Byte shows 0.1 KB\n" +
                    "Test 3: If the size of 3rd-File-160-Byte shows 0.2 KB\n" +
                    "Test 3: If the size of 4th-File-299-Byte shows 0.3 KB\n" +
                    "Test 4: If the size of 5th-File-900-Byte shows 0.9 KB\n" +
                    "Test 6: If the size of 6th-File-901-Byte shows 1.0 KB\n" +
                    "Test 7: If the size of 7th-File-999-KB shows 999.0 KB\n" +
                    "Test 8: If the size of 8th-File-1000-KB shows 1.0 MB\n" +
                    "Test 9: If the size of 9th-File-2.8-MB shows 2.8 MB\n\n" +
                           "press PASS.\n\n";

    public static void test() {
        JFrame frame = new JFrame("JFileChooser File Size test");
        JFileChooser fc = new JFileChooser();
        fc.setControlButtonsAreShown(false);
        Path dir = Paths.get(".");
        String[] tempFilesName = {"1st-Empty-File", "2nd-File-1-Byte", "3rd-File-160-Byte",
                "4th-File-299-Byte", "5th-File-900-Byte", "6th-File-901-Byte",
                "7th-File-999-KB", "8th-File-1000-KB", "9th-File-2.8-MB"};

        int[] tempFilesSize = {0, 1, 160, 299, 900, 901, 999_000, 1_000_000, 2_800_000};

        tempFilePaths = new Path[tempFilesName.length];
        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);

        // Create temp files
        try {
            for (int i = 0; i < tempFilePaths.length; i++) {
                tempFilePaths[i] = dir.resolve(tempFilesName[i]);
                if (!Files.exists(tempFilePaths[i])){
                    RandomAccessFile f = new RandomAccessFile(tempFilePaths[i].toFile(), "rw");
                    f.setLength(tempFilesSize[i]);
                    f.close();
                }
            }
            fc.setCurrentDirectory(dir.toFile());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        frame.add(fc);
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame passFailJFrame = new PassFailJFrame("JFileChooser Test Instructions",
                INSTRUCTIONS, 5, 19, 35);
        try {
            SwingUtilities.invokeAndWait(FileSizeCheck::test);
            passFailJFrame.awaitAndCheck();
        } finally {
            try {
                for (int i = 0; i < tempFilePaths.length; ++i) {
                    Files.deleteIfExists(tempFilePaths[i]);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}

