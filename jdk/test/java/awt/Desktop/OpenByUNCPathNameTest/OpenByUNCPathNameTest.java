/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
   @bug 6550588
   @summary java.awt.Desktop cannot open file with Windows UNC filename
   @author Anton Litvinov
*/

import java.awt.*;
import java.awt.event.*;
import java.io.*;

public class OpenByUNCPathNameTest {
    private static boolean validatePlatform() {
        String osName = System.getProperty("os.name");
        if (osName == null) {
            throw new RuntimeException("Name of the current OS could not be retrieved.");
        }
        return osName.startsWith("Windows");
    }

    private static void openFile() throws IOException {
        if (!Desktop.isDesktopSupported()) {
            System.out.println("java.awt.Desktop is not supported on this platform.");
        } else {
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) {
                System.out.println("Action.OPEN is not supported on this platform.");
                return;
            }
            File file = File.createTempFile("Read Me File", ".txt");
            try {
                // Test opening of the file with Windows local file path.
                desktop.open(file);
                Robot robot = null;
                try {
                    Thread.sleep(5000);
                    robot = new Robot();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                pressAltF4Keys(robot);

                // Test opening of the file with Windows UNC pathname.
                String uncFilePath = "\\\\127.0.0.1\\" + file.getAbsolutePath().replace(':', '$');
                File uncFile = new File(uncFilePath);
                if (!uncFile.exists()) {
                    throw new RuntimeException(String.format(
                        "File with UNC pathname '%s' does not exist.", uncFilePath));
                }
                desktop.open(uncFile);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
                pressAltF4Keys(robot);
            } finally {
                file.delete();
            }
        }
    }

    private static void pressAltF4Keys(Robot robot) {
        if (robot != null) {
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_F4);
            robot.delay(50);
            robot.keyRelease(KeyEvent.VK_F4);
            robot.keyRelease(KeyEvent.VK_ALT);
        }
    }

    public static void main(String[] args) throws IOException {
        if (!validatePlatform()) {
            System.out.println("This test is only for MS Windows OS.");
        } else {
            openFile();
        }
    }
}
