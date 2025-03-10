/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTEvent;
import java.awt.Button;
import java.awt.Frame;
import java.awt.MenuItem;
import java.awt.PopupMenu;

import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4186663 4265525
 * @summary Tests that multiple PopupMenus cannot appear at the same time
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MultiplePopupMenusTest
 */

public class MultiplePopupMenusTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Click the right mouse button on the button
                If multiple popups appear at the same time the
                test fails else passes.
                """;

        PassFailJFrame.builder()
                .title("MultiplePopupMenusTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(30)
                .testUI(MultiplePopupMenusTest::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame fr = new Frame("MultiplePopupMenusTest Test");
        TestButton button = new TestButton("button");
        fr.add(button);
        fr.setSize(200, 200);
        return fr;
    }

    static class TestButton extends Button {
        public TestButton(String title) {
            super(title);
            enableEvents(AWTEvent.MOUSE_EVENT_MASK);
        }

        @Override
        public void processMouseEvent(MouseEvent e) {
            if (e.isPopupTrigger()) {
                for (int i = 0; i < 10; i++) {
                    PopupMenu pm = new PopupMenu("Popup " + i);
                    pm.add(new MenuItem("item 1"));
                    pm.add(new MenuItem("item 2"));
                    add(pm);
                    pm.show(this, e.getX() + i * 5, e.getY() + i * 5);
                }
            }
            super.processMouseEvent(e);
        }
    }
}
