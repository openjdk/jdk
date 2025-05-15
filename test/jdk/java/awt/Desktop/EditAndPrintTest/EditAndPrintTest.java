/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @key printer
 * @bug 6255196
 * @summary  Verifies the function of methods edit(java.io.File file) and
 *           print(java.io.File file)
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @run main/manual EditAndPrintTest
 */

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JPanel;

import jtreg.SkippedException;

public class EditAndPrintTest extends JPanel {

    static final String INSTRUCTIONS = """
            This test tries to edit and print a directory, which will expectedly raise IOException.
            Then this test would edit and print a .txt file, which should be successful.
            After test execution close the editor if it was launched by test.
            If you see any EXCEPTION messages in the output press FAIL.
            """;

    static Desktop desktop;

    public EditAndPrintTest() {
        /*
         * Part 1: print or edit a directory, which should throw an IOException.
         */
        File userHome = new File(System.getProperty("user.home"));
        try {
            if (desktop.isSupported(Action.EDIT)) {
                PassFailJFrame.log("Trying to edit " + userHome);
                desktop.edit(userHome);
                PassFailJFrame.log("No exception has been thrown for editing " +
                        "directory " + userHome.getPath());
                PassFailJFrame.log("Test failed.");
            } else {
                PassFailJFrame.log("Action EDIT is unsupported.");
            }
        } catch (IOException e) {
            PassFailJFrame.log("Expected IOException is caught.");
        }

        try {
            if (desktop.isSupported(Action.PRINT)) {
                PassFailJFrame.log("Trying to print " + userHome);
                desktop.print(userHome);
                PassFailJFrame.log("No exception has been thrown for printing " +
                        "directory " + userHome.getPath());
                PassFailJFrame.log("Test failed.");
            } else {
                PassFailJFrame.log("Action PRINT is unsupported.\n");
            }
        } catch (IOException e) {
            PassFailJFrame.log("Expected IOException is caught.");
        }

        /*
         * Part 2: print or edit a normal .txt file, which may succeed if there
         * is associated application to print or edit the given file. It fails
         * otherwise.
         */
        // Create a temp .txt file for test.
        String testFilePath = System.getProperty("java.io.tmpdir") + File.separator + "JDIC-test.txt";
        File testFile = null;
        try {
            PassFailJFrame.log("Creating temporary file.");
            testFile = File.createTempFile("JDIC-test", ".txt", new File(System.getProperty("java.io.tmpdir")));
            testFile.deleteOnExit();
            FileWriter writer = new FileWriter(testFile);
            writer.write("This is a temp file used to test print() method of Desktop.");
            writer.flush();
            writer.close();
        } catch (IOException ioe){
            PassFailJFrame.log("EXCEPTION: " + ioe.getMessage());
            PassFailJFrame.forceFail("Failed to create temp file for testing.");
        }

        try {
            if (desktop.isSupported(Action.EDIT)) {
                PassFailJFrame.log("Try to edit " + testFile);
                desktop.edit(testFile);
                PassFailJFrame.log("Succeed.");
            }
        } catch (IOException e) {
            PassFailJFrame.log("EXCEPTION: " + e.getMessage());
        }

        try {
            if (desktop.isSupported(Action.PRINT)) {
                PassFailJFrame.log("Trying to print " + testFile);
                desktop.print(testFile);
                PassFailJFrame.log("Succeed.");
            }
        } catch (IOException e) {
            PassFailJFrame.log("EXCEPTION: " + e.getMessage());
        }
    }

    public static void main(String args[]) throws InterruptedException,
            InvocationTargetException {
        if (!Desktop.isDesktopSupported()) {
            throw new SkippedException("Class java.awt.Desktop is not supported " +
                    "on current platform. Further testing will not be performed");
        }

        desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Action.PRINT) && !desktop.isSupported(Action.EDIT)) {
            throw new SkippedException("Neither EDIT nor PRINT actions are supported. Nothing to test.");
        }

        PassFailJFrame.builder()
                .title("Edit and Print test")
                .splitUI(EditAndPrintTest::new)
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(60)
                .logArea()
                .build()
                .awaitAndCheck();
    }
}
