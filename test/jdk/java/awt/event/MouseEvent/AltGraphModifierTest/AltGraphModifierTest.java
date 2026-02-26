/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8041928 8158616
 * @requires (os.family != "mac")
 * @summary Confirm that the Alt-Gr Modifier bit is set correctly.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AltGraphModifierTest
 */

import java.awt.Frame;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class AltGraphModifierTest {

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                This test is for verifying Alt-Gr modifier of an event.
                Please check if Alt-Gr key is present on keyboard.
                If not present, press Pass.
                On Windows:
                    Press Alt-Gr or Right Alt key and simultaneously
                        perform mouse click on the "TestWindow".
                On Linux:
                    Navigate to
                      System Settings-> Keyboard-> Special Character Entry
                    Select "Right Alt" option for the "Alternate Characters Key"
                    Close the settings and navigate to test
                    Press Right Alt Key & simultaneously
                        perform mouse click on the "TestWindow".

                    If the system does not have such setting, press Pass.
                    After the test, change the Setting of "Alternate Characters Key"
                    back to "Layout default".

                If "Alt-Gr Modifier bit is set" message is displayed in logArea,
                press Pass else press Fail.
                """;

         PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(AltGraphModifierTest::initTestWindow)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    public static Frame initTestWindow() {
        Frame mainFrame = new Frame();
        mainFrame.setTitle("TestWindow");
        mainFrame.setBounds(700, 10, 300, 300);
        mainFrame.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int ex = e.getModifiersEx();
                if ((ex & InputEvent.ALT_GRAPH_DOWN_MASK) == 0) {
                    PassFailJFrame.log("Alt-Gr Modifier bit is not set.");
                } else {
                    PassFailJFrame.log("Alt-Gr Modifier bit is set");
                }
            }
        });
        return mainFrame;
    }
}
