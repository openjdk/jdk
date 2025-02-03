/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Choice;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Window;

/*
 * @test
 * @bug 4927930
 * @summary Verify that the focus is set to the selected item after calling the java.awt.Choice.select() method
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ChoiceFocusTest
 */

public class ChoiceFocusTest {

    private static final String INSTRUCTIONS = """
            1. Use the mouse to select Item 5 in the Choice list.
            2. Click on the Choice. Item5 is now selected and highlighted. This is the correct behavior.
            3. Select Item 1 in the Choice list.
            4. Click the "choice.select(5)" button. This causes a call to Choice.select(5). Item 5 is now selected.
            5. Click on the Choice.
            6. If the cursor and focus are on item 5, the test passes. Otherwise, it fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("ChoiceFocusTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 3)
                .columns(50)
                .testUI(ChoiceFocusTest::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static Window createAndShowUI() {
        Panel panel = new Panel();
        Choice choice = new Choice();
        Button button = new Button("choice.select(5);");

        for (int i = 0; i < 10; i++) {
            choice.add(String.valueOf(i));
        }

        button.addActionListener(e -> choice.select(5));

        panel.add(button);
        panel.add(choice);

        Frame frame = new Frame("ChoiceFocusTest");
        frame.add(panel);
        frame.pack();

        return frame;
    }
}
