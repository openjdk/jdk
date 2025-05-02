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

/* @test
 * @bug 4700351
 * @summary Checks if JToolBar keeps orientation when dragged off
 * @key headful
 * @run main bug4700351
 */

import javax.swing.JFrame;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicToolBarUI;

public class bug4700351 {
    static JFrame fr;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("bug4700351");
                JToolBar tb = new JToolBar();
                tb.setOrientation(JToolBar.VERTICAL);
                fr.add(tb);
                BasicToolBarUI ui = (javax.swing.plaf.basic.BasicToolBarUI) tb.getUI();
                if (!ui.isFloating()) {
                    ui.setFloatingLocation(100, 100);
                    ui.setFloating(true, tb.getLocation());
                }
                if (tb.getOrientation() != JToolBar.VERTICAL) {
                    throw new RuntimeException("Error: toolbar's orientation " +
                            "has changed");
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }
}
