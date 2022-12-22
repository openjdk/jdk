/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4400728
 * @summary This testcase tests CCC4400728 request, checks whether JFileChooser
 *          constructor set correct default directory or not, it is typically
 *          the "Documents" folder on Windows, and the user's home directory
 *          on Unix.
 * @run main JFileChooserDefaultDirectoryTest
 */
public class JFileChooserDefaultDirectoryTest {

    public static void main(String[] args) throws Exception {
        final AtomicReference<String> actual = new AtomicReference<>("");
        SwingUtilities.invokeAndWait(() -> {
            JFileChooser jFileChooser = new JFileChooser();
            actual.set(jFileChooser.getFileSystemView()
                                   .getDefaultDirectory()
                                   .getName());
        });
        String actualDefaultDirectory = actual.get();
        final boolean isWindows = System.getProperty("os.name")
                                        .startsWith("Windows");
        if (isWindows) {
            if (actualDefaultDirectory.equals("Documents")) {
                System.out.println("Test Passed");
            } else {
                throw new RuntimeException(
                        "Test Failed, JFileChooser constructor sets incorrect" +
                        " default directory, actual = " +
                        actualDefaultDirectory +
                        " expected should be 'Documents'");
            }
        } else {
            final String userHome = System.getProperty("user.home");
            System.out.println("UserHome dir = " + userHome);
            String expectedDefaultDirectory = new File(userHome).getName();
            if (expectedDefaultDirectory.equals(actualDefaultDirectory)) {
                System.out.println("Test Passed");
            } else {
                throw new RuntimeException(
                        "Test Failed, JFileChooser constructor sets incorrect" +
                        " default directory, actual = " +
                        actualDefaultDirectory + " expected = " +
                        expectedDefaultDirectory);
            }
        }
    }

}
