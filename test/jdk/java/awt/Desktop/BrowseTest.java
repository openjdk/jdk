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
 * @bug 6255196
 * @summary  Verifies the function of method browse(java.net.URI uri).
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @run main/manual BrowseTest
 */

import java.awt.Desktop;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import javax.swing.JPanel;

import jtreg.SkippedException;

public class BrowseTest extends JPanel {
    static final String INSTRUCTIONS = """
            This test could launch default file manager to open user's home
            directory, and default web browser to show the URL of java vendor.
            After test execution close the native file manager and web browser
            windows if they were launched by test.
            Also check output for any unexpected EXCEPTIONS,
            if you see any failure messages press Fail otherwise press Pass.
            """;

    public BrowseTest() {
        Desktop desktop = Desktop.getDesktop();

        URI dirURI = new File(System.getProperty("user.home")).toURI();
        URI webURI = URI.create(System.getProperty("java.vendor.url", "http://www.java.com"));
        boolean failed = false;
        try {
            PassFailJFrame.log("Try to browse " + dirURI + " ...");
            desktop.browse(dirURI);
            PassFailJFrame.log("Succeed.\n");
        } catch (Exception e) {
            PassFailJFrame.log("EXCEPTION: " + e.getMessage());
        }

        try {
            PassFailJFrame.log("Try to browse " + webURI + " ...");
            desktop.browse(webURI);
            PassFailJFrame.log("Succeed.\n");
        } catch (Exception e) {
            PassFailJFrame.log("EXCEPTION: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        if (!Desktop.isDesktopSupported()) {
            throw new SkippedException("Class java.awt.Desktop is not supported " +
                    "on current platform. Further testing will not be performed");
        }

        PassFailJFrame.builder()
                .title("Browser Test")
                .splitUI(BrowseTest::new)
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(40)
                .logArea()
                .build()
                .awaitAndCheck();
    }
}
