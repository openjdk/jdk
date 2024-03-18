/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Panel;

/*
 * @test
 * @bug 6242241
 * @summary Tests TransferFlavor that supports DnD of MS Outlook attachments.
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DnDFileGroupDescriptor
 */

public class DnDFileGroupDescriptor {
    private static final String INSTRUCTIONS = """
            When the test starts, a RED panel appears.
            1. Start MS Outlook program. Find and open the mail form with attachments.

            2. Select attachments from the mail and drag into a red field of applet.
                When the mouse enters the field during the process of drag, the application
                should change the cursor form to OLE-copy and field color to yellow.

            3. Release the mouse button (drop attachments) over the field.
                File paths in temporary folder should appear.
                You should be able to repeat this operation multiple times.

            If the above is the case then press PASS, else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(DnDFileGroupDescriptor::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame frame = new Frame("Test MS Outlook Mail Attachments DnD");
        Panel mainPanel = new Panel();
        mainPanel.setLayout(new BorderLayout());

        Component dropTarget = new DnDTarget(Color.RED, Color.YELLOW);
        mainPanel.add(dropTarget, "Center");

        frame.add(mainPanel);
        frame.setSize(400, 200);
        frame.setAlwaysOnTop(true);
        return frame;
    }
}
