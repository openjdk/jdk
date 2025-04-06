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
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ItemEvent;

/*
 * @test
 * @bug 6476183
 * @summary Drop down of a Choice changed to enabled state has a disabled like appearance
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DisabledList
 */

public class DisabledList {

    private static final String INSTRUCTIONS = """
            1) Select the checkbox
            2) Open Choice
            3) Drag mouse over the scrollbar or drag out it the choice
            4) If choice's items become disabled press fail, otherwise pass
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("DisabledList Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 3)
                .columns(45)
                .testUI(DisabledList::createAndShowUI)
                .logArea(4)
                .build()
                .awaitAndCheck();
    }

    private static Window createAndShowUI() {
        Frame frame = new Frame("DisabledList");
        frame.setSize(200, 200);
        frame.validate();
        Checkbox checkbox = new Checkbox("checkbox");
        final Choice choice = new Choice();
        choice.setEnabled(false);
        for (int i = 0; i < 15; i++) {
            choice.addItem("Item" + i);
        }
        checkbox.addItemListener(event -> {
            PassFailJFrame.log("CheckBox.itemStateChanged occurred");
            choice.setEnabled(event.getStateChange() == ItemEvent.SELECTED);
        });
        frame.add(BorderLayout.NORTH, checkbox);
        frame.add(BorderLayout.CENTER, choice);
        return frame;
    }
}
