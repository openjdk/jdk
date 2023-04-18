/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
  @test
  @bug 4221123
  @summary Why Dialog can't be an owner of FileDialog?
  @key headful
  @run main DialogAsParentOfFileDialog
*/

import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.lang.reflect.InvocationTargetException;

public class DialogAsParentOfFileDialog {
    FileDialog fdialog;

    public void start () {
        StringBuilder errors = new StringBuilder();
        String nl = System.lineSeparator();
        Dialog dlg;
        String title;
        int mode;
        boolean passed;

        System.out.println("DialogAsParentOfFileDialog");

        /*
         * public FileDialog(Dialog parent),
         * checks owner and default settings.
         */
        System.out.print("\ttest 01: ");
        dlg = new Dialog(new Frame());
        fdialog = new FileDialog(dlg);
        passed =
            fdialog.getOwner() == dlg
            && fdialog.isModal()
            && fdialog.getTitle().equals("")
            && fdialog.getMode() == FileDialog.LOAD
            && fdialog.getFile() == null
            && fdialog.getDirectory() == null
            && fdialog.getFilenameFilter() == null;
        System.out.println(passed ? "passed" : "FAILED");
        if (!passed) {
            errors.append(nl);
            errors.append("DialogAsParentOfFileDialog FAILED");
        }

        /*
         * public FileDialog(Dialog parent, String title),
         * checks owner, title and default settings.
         */
        System.out.print("\ttest 02: ");
        dlg = new Dialog(new Frame());
        title = "Title";
        fdialog = new FileDialog(dlg, title);
        passed =
            fdialog.getOwner() == dlg
            && fdialog.isModal()
            && fdialog.getTitle().equals(title)
            && fdialog.getMode() == FileDialog.LOAD
            && fdialog.getFile() == null
            && fdialog.getDirectory() == null
            && fdialog.getFilenameFilter() == null;
        System.out.println(passed ? "passed" : "FAILED");
        if (!passed) {
            errors.append(nl);
            errors.append("DialogAsParentOfFileDialog FAILED");
        }

        /*
         * public FileDialog(Dialog parent, String title),
         * title: null.
         * expected results: FileDialog object with a null title
         */
        System.out.print("\ttest 03: ");
        dlg = new Dialog(new Frame());
        title = null;
        fdialog = new FileDialog(dlg, title);
        passed =
            fdialog.getOwner() == dlg
            && (fdialog.getTitle() == null
                || fdialog.getTitle().equals(""));
        System.out.println(passed ? "passed" : "FAILED");
        if (!passed) {
            errors.append(nl);
            errors.append("DialogAsParentOfFileDialog FAILED");
        }

        /*
         * public FileDialog(Dialog parent, String title, int mode),
         * checks owner, title and mode.
         */
        dlg = new Dialog(new Frame());
        title = "Title";

        System.out.print("\ttest 04: ");
        mode = FileDialog.SAVE;
        fdialog = new FileDialog(dlg, title, mode);
        passed =
            fdialog.getOwner() == dlg
            && fdialog.isModal()
            && fdialog.getTitle().equals(title)
            && fdialog.getMode() == mode
            && fdialog.getFile() == null
            && fdialog.getDirectory() == null
            && fdialog.getFilenameFilter() == null;
        System.out.println(passed ? "passed" : "FAILED");
        if (!passed) {
            errors.append(nl);
            errors.append("DialogAsParentOfFileDialog FAILED");
        }

        System.out.print("\ttest 05: ");
        mode = FileDialog.LOAD;
        fdialog = new FileDialog(dlg, title, mode);
        passed =
            fdialog.getOwner() == dlg
            && fdialog.isModal()
            && fdialog.getTitle().equals(title)
            && fdialog.getMode() == mode
            && fdialog.getFile() == null
            && fdialog.getDirectory() == null
            && fdialog.getFilenameFilter() == null;
        System.out.println(passed ? "passed" : "FAILED");
        if (!passed) {
            errors.append(nl);
            errors.append("DialogAsParentOfFileDialog FAILED");
        }

        /*
         * public FileDialog(Dialog parent, String title, int mode),
         * mode: Integer.MIN_VALUE, Integer.MIN_VALUE+1,
         *       Integer.MAX_VALUE-1, Integer.MAX_VALUE
         * expected results: IllegalArgumentException should be thrown
         */
        System.out.print("\ttest 06: ");
        dlg = new Dialog(new Frame());
        title = "Title";
        int[] modes = {Integer.MIN_VALUE, Integer.MIN_VALUE+1,
                       Integer.MAX_VALUE-1, Integer.MAX_VALUE};
        passed = true;
        for (int i = 0; i < modes.length; i++) {
            try {
                fdialog = new FileDialog(dlg, title, modes[i]);
                passed = false;
            } catch (IllegalArgumentException e) {}
        }
        System.out.println(passed ? "passed" : "FAILED");
        if (!passed) {
            errors.append(nl);
            errors.append("DialogAsParentOfFileDialog FAILED");
        }

        if (!errors.isEmpty()) {
            throw new RuntimeException("Following tests failed:" + errors);
        }
    }

    public static void main(String[] args) throws InterruptedException,
                                           InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            new DialogAsParentOfFileDialog().start();
        });
    }
}
