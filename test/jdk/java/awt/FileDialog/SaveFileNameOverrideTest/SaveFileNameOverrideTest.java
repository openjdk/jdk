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

import javax.swing.JButton;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;

/*
 * @test
 * @bug 6998877 8022531
 * @summary After double-click on the folder names, FileNameOverrideTest FAILED
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SaveFileNameOverrideTest
 */

public class SaveFileNameOverrideTest {

    private final static String clickDirName = "Directory for double click";
    private static final String INSTRUCTIONS =
            """
            1) Click on the 'Show File Dialog' button. A file dialog will appear.
            2) Double-click on '%s'
            3) Click on a confirmation button.
            (It can be 'OK', 'Save' or any other name depending on the platform).
            3) The test will automatically pass or fail.
            """
            .formatted(clickDirName);
    private final static String dirPath = ".";

    public static void main(String[] args) throws Exception {
        System.out.println(System.getProperties());
        System.out.println(new File(dirPath).getAbsolutePath());
        File tmpDir = new File(dirPath + File.separator + clickDirName);
        if (!tmpDir.mkdir()) {
            throw new RuntimeException("Cannot create directory.");
        }
        tmpDir.deleteOnExit();

        PassFailJFrame
                .builder()
                .title("SaveFileNameOverrideTest Instructions")
                .instructions(INSTRUCTIONS)
                .splitUIRight(SaveFileNameOverrideTest::getButton)
                .rows(8)
                .columns(40)
                .build()
                .awaitAndCheck();
    }

    public static JButton getButton() {
        JButton showBtn = new JButton("Show File Dialog");
        showBtn.addActionListener(e -> {
            FileDialog fd =
                    new FileDialog((Frame) null, "Save", FileDialog.SAVE);

            fd.setFile("input");
            fd.setDirectory(new File(dirPath).getAbsolutePath());
            fd.setVisible(true);

            String output = fd.getFile();
            if ("input".equals(output)) {
                PassFailJFrame.forcePass();
            } else {
                PassFailJFrame.forceFail("TEST FAILED (output file - " + output + ")");
            }
        });
        return showBtn;
    }
}
