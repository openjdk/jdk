/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFileChooser;
import javax.swing.UIManager;
import java.io.File;

/*
 * @test
 * @bug 8312075
 * @summary Test to check if created newFolder is updated with
 *          changing Locale.
 * @run main/othervm -Duser.language=en -Duser.country=US FileChooserNewFolderLocaleTest
 */
public class FileChooserNewFolderLocaleTest {
    static final String FRENCH_NEW_FOLDER = "Nouveau dossier";

    public static void main(String[] args) throws Exception {
        File newFolderEnglish = null;
        File newFolderFrench = null;
        String newFolderKey;

        boolean isWindows =
                System.getProperty("os.name").toLowerCase().contains("windows");
        if (isWindows) {
            newFolderKey = "FileChooser.win32.newFolder";
        } else {
            newFolderKey = "FileChooser.other.newFolder";
        }

        String englishNewFolder = UIManager.getString(newFolderKey);
        try {
            JFileChooser fileChooser = new JFileChooser();
            File currentDir = new File(".");

            newFolderEnglish =
                    fileChooser.getFileSystemView().createNewFolder(currentDir);
            if (!newFolderEnglish.getName().contains(englishNewFolder)) {
                throw new RuntimeException("English Locale verification Failed");
            }

            UIManager.put(newFolderKey, FRENCH_NEW_FOLDER);

            newFolderFrench =
                    fileChooser.getFileSystemView().createNewFolder(currentDir);
            if (!newFolderFrench.getName().contains(FRENCH_NEW_FOLDER)) {
                throw new RuntimeException("Failed to update to French Locale");
            }
        } finally {
            deleteFolder(newFolderEnglish);
            deleteFolder(newFolderFrench);
        }
        System.out.println("Test Passed!");
    }

    public static void deleteFolder(File file) {
        if (file != null && !(file.delete())) {
            System.out.println("Failed to delete file : " + file.getName());
        }
    }
}
