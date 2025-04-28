/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 8352970
   @requires (os.family == "windows")
   @summary Basic sanity test for FileSystemView.getChooserComboBoxFiles().
  */

import java.io.File;
import java.util.Arrays;
import javax.swing.filechooser.FileSystemView;

public class FileSystemViewFilesTest {

    public static void main(String[] args) throws Exception {

        FileSystemView fsv = FileSystemView.getFileSystemView();
        File[] roots = fsv.getRoots();
        File desktop = Arrays.asList(roots).stream()
            .filter(f -> f.getName().equals("Desktop"))
            .findFirst()
            .orElse(null);
        if (desktop == null) {
            System.out.println("No desktop available in " + roots.length + " roots");
            return;
        }

        File[] chooserFiles = fsv.getChooserComboBoxFiles();
        boolean found = Arrays.asList(chooserFiles).stream()
            .anyMatch(f -> f.equals(desktop));
        if (!found) {
            throw new RuntimeException("Desktop not included in chooser combo box files.");
        }
    }

}
