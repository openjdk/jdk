/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 6373369
  @summary Bug in WListPeer.getMaxWidth(), checks that the preferred width
  of the list is calculated correctly
  @requires (os.family == "windows")
  @key headful
  @run main MaxWidthTest
*/

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.FontMetrics;
import java.awt.List;
import java.awt.TextArea;
import java.awt.Toolkit;

public class MaxWidthTest {
    static Frame frame;
    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("MaxWidthTest");
                frame.setLayout(new BorderLayout());
                List list = new List();
                list.add("Very very very long string - the actual width more than the minimum width !!!");
                frame.add(BorderLayout.WEST, list);
                frame.add(BorderLayout.CENTER, new TextArea());

                frame.setBounds(200, 200, 200, 200);
                frame.pack();
                frame.setVisible(true);

                // as WListPeer.minimumSize() - just predefined value
                FontMetrics fm = frame.getFontMetrics(list.getFont());
                int minimum = 20 + fm.stringWidth("0123456789abcde");

                // as WListPeer.preferredSize() - equals to Max.max(minimum,getMaxWidth()+20)
                // getMaxWidth() returns the actual size of the list
                int preferred = list.getPreferredSize().width;

                System.out.println(preferred + "," + minimum);
                if (preferred <= minimum) {
                    throw new RuntimeException("Test failed because the actual width more than the minimum width.");
                }
            });
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
