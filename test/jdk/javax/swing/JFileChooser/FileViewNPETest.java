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

/*
 * @test
 * @bug 6616245
 * @key headful
 * @requires (os.family == "windows" | os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test to check if NPE occurs when using custom FileView.
 * @run main/manual FileViewNPETest
 */
public class FileViewNPETest {
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
        JFrame frame;
        JFileChooser jfc;

        //Initialize the components
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. The traversable folder is set to the Documents folder,
                 if it exists, in the user's home folder, otherwise
                 it's the user's home. Other folders are non-traversable.
                2. When the file chooser appears on the screen, select any
                 non-traversable folder from "Look-In" combo box,
                 for example the user's folder or a folder above it.
                 (The folder will not be opened since it's non-traversable).
                3. Select the Documents folder again.
                4. If NullPointerException does not occur in the step 3,
                 click Pass, otherwise the test fails automatically.
                """;
        frame = new JFrame("JFileChooser File View NPE test");
        passFailJFrame = new PassFailJFrame("Test Instructions", INSTRUCTIONS,
                5L, 13, 40);
        jfc = new JFileChooser();

        String userHome = System.getProperty("user.home");
        String docs = userHome + File.separator + "Documents";
        String path = (new File(docs).exists()) ? docs : userHome;

        jfc.setCurrentDirectory(new File(path));
        jfc.setFileView(new CustomFileView(path));
        jfc.setControlButtonsAreShown(false);

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        frame.add(jfc, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }
}

class CustomFileView extends FileView {
    private final String basePath;

    public CustomFileView(String path) {
        basePath = path;
    }

    public Boolean isTraversable(File filePath) {
        return ((filePath != null) && (filePath.isDirectory()))
                && filePath.getAbsolutePath().startsWith(basePath);
    }
}
