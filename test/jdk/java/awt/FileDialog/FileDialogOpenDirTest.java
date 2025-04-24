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

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Toolkit;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JButton;
import jtreg.SkippedException;

/*
 * @test
 * @bug 4974135
 * @summary FileDialog should open current directory by default.
 * @requires (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @library /test/lib
 * @build PassFailJFrame
 * @build jtreg.SkippedException
 * @run main/manual FileDialogOpenDirTest
 */

public class FileDialogOpenDirTest {
    private static JButton initialize() {
        System.setProperty("sun.awt.disableGtkFileDialogs", "true");

        JButton open = new JButton("Open File Dialog");
        open.addActionListener(e -> {
            new FileDialog((Frame) null).setVisible(true);
        });

        return open;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        String toolkit = Toolkit.getDefaultToolkit().getClass().getName();
        if (!toolkit.equals("sun.awt.X11.XToolkit")) {
            throw new SkippedException("Test is not designed for toolkit " + toolkit);
        }

        String curdir = System.getProperty("user.dir");
        String instructions = """
                        Click the \"Open File Dialog\" button below to open FileDialog.
                        Verify that the directory opened is current directory, that is:
                        $curdir,
                        If so press Pass, otherwise press Fail
                        """.replace("$curdir", curdir);

        PassFailJFrame.builder()
                .title("Directory File Dialog Test Instructions")
                .instructions(instructions)
                .rows((int) instructions.lines().count() + 1)
                .columns(40)
                .splitUIBottom(FileDialogOpenDirTest::initialize)
                .build()
                .awaitAndCheck();
    }
}
