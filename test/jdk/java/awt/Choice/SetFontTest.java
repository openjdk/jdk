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

import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Panel;

/*
 * @test
 * @bug 4293346
 * @summary Checks that Choice does update its dimensions on font change
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SetFontTest
 */

public class SetFontTest {

    private static final String INSTRUCTIONS = """
            Choice component used to not update its dimension on font change.
            Select one of fonts on the choice pull down list.
            Pull down the list after the font change; if items in the list are
            shown correctly the test is passed, otherwise it failed.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("SetFontTest Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(SetFontTest::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createAndShowUI() {
        Frame frame = new Frame("SetFontTest");
        Choice choice = new Choice();
        frame.setBounds(100, 400, 400, 100);
        choice.addItem("dummy");
        choice.addItem("Set LARGE Font");
        choice.addItem("Set small Font");
        choice.addItem("addNewItem");
        choice.addItem("deleteItem");

        choice.addItemListener(e -> {
            if (e.getItem().toString().equals("addNewItem")) {
                choice.addItem("very very very very long item");
                frame.validate();
            } else if (e.getItem().toString().equals("deleteItem")) {
                if (choice.getItemCount() > 4) {
                    choice.remove(4);
                    frame.validate();
                }
            } else if (e.getItem().toString().equals("Set LARGE Font")) {
                choice.setFont(new Font("Dialog", Font.PLAIN, 24));
                frame.validate();
            } else if (e.getItem().toString().equals("Set small Font")) {
                choice.setFont(new Font("Dialog", Font.PLAIN, 10));
                frame.validate();
            }
        });
        Panel panel = new Panel();
        panel.add(choice);
        frame.add(panel, BorderLayout.CENTER);
        return frame;
    }
}
