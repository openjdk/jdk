/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.InvocationTargetException;
import javax.swing.JButton;

/*
 * @test
 * @bug 8026869
 * @summary Support apple.awt.use-file-dialog-packages property.
 * @requires (os.family == "mac")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileDialogForPackages
*/

public class FileDialogForPackages {

    private static JButton initialize() {
        System.setProperty("apple.awt.use-file-dialog-packages", "true");

        FileDialog fd = new FileDialog((Frame) null, "Open");
        String APPLICATIONS_FOLDER = "/Applications";
        fd.setDirectory(APPLICATIONS_FOLDER);

        JButton showBtn = new JButton("Show File Dialog");
        showBtn.addActionListener(e -> {
            fd.setVisible(true);
            String output = fd.getFile();
            if (output != null) {
                PassFailJFrame.log(output + " is selected\n");
            }
        });
        return showBtn;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        String instructions = """
                1) Click on 'Show File Dialog' button. A file dialog will come up.
                2) Navigate to the Applications folder if not already there.
                3) Check that the application bundles can be selected
                   but can not be navigated.
                4) If it's true then press Pass, otherwise press Fail.
                """;

        PassFailJFrame.builder()
                .title("Directory File Dialog Test Instructions")
                .instructions(instructions)
                .rows((int) instructions.lines().count() + 1)
                .columns(40)
                .logArea(8)
                .splitUIBottom(FileDialogForPackages::initialize)
                .build()
                .awaitAndCheck();
    }
}
