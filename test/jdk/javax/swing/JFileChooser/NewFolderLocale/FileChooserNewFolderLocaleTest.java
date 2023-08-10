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
import java.util.Locale;
import java.util.ResourceBundle;

/*
 * @test
 * @bug 8312075
 * @summary Test to check if created newFolder is updated with
 *          changing Locale.
 * @run main FileChooserNewFolderLocaleTest
 */

public class FileChooserNewFolderLocaleTest {
    static ResourceBundle res;
    static String newFolderKey;
    static String newFolderSubKey;

    public static void main(String[] args) throws Exception {
        File newFolderEnglish = null;
        File newFolderFrench = null;
        boolean IS_WINDOWS =
                System.getProperty("os.name").toLowerCase().contains("windows");
        if (IS_WINDOWS) {
            newFolderKey = "FileChooser.win32.newFolder";
            newFolderSubKey = "FileChooser.win32.newFolder.subsequent";
        } else {
            newFolderKey = "FileChooser.other.newFolder";
            newFolderSubKey = "FileChooser.other.newFolder.subsequent";
        }

        try {
            JFileChooser fileChooser = new JFileChooser();
            File currentDir = new File(".");
            newFolderEnglish =
                    fileChooser.getFileSystemView().createNewFolder(currentDir);

            setLocale("fr");
            fileChooser = new JFileChooser();
            newFolderFrench =
                    fileChooser.getFileSystemView().createNewFolder(currentDir);
            if( !newFolderFrench.getName().contains
                    (UIManager.getString(newFolderKey))) {
                throw new RuntimeException("Failed to update new Locale");
            }
        } finally {
            if (newFolderEnglish != null && newFolderEnglish.delete()) {
                System.out.println("Deleted the file: " +
                        newFolderEnglish.getName());
            } else {
                System.out.println("Failed to delete file : " +
                        newFolderEnglish.getName());
            }
            if (newFolderFrench != null && newFolderFrench.delete()) {
                System.out.println("Deleted the file: " +
                        newFolderFrench.getName());
            } else {
                System.out.println("Failed to delete file : " +
                        newFolderFrench.getName());
            }
        }
        System.out.println("Test Passed!");
    }

    private static void setLocale(String locale) {
        res = ResourceBundle.getBundle("bundle",
                new Locale(locale));
        UIManager.put("FileChooser.acceptAllFileFilterText",
                res.getString("accept_all"));
        UIManager.put("FileChooser.directoryOpenButtonText",
                res.getString("open_text"));
        UIManager.put("FileChooser.openButtonText",
                res.getString("open_text"));
        UIManager.put("FileChooser.saveButtonText",
                res.getString("save_text"));
        UIManager.put("FileChooser.cancelButtonText",
                res.getString("cancel_text"));
        UIManager.put("FileChooser.lookInLabelText",
                res.getString("file_look"));
        UIManager.put("FileChooser.saveInLabelText",
                res.getString("file_save"));
        UIManager.put("FileChooser.fileNameLabelText",
                res.getString("file_name"));
        UIManager.put("FileChooser.filesOfTypeLabelText",
                res.getString("file_type"));
        UIManager.put(newFolderKey,
                res.getString("new_folder"));
        UIManager.put(newFolderSubKey,
                res.getString("new_folder") + " ({0})");
    }
}
