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

import java.awt.Desktop;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import javax.swing.JPanel;

import jtreg.SkippedException;

/*
 * @test
 * @bug 6255196
 * @summary Verifies the function of methods mail() and mail(java.net.URI uri).
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @run main/manual MailTest
 */

public class MailTest extends JPanel {

    static final String INSTRUCTIONS = """
            This test could launch the mail client to compose mail
            with and without filling the message fields.
            After test execution close the mail composing windows if they
            were launched by test.
            If you see any unexpected EXCEPTION messages in the output
            press Fail. Otherwise press Pass.
            """;

    private MailTest() {
        Desktop desktop = Desktop.getDesktop();
        /*
         * Part 1: launch the mail composing window without a mailto URI.
         */
        try {
            desktop.mail();
        } catch (IOException e) {
            PassFailJFrame.log("EXCEPTION: " + e.getMessage());
        }

        /*
         * Part 2: launch the mail composing window with a mailto URI.
         */
        URI testURI = null;
        try {
            testURI = new URI("mailto", "foo@bar.com?subject=test subject" +
                    "&cc=foocc@bar.com&body=test body", null);
            desktop.mail(testURI);
        } catch (IOException e) {
            PassFailJFrame.log("EXCEPTION: " + e.getMessage());
        } catch (java.net.URISyntaxException use) {
            // Should not reach here.
            PassFailJFrame.log("EXCEPTION: " + use.getMessage());
        }

        /*
         * Part 3: try to launch the mail composing window with a URI with a
         * scheme which is not "mailto":
         *   http://java.net.
         * An IOException should be thrown in this case.
         */
        try {
            testURI = URI.create("http://java.com");
            PassFailJFrame.log("Try to mail: " + testURI);
            desktop.mail(testURI);
        } catch (IllegalArgumentException e) {
            PassFailJFrame.log("Caught expected IllegalArgumentException");
        } catch (IOException ioe) {
            PassFailJFrame.log("EXCEPTION: " + ioe.getMessage());
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        if (!Desktop.isDesktopSupported()) {
            throw new SkippedException("Class java.awt.Desktop is not supported " +
                    "on current platform. Further testing will not be performed");
        }

        if (!Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
            throw new SkippedException("Action.MAIL is not supported.");
        }

        PassFailJFrame.builder()
                .title("Mail Test")
                .splitUI(MailTest::new)
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(40)
                .logArea()
                .build()
                .awaitAndCheck();
    }
}
