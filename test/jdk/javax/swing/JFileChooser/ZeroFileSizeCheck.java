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
 * @summary To test if the TEST-EMPTY-FILE size shows 0 bytes.
 * @run main/manual ZeroFileSizeCheck
 */

import java.lang.reflect.InvocationTargetException;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import javax.swing.JFileChooser;

public class ZeroFileSizeCheck {

    private static JFrame frame;
    private static final String INSTRUCTIONS =
            "Click on the \"Details\" button in right-top corner.\n\nScroll Down if required. \n\nIf the size of TEST-EMPTY-FILE shows 0 KB in \nDetails view," +
                    " press PASS.\n\n";

    public static void test() {
        JFileChooser fc = new JFileChooser();
        try {
            Path dir = Paths.get(System.getProperty("test.src"));
            // create empty file
            Path emptyFile = dir.resolve("TEST-EMPTY-FILE.txt");
            if (!Files.exists(emptyFile)) {
                Files.createFile(emptyFile);
            }
            fc.setCurrentDirectory(dir.toFile());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        fc.showOpenDialog(null);
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

