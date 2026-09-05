/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8364146
 * @key headful
 * @summary Verifies JList getScrollableUnitIncrement return non-negative number
 * @run main JListTest
 */

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
public class JListTest {

    private static JFrame f;

    public static void main(String[] argv) throws Exception {

        SwingUtilities.invokeAndWait(() -> {
            try {
                f = new JFrame();
                String[] data = {"One", "Two", "Three", "Four", "Five", "Six ",
                                 "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelv"};
                JList<String> list = new JList<>(data);
                list.setLayoutOrientation(JList.HORIZONTAL_WRAP);

                JScrollPane sp = new JScrollPane(list);
                sp.setPreferredSize(new Dimension(200, 200));
                f.add(sp);
                f.pack();
                f.setVisible(true);

                Rectangle cell = list.getCellBounds(1, data.length);
                System.out.println(cell);
                cell.y = list.getHeight() + 10;
                int unit = list.getScrollableUnitIncrement(
                                cell,
                                SwingConstants.VERTICAL,
                                -1);
                System.out.println("Scrollable unit increment: " + unit);

                if (unit < 0) {
                    throw new RuntimeException("JList scrollable unit increment should be greater than 0.");
                }
                unit = list.getScrollableUnitIncrement(
                                cell,
                                SwingConstants.VERTICAL,
                                1);
                System.out.println("Scrollable unit increment: " + unit);
                if (unit < 0) {
                    throw new RuntimeException("JList scrollable unit increment should be greater than 0.");
                }
            } finally {
                if (f != null) {
                    f.dispose();
                }
            }
        });
    }
}
