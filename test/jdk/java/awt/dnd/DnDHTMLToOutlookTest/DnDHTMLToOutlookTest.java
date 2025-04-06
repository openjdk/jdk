/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6392086
 * @summary Tests dnd to another screen
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DnDHTMLToOutlookTest
 */

public class DnDHTMLToOutlookTest {

    private static final String INSTRUCTIONS = """
            The window contains a yellow button. Click on the button
            to copy HTML from DnDSource.html file into the clipboard or drag
            HTML context. Paste into or drop over the HTML capable editor in
            external application such as Outlook, Word.

            When the mouse enters the editor, cursor should change to indicate
            that copy operation is about to happen and then release the mouse
            button. HTML text without tags should appear inside the document.

            You should be able to repeat this operation multiple times.
            If the above is true Press PASS else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .title("Test Instructions")
                      .instructions(INSTRUCTIONS)
                      .columns(40)
                      .testUI(DnDHTMLToOutlookTest::createAndShowUI)
                      .build()
                      .awaitAndCheck();
    }

    private static Frame createAndShowUI() {
        Frame frame = new Frame("DnDHTMLToOutlookTest");
        Panel mainPanel;
        Component dragSource;

        mainPanel = new Panel();
        mainPanel.setLayout(new BorderLayout());

        mainPanel.setBackground(Color.YELLOW);
        dragSource = new DnDSource("Drag ME (HTML)!");

        mainPanel.add(dragSource, BorderLayout.CENTER);
        frame.add(mainPanel);
        frame.setSize(200, 200);
        return frame;
    }
}
