/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 4276920
   @summary Tests that BasicComboPopup.hide() doesn't cause unnecessary repaints
   @key headful
*/

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class bug4276920 {

    static volatile TestComboBox combo;
    static volatile JFrame frame;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(bug4276920::createUI);
            Thread.sleep(2000);
            int before = combo.getRepaintCount();
            SwingUtilities.invokeAndWait(combo::hidePopup);
            int after = combo.getRepaintCount();
            if (after > before) {
                throw new Error("Failed 4276920: BasicComboPopup.hide() caused unnecessary repaint()");
            }
         } finally {
            if (frame != null) {
            SwingUtilities.invokeAndWait(frame::dispose);
            }
         }
     }

     static void createUI() {
        combo = new TestComboBox(new String[] {"Why am I so slow?"});
        frame = new JFrame("bug4276920");
        frame.getContentPane().add(combo);
        frame.pack();
        frame.validate();
        frame.setVisible(true);
    }

    static class TestComboBox extends JComboBox {
        int count = 0;

        TestComboBox(Object[] content) {
            super(content);
        }

        public void repaint() {
            super.repaint();
            count++;
        }

        int getRepaintCount() {
            return count;
        }
    }
}
