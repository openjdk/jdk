/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4974135
 * @summary FileDialog should open current directory by default.
 * @requires (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileDialogOpenDirTest
 */

import java.awt.Button;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JFrame;

public class FileDialogOpenDirTest {
    private static JFrame initialize() {
        System.setProperty("sun.awt.disableGtkFileDialogs", "true");

        JFrame frame = new JFrame("Open Directory File Dialog Test Frame");
        Button open = new Button("Open File Dialog");
        open.addActionListener(e -> {
            new FileDialog(frame).show();
        });

        frame.setLayout(new FlowLayout());
        frame.add(open);
        frame.pack();
        return frame;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        String curdir = System.getProperty("user.dir");
        String instructions = """
                        After test started you will see 'Test Frame' with a button inside.
                        Click the button to open FileDialog.
                        Verify that the directory opened is current directory, that is:
                        $curdir,
                        If so press Pass, otherwise Fail
                        """.replace("$curdir", curdir);

        String toolkit = Toolkit.getDefaultToolkit().getClass().getName();
        if (!toolkit.equals("sun.awt.X11.XToolkit")) {
            instructions = """
                    The test is not applicable for $toolkit. Press Pass.
                    """. replace("$toolkit", toolkit);
        }

        PassFailJFrame.builder()
                .title("Directory File Dialog Test Instructions")
                .instructions(instructions)
                .rows((int) instructions.lines().count() + 1)
                .columns(40)
                .testUI(FileDialogOpenDirTest::initialize)
                .build()
                .awaitAndCheck();
    }
}
