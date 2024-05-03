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

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JButton;
import jtreg.SkippedException;

/*
 * @test
 * @bug 6260659
 * @summary File Name set programmatically in FileDialog is overridden during navigation, XToolkit
 * @requires (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @library /test/lib
 * @build PassFailJFrame
 * @build jtreg.SkippedException
 * @run main/manual FileNameOverrideTest
 */

public class FileNameOverrideTest {
    private final static String clickDirName = "Directory for double click";

    private static JButton initialize() {
        final String fileName = "input";
        final String dirPath = System.getProperty("user.dir");;

        JButton showBtn = new JButton("Show File Dialog");
        showBtn.addActionListener(w -> {
            FileDialog fd = new FileDialog((Frame) null, "Open");
            fd.setFile(fileName);
            fd.setDirectory(dirPath);
            fd.setVisible(true);
            String output = fd.getFile();
            fd.dispose();
            if (fileName.equals(output)) {
                PassFailJFrame.forcePass();
            } else {
                PassFailJFrame.forceFail("File name mismatch: "
                        + fileName + " vs " + output);
            }
        });

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

        return showBtn;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        String instructions = """
                1) Click on 'Show File Dialog' button. A file dialog will come up.
                2) Double-click on '$clickDirName' and click OK or Open
                   to confirm selection (label on the button depends on the current window manager).
                3) Test will pass or fail automatically.
                """.replace("$clickDirName", clickDirName);

        String toolkit = Toolkit.getDefaultToolkit().getClass().getName();
        if (!toolkit.equals("sun.awt.X11.XToolkit")) {
            throw new SkippedException("Test is not designed for toolkit " + toolkit);
        }

        PassFailJFrame.builder()
                .title("File Dialog Return Test Instructions")
                .instructions(instructions)
                .rows((int) instructions.lines().count() + 1)
                .columns(50)
                .splitUIBottom(FileNameOverrideTest::initialize)
                .build()
                .awaitAndCheck();
    }
}
