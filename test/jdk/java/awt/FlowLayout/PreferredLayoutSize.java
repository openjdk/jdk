/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4284124
  @summary FlowLayout gives a wrong size if the first component is hidden.
  @key headful
  @run main PreferredLayoutSize
*/

import java.awt.Button;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.lang.reflect.InvocationTargetException;

public class PreferredLayoutSize {
    public void start() {
        Frame f = new Frame("PreferredLayoutSize");
        int[] widths = new int[2];

        try {
            f.setLocationRelativeTo(null);
            Button b1 = new Button("button 1");
            Button b2 = new Button("button 2");
            f.setLayout(new FlowLayout(FlowLayout.LEFT, 50, 5));
            f.add(b1);
            f.add(b2);
            f.pack();
            f.setVisible(true);
            b1.setVisible(false);
            b2.setVisible(true);
            Dimension d1 = f.getPreferredSize();
            Dimension d2 = b2.getPreferredSize();
            widths[0] = d1.width - d2.width;
            b1.setVisible(true);
            b2.setVisible(false);
            d1 = f.getPreferredSize();
            d2 = b1.getPreferredSize();
            widths[1] = d1.width - d2.width;
            f.setVisible(false);
        } finally {
            f.dispose();
        }

        if (widths[0] != widths[1]) {
            throw new RuntimeException("Test FAILED");
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PreferredLayoutSize test = new PreferredLayoutSize();
        EventQueue.invokeAndWait(test::start);
    }
}
