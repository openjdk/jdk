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
 * @summary To test if the 1-Empty-File size shows 0 KB and other files show correct size.
 * @run main/manual FileSizeCheck
 */

import java.lang.reflect.InvocationTargetException;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import java.io.RandomAccessFile;
import javax.swing.WindowConstants;

public class FileSizeCheck {

    private static JFrame frame;
    private static JFileChooser fc;
    private static PassFailJFrame passFailJFrame;
    private static Path [] tempFilePaths;
    private static final String INSTRUCTIONS =
            "Click on the \"Details\" button in right-top corner.\n\n" +
                    "Scroll Down if required. \n\n" +
                    "Test 1: If the size of 1-Empty-File shows 0 KB\n" +
                    "Test 2: If the size of 2-File-2047-Byte shows 2.0 KB\n" +
                    "Test 3: If the size of 3-File-2.5-KB shows 2.5 KB\n" +
                    "Test 4: If the size of 4-File-999-KB shows 999.0 KB\n" +
                    "Test 5: If the size of 5-File-1000-KB shows 1.0 MB\n" +
                    "Test 6: If the size of 6-File-2.8-MB shows 2.8 MB\n\n" +
                           "press PASS.\n\n";

    public static void test() {
        frame = new JFrame("JFileChooser File Size test");
        fc = new JFileChooser();
        Path dir = Paths.get(System.getProperty("test.src"));
        String [] tempFilesName = {"1-Empty-File", "2-File-2047-Byte",
                "3-File-2.5-KB", "4-File-999-KB", "5-File-1000-KB", "6-File-2.8-MB"};
        int [] tempFilesSize = {0, 2_047, 2_500, 999_000, 1_000_000, 2_800_000};
        tempFilePaths = new Path[tempFilesName.length];
        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);
        // create temp files

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

    public static void main(String args[]) throws InterruptedException,
            InvocationTargetException {
        passFailJFrame = new PassFailJFrame("JFileChooser Test Instructions",
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
                System.out.println(ex);
            }
        }
    }
}

