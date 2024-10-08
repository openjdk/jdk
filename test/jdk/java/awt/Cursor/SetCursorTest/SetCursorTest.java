/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4160080
 * @summary Test setCursor() on lightweight components when event is generated
 *          by a button
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SetCursorTest
 */

import java.awt.Cursor;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;


public class SetCursorTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                This test checks for the behavior of setCursor() when called in
                a JFrame's JButton action event.

                1. Click the "OK" button in the test window.
                2. Verify that the cursor changes to the waiting cursor instead
                    of the default system cursor.

                If true, then pass the test. Otherwise, fail this test.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(SetCursorTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static myFrame createUI() {
        myFrame f = new myFrame();
        return f;
    }
}

class myFrame extends JFrame {
    public myFrame() {
        super("SetCursor With Button Test");
        setSize(200, 200);

        final JPanel p = new JPanel();
        final JButton okButton = new JButton("OK");
        okButton.addActionListener(e ->
                setCursor(new Cursor(Cursor.WAIT_CURSOR)));

        p.add(okButton);
        getContentPane().add(p);
    }
}
