/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.FilenameFilter;

/*
 * @test
 * @bug 4364256
 * @summary Test to File Dialog filter
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileDialogFilterTest
 */

public class FileDialogFilterTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                   Run the test, make sure a file dialog
                   comes up with no crash. If the file dialog
                   comes up successfully then press PASS, else FAIL.
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .build()
                .awaitAndCheck();
    }

    public static FileDialog initialize() {
        FileDialog fDlg = new FileDialog(new Frame());
        fDlg.addNotify();
        fDlg.setFilenameFilter(new MyFilter());
        return fDlg;
    }
}

class MyFilter implements FilenameFilter {
    public boolean accept(File dir, String name) {
        return true;
    }
}
