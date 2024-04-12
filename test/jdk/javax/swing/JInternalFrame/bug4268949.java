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
 * @test
 * @bug 4268949
 * @summary Tests if JInternalFrame can do setBackground()
 * @run main bug4268949
 */

import java.awt.Color;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;

public class bug4268949 {

    static Color c1;
    static Color c2;
    static Color c3;

    public static void main(String[] argv) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JInternalFrame if1, if2, if3;
            if1 = new JInternalFrame("Frame 1");
            if2 = new JInternalFrame("Frame 2");
            if3 = new JInternalFrame("Frame 3");
            if1.setBounds(20, 20, 95, 95);
            if2.setBounds(120, 20, 95, 95);
            if3.setBounds(220, 20, 95, 95);
            if1.setBackground(Color.red);
            if2.setBackground(Color.blue);
            if3.setBackground(Color.green);
            c1 = if1.getContentPane().getBackground();
            c2 = if2.getContentPane().getBackground();
            c3 = if3.getContentPane().getBackground();
        });
        if (!(c1.equals(Color.red)) || !(c2.equals(Color.blue))
                || !(c3.equals(Color.green))) {
            throw new RuntimeException("Test failed: JInternalFrame " +
                    "cannot do setBackground()");
        }
    }
}
