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
 * @summary To test if the TEST-EMPTY-FILE size shows 0 KB and other files show correct size.
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

public class FileSizeCheck {

    private static JFrame frame;
    private static final String INSTRUCTIONS =
            "Click on the \"Details\" button in right-top corner.\n\nScroll Down if required. \n\n" +
                    "Test 1: If the size of 2.5-KB-File shows 2.5 KB\n" +
                    "Test 2: If the size of 2.8-MB-File shows 2.8 MB\n" +
                    "Test 3: If the size of 999-KB-File shows 999 KB\n" +
                    "Test 4: If the size of 1000-KB-File shows 1 MB\n" +
                    "Test 5: If the size of 2047-Byte-File shows 2 KB\n" +
                    "Test 6: If the size of Empty-File shows 0 KB\n\n" +
                           " press PASS.\n\n";

    public static void test() {
        JFileChooser fc = new JFileChooser();
        Path dir = Paths.get(System.getProperty("test.src"));
        String [] tempFilesName = {"2.5-KB-File","2.8-MB-File","999-KB-File","1000-KB-File","2047-Byte-File","Empty-File"};
        int [] tempFilesSize = {2500, 2800000,999000,1000000,2047,0};
        Path [] tempFilePaths = new Path[tempFilesName.length];
        for (int i = 0 ; i < tempFilesName.length ; i++) {
            tempFilePaths[i] = dir.resolve(tempFilesName[i]);
        }

        // create temp files
        try {
            for (int i = 0 ; i < tempFilePaths.length ; i++) {
                if (!Files.exists(tempFilePaths[i])){
                    RandomAccessFile f = new RandomAccessFile(tempFilePaths[i].toString(), "rw");
                    f.setLength(tempFilesSize[i]);
                    f.close();
                }
            }
            fc.setCurrentDirectory(dir.toFile());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        fc.showOpenDialog(null);

        // delete temp files
        try {
            for (int i = 0 ; i < tempFilePaths.length ; ++i) {
                Files.deleteIfExists(Paths.get(tempFilePaths[i].toString()));
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String args[]) throws Exception {
        PassFailJFrame passFailJFrame = new PassFailJFrame("JFileChooser Test Instructions" ,
                INSTRUCTIONS, 5, 19, 35);
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame();
            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);
            test();
        });

        passFailJFrame.awaitAndCheck();
    }
}

