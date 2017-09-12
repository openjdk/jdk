/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
   @bug 6868611
   @summary FileSystemView throws NullPointerException
   @author Pavel Porvatov
   @run main bug6868611
*/

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.io.File;

public class bug6868611 {
    private static final int COUNT = 1000;

    public static void main(String[] args) throws Exception {
        String tempDirProp = System.getProperty("java.io.tmpdir");

        final String tempDir = tempDirProp == null || !new File(tempDirProp).isDirectory() ?
            System.getProperty("user.home") : tempDirProp;

        System.out.println("Temp directory: " + tempDir);

        // Create 1000 files
        for (int i = 0; i < 1000; i++) {
            new File(tempDir, "temp" + i).createNewFile();
        }

        // Init default FileSystemView
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                FileSystemView.getFileSystemView().getFiles(new File(tempDir), false);
            }
        });

        for (int i = 0; i < COUNT; i++) {
            Thread thread = new MyThread(tempDir);

            thread.start();

            Thread.sleep((long) (Math.random() * 100));

            thread.interrupt();

            if (i % 100 == 0) {
                System.out.print("*");
            }
        }

        System.out.println();

        // Remove 1000 files
        for (int i = 0; i < 1000; i++) {
            new File(tempDir, "temp" + i).delete();
        }
    }

    private static class MyThread extends Thread {
        private final String dir;

        private MyThread(String dir) {
            this.dir = dir;
        }

        public void run() {
            FileSystemView fileSystemView = FileSystemView.getFileSystemView();

            fileSystemView.getFiles(new File(dir), false);
        }
    }
}
