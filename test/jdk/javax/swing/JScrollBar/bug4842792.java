/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.Date;

/*
 * @test
 * @bug 4842792
 * @summary JScrollBar behaves incorrectly if "Block increment" value is big enough
 * @run main bug4842792
 */

public class bug4842792 {
    public static TestScrollBar scrollBar;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            scrollBar = new TestScrollBar(JScrollBar.HORIZONTAL, 10, 10, 0, 100);
            scrollBar.setPreferredSize(new Dimension(200, 20));
            scrollBar.setBlockIncrement(Integer.MAX_VALUE);

            if (scrollBar.doTest() == 0) {
                throw new RuntimeException("The scrollbar new value should not be 0");
            }
        });
        System.out.println("Test Passed!");
    }

    static class TestScrollBar extends JScrollBar {
        public TestScrollBar(int orientation, int value, int extent,
                             int min, int max) {
            super(orientation, value, extent, min, max);
        }

        public int doTest() {
            MouseEvent mouseEvent = new MouseEvent(scrollBar,
                                           MouseEvent.MOUSE_PRESSED,
                                           (new Date()).getTime(),
                                           MouseEvent.BUTTON1_DOWN_MASK,
                                           150, 10, 1, true);
            processMouseEvent(mouseEvent);
            return scrollBar.getValue();
        }
    }
}
