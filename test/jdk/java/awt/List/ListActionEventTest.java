/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4089604
 * @summary Enter key doesn't fire List actionPerformed as specified
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ListActionEventTest
*/

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class ListActionEventTest {

    private static final String INSTRUCTIONS = """
            A frame will be shown.
            1. Click any item in the list (say item 1) in the frame
            2. A message 'ItemSelected' is displayed on the message window.
            3. Press the return key on the selected item.
            4. If the text 'ActionPerformed' is displayed on the message window,
               then press PASS else press FAIL.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("ListActionEventTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(ListActionEventTest::createTestUI)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Frame frame = new Frame("ListActionEventTest frame");

        Panel pnl1 = new Panel();
        frame.add(pnl1);
        pnl1.setLayout(new BorderLayout());

        List list = new List();
        for (int i = 0; i < 5; i++) {
            list.addItem("Item " + i);
        }
        pnl1.add(list);

        list.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                PassFailJFrame.log("ActionPerformed");
            }
        });

        list.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent ev) {
                PassFailJFrame.log("ItemSelected");
            }
        });
        frame.pack();
        return frame;
    }
}
