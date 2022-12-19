/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.Icon;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileSystemView;

/*
 * @test
 * @bug 8296198
 * @key headful
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test to check if the Link to a folder is traversable with custom
 * FileSystemView is valid on ValueChanged property listener.
 * @run main/manual CustomFSVLinkTest
 */
public class CustomFSVLinkTest {
    static JFrame frame;
    static JFileChooser jfc;

    static PassFailJFrame passFailJFrame;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    initialize();
                } catch (InterruptedException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        passFailJFrame.awaitAndCheck();
    }

    static void initialize() throws InterruptedException, InvocationTargetException {
        //Initialize the components
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. Create a link to a any valid folder.
                2. Navigate to the linked folder through the link created
                (From FileChooser).
                3. If "link" can't be traversed or if its absolute path is null
                   click FAIL. If "link" can be traversed then click PASS.
                """;
        frame = new JFrame("JFileChooser Link test");
        jfc = new JFileChooser(new MyFileSystemView());
        passFailJFrame = new PassFailJFrame("Test Instructions", INSTRUCTIONS, 5L, 8, 40);

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jfc.setDialogType(JFileChooser.CUSTOM_DIALOG);

        frame.add(jfc, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }

    private static class MyFileSystemView extends FileSystemView {
        FileSystemView delegate;

        MyFileSystemView() {
            delegate = FileSystemView.getFileSystemView();
        }

        @Override
        public File createNewFolder(File containingDir) throws IOException {
            return delegate.createNewFolder(containingDir);
        }

        @Override
        public boolean isRoot(File f) {
            return delegate.isRoot(f);
        }

        @Override
        public Boolean isTraversable(File f) {
            return delegate.isTraversable(f);
        }

        @Override
        public String getSystemDisplayName(File f) {
            return delegate.getSystemDisplayName(f);
        }

        @Override
        public String getSystemTypeDescription(File f) {
            return delegate.getSystemTypeDescription(f);
        }

        @Override
        public Icon getSystemIcon(File f) {
            return delegate.getSystemIcon(f);
        }

        @Override
        public boolean isParent(File folder, File file) {
            return delegate.isParent(folder, file);
        }

        @Override
        public File getChild(File parent, String fileName) {
            return delegate.getChild(parent, fileName);
        }

        @Override
        public boolean isFileSystem(File f) {
            return delegate.isFileSystem(f);
        }

        @Override
        public boolean isHiddenFile(File f) {
            return delegate.isHiddenFile(f);
        }

        @Override
        public boolean isFileSystemRoot(File dir) {
            return delegate.isFileSystemRoot(dir);
        }

        @Override
        public boolean isDrive(File dir) {
            return delegate.isDrive(dir);
        }

        @Override
        public boolean isFloppyDrive(File dir) {
            return delegate.isFloppyDrive(dir);
        }

        @Override
        public boolean isComputerNode(File dir) {
            return delegate.isComputerNode(dir);
        }

        @Override
        public File[] getRoots() {
            return delegate.getRoots();
        }

        @Override
        public File getHomeDirectory() {
            return delegate.getHomeDirectory();
        }

        @Override
        public File getDefaultDirectory() {
            return delegate.getDefaultDirectory();
        }

        @Override
        public File createFileObject(File dir, String filename) {
            return delegate.createFileObject(dir, filename);
        }

        @Override
        public File createFileObject(String path) {
            return delegate.createFileObject(path);
        }

        @Override
        public File[] getFiles(File dir, boolean useFileHiding) {
            return delegate.getFiles(dir, useFileHiding);
        }

        @Override
        public File getParentDirectory(File dir) {
            return delegate.getParentDirectory(dir);
        }

        @Override
        public File[] getChooserComboBoxFiles() {
            return delegate.getChooserComboBoxFiles();
        }

        @Override
        public boolean isLink(File file) {
            return delegate.isLink(file);
        }

        @Override
        public File getLinkLocation(File file) throws FileNotFoundException {
            return delegate.getLinkLocation(file);
        }
    }
}
