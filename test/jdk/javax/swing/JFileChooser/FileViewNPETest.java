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

import java.awt.BorderLayout;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import javax.swing.filechooser.FileView;
import jdk.test.lib.Platform;
/*
 * @test
 * @bug 6616245
 * @key headful
 * @requires (os.family == "windows" | os.family == "linux")
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jdk.test.lib.Platform
 * @summary Test to check if NPE occurs when using custom FileView.
 * @run main/manual FileViewNPETest
 */
public class FileViewNPETest {
    static JFrame frame;
    static JFileChooser jfc;
    static PassFailJFrame passFailJFrame;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    initialize();
                } catch (InterruptedException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        passFailJFrame.awaitAndCheck();
    }

    static void initialize() throws InterruptedException, InvocationTargetException {
        //Initialize the components
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. Select any folder except "C:\\temp" (In windows) / "/tmp"
                (In linux) and click to traverse through it.
                2. Repeat the traversal try (2 times) and on second try if
                NPE occurs then test FAILS else test PASS.
                """;
        frame = new JFrame("JFileChooser File View NPE test");
        passFailJFrame = new PassFailJFrame("Test Instructions", INSTRUCTIONS, 5L, 5, 40);
        jfc = new JFileChooser();
        String path = "";
        if (Platform.isWindows()) {
            path = "C:" + File.separator + "temp";
        } else {
            path = File.separator + "tmp";
        }

        jfc.setCurrentDirectory(new File(path));
        jfc.setFileView(new CustomFileView(path));

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        frame.add(jfc, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }
}

class CustomFileView extends FileView {
    private String basePath;

    public CustomFileView(String path) {
        basePath = path;
    }

    public Boolean isTraversable(File filePath) {
        if ((filePath != null) && (filePath.isDirectory())) {
            return filePath.getAbsolutePath().startsWith(basePath);
        } else {
            return false;
        }
    }
}
