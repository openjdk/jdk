/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6367251
 * @summary 2 items are highlighted when dragging outside and press UP or DOWN
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MultiItemSelected_UpDown
 */

import java.awt.Choice;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.lang.reflect.InvocationTargetException;

public class MultiItemSelected_UpDown extends Frame {
    static final String INSTRUCTIONS = """
            1) Open Choice.
            2) Start drag from first item to another one.
            3) Without interrupting drag
               move mouse cursor outside the choice popup.
            4) Press UP, DOWN key several times to position
               selection cursor to a different item.
            5) Release mouse button.
            6) If popup is closed upon mouse button release open Choice again.
            7) Verify that there is only one
               selection cursor in the dropdown list.
            8) If true then press Pass, otherwise press Fail.
            """;

    public MultiItemSelected_UpDown() {
        Choice choice = new Choice();

        for (int i = 1; i < 20; i++) {
            choice.add(" item " + i);
        }
        add(choice);
        choice.addItemListener(ie -> System.out.println(ie));
        setLayout(new FlowLayout());
        setSize(200, 200);
        validate();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("MultiItemSelected Up/Down Test")
                .testUI(MultiItemSelected_UpDown::new)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .build()
                .awaitAndCheck();
    }
}
