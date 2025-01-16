/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;

/*
 * @test
 * @bug 6698013
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary JFileChooser can no longer navigate non-local file systems.
 * @run main/manual bug6698013
 */

public class bug6698013 {

    final static VirtualFile root = new VirtualFile("testdir", true);

    public static void main(String[] args) throws Exception {
        String instructions = """
                1. Go into 'subdir' folder via double click
                2. Return to parent directory
                3. Go into 'subdir' folder: select 'subdir' folder and press the 'Open' button
                If both methods of navigating into the subdir work, pass test. Otherwise fail.""";

        PassFailJFrame pfframe = PassFailJFrame.builder()
                .title("bug6698013")
                .instructions(instructions)
                .rows(25)
                .columns(40)
                .testTimeOut(10)
                .build();

        SwingUtilities.invokeAndWait(() -> {
            JFileChooser chooser = new JFileChooser(new VirtualFileSystemView());
            chooser.setCurrentDirectory(root);
            chooser.showOpenDialog(null);
        });

        pfframe.awaitAndCheck();
    }
}

class VirtualFileSystemView extends FileSystemView {
    final static VirtualFile rootFile = new VirtualFile("testdir/test.txt", false);
    final static VirtualFile subdir = new VirtualFile("testdir/subdir", true);
    final static VirtualFile subdirFile = new VirtualFile("testdir/subdir/subtest.txt", false);

    public boolean isRoot(File dir) {
        return bug6698013.root.equals(dir);
    }

    public File createNewFolder(File dir) {
        return null;
    }

    public File[] getRoots() {
        return new File[]{bug6698013.root};
    }

    public boolean isDrive(File dir) {
        return false;
    }

    public boolean isFloppyDrive(File dir) {
        return false;
    }

    public File getParentDirectory(File dir) {
        if (dir == null) {
            return null;
        }

        return new VirtualFile(dir.getPath(), true).getParentFile();
    }

    public File[] getFiles(File dir, boolean hide_hidden) {
        if (dir.equals(bug6698013.root)) {
            return new File[]{rootFile, subdir};
        }

        if (dir.equals(subdir)) {
            return new File[]{subdirFile};
        }

        return null;
    }

    public File getHomeDirectory() {
        return bug6698013.root;
    }

    public File getDefaultDirectory() {
        return getHomeDirectory();
    }

    public String getSystemDisplayName(File file) {
        return file.getName();
    }

    public Boolean isTraversable(File file) {
        return Boolean.valueOf(file.isDirectory());
    }
}

/**
 * A Virtual File. Contains a path and a directory flag that
 * represents the location of a virtual file to be contained in the
 * Virtual FileSystemView.
 */
class VirtualFile extends File {

    private static final long serialVersionUID = 0L;

    private String path;

    private boolean directory;

    public VirtualFile(String path, boolean directory) {
        super(path);
        this.path = path;
        this.directory = directory;
    }

    public File getParentFile() {
        int index = path.lastIndexOf('/');

        if (index == -1) {
            return null;
        }

        return new VirtualFile(path.substring(0, index), true);
    }

    public File getCanonicalFile() {
        return this;
    }

    public String getParent() {
        File parent_file = getParentFile();

        return parent_file == null ? null : parent_file.getPath();
    }

    public String getName() {
        int index = path.lastIndexOf('/');

        return index == -1 ? path : path.substring(index + 1);
    }

    public String getPath() {
        return path;
    }

    public String getAbsolutePath() {
        return path;
    }

    public String getCanonicalPath() {
        return path;
    }

    public String toString() {
        return path;
    }

    public boolean equals(Object obj) {
        return obj instanceof VirtualFile && path.equals(obj.toString());
    }

    public int hashCode() {
        return path.hashCode();
    }

    public boolean canWrite() {
        return true;
    }

    public boolean isDirectory() {
        return directory;
    }

    public boolean exists() {
        return true;
    }
}
