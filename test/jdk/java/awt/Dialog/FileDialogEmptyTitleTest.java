/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4177831
 * @summary solaris: default FileDialog title is not empty
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileDialogEmptyTitleTest
 */

public class FileDialogEmptyTitleTest {
    static String instructions = """
            Test passes if title of file dialog is empty,
            otherwise test failed.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("FileDialogEmptyTitleTest")
                .instructions(instructions)
                .testTimeOut(5)
                .rows(10)
                .columns(35)
                .testUI(FileDialogEmptyTitleTest::createGUI)
                .build()
                .awaitAndCheck();
    }

    public static FileDialog createGUI() {
        Frame frame = new Frame("invisible dialog owner");
        FileDialog fileDialog = new FileDialog(frame);
        return fileDialog;
    }
}
