/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6260659
 * @summary File Name set programmatically in FileDialog is overridden during navigation, XToolkit
 * @requires (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileNameOverrideTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.FileDialog;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JFrame;
import javax.swing.JTextField;

public class FileNameOverrideTest {
    private final static String fileName = "input";
    private final static String clickDirName = "Directory for double click";
    private final static String dirPath = System.getProperty("user.dir");;
    private static Button showBtn;
    private static FileDialog fd;

    private static JFrame initialize() {
        JFrame frame = new JFrame("File Name Override Test Frame");
        frame.setLayout(new BorderLayout());
        JTextField textOutput = new JTextField(30);
        frame.add(textOutput, BorderLayout.NORTH);

        fd = new FileDialog(frame, "Open");

        showBtn = new Button("Show File Dialog");
        showBtn.addActionListener(w -> {
            fd.setFile(fileName);
            fd.setDirectory(dirPath);
            fd.setVisible(true);
            String output = fd.getFile();
            if (fileName.equals(output)) {
                textOutput.setText("TEST PASSED");
            } else {
                textOutput.setText("TEST FAILED (output file - " + output + ")");
            }
        });
        frame.add(showBtn, BorderLayout.SOUTH);

        try {
            File tmpFileUp = new File(dirPath + File.separator + fileName);
            File tmpDir = new File(dirPath + File.separator + clickDirName);
            File tmpFileIn = new File(tmpDir.getAbsolutePath() + File.separator + fileName);
            tmpDir.mkdir();
            tmpFileUp.createNewFile();
            tmpFileIn.createNewFile();
        } catch (IOException ex) {
            throw new RuntimeException("Cannot create test folder", ex);
        }

        frame.pack();
        return frame;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        String instructions = """
                1) Click on 'Show File Dialog' button. A file dialog will come up.
                2) Double-click on '$clickDirName' and click OK.
                3) The text in the text field will indicate if test is passed or failed.
                """;

        String toolkit = Toolkit.getDefaultToolkit().getClass().getName();
        if (!toolkit.equals("sun.awt.X11.XToolkit")) {
            instructions = """
                    The test is not applicable for $toolkit. Press Pass.
                    """. replace("$toolkit", toolkit);
        }

        PassFailJFrame.builder()
                .title("File Dialog Return Test Instructions")
                .instructions(instructions)
                .rows((int) instructions.lines().count() + 1)
                .columns(50)
                .testUI(FileNameOverrideTest::initialize)
                .build()
                .awaitAndCheck();
    }
}
