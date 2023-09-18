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

/* @test
 * @bug 4107843
 * @summary ToolTipText for JTabbedPane.
 * @run main bug4107843
 */

import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public class bug4107843 {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane tp = new JTabbedPane();
            tp.add("First", new JButton("Button1"));
            tp.add("Second", new JButton("Button2"));
            tp.setToolTipTextAt(0, "first button");
            if (!tp.getToolTipTextAt(0).equals("first button")) {
                throw new RuntimeException("ToolTipText isn't set " +
                        "as expected...");
            }
            tp.setToolTipTextAt(1, "second button");
            if (!tp.getToolTipTextAt(1).equals("second button")) {
                throw new RuntimeException("ToolTipText isn't set " +
                        "as expected...");
            }
        });
    }
}
