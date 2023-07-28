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
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import java.awt.BorderLayout;

import java.util.Locale;
import java.util.ResourceBundle;

/*
 * @test
 * @bug 8312075
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test to check if created newFolder is updated with
 *          changing Locale.
 * @run main/manual FileChooserNewFolderLocaleTest
 */

public class FileChooserNewFolderLocaleTest {
    static JFrame frame;
    static JFileChooser jfc;
    static PassFailJFrame passFailJFrame;
    static ResourceBundle res;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    initialize();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        passFailJFrame.awaitAndCheck();
    }

    private static void initialize() throws Exception {
        setLocale("fr");
        //Initialize the components
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. Click on New Folder button to create new Folder.
                2. If the created New Folder name is "Nouveau dossier"
                   (set from bundle_fr properties file) test is PASS.
                3. created New Folder name is "New Folder" then test is FAIL.
                """;
        frame = new JFrame("JFileChooser NewFolder Locale test");
        jfc = new JFileChooser();

        passFailJFrame = new PassFailJFrame("Test Instructions",
                INSTRUCTIONS, 5L, 8, 40);
        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(jfc, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }

    private static void setLocale(String locale) {
        boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");
        String newFolderKey;
        String newFolderSubKey;

        if (IS_WINDOWS) {
            newFolderKey = "FileChooser.win32.newFolder";
            newFolderSubKey = "FileChooser.win32.newFolder.subsequent";
        } else {
            newFolderKey = "FileChooser.other.newFolder";
            newFolderSubKey = "FileChooser.other.newFolder.subsequent";
        }

        res = ResourceBundle.getBundle("bundle", new Locale(locale));
        UIManager.put("FileChooser.acceptAllFileFilterText", res.getString("accept_all"));
        UIManager.put("FileChooser.directoryOpenButtonText", res.getString("open_text"));
        UIManager.put("FileChooser.openButtonText", res.getString("open_text"));
        UIManager.put("FileChooser.saveButtonText", res.getString("save_text"));
        UIManager.put("FileChooser.cancelButtonText", res.getString("cancel_text"));
        UIManager.put("FileChooser.lookInLabelText", res.getString("file_look"));
        UIManager.put("FileChooser.saveInLabelText", res.getString("file_save"));
        UIManager.put("FileChooser.fileNameLabelText", res.getString("file_name"));
        UIManager.put("FileChooser.filesOfTypeLabelText", res.getString("file_type"));
        UIManager.put(newFolderKey, res.getString("new_folder"));
        UIManager.put(newFolderSubKey, res.getString("new_folder")
                + " ({0})");
    }
}
