/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
   @bug 6989617
   @summary Enable JComponent to control repaintings of its children
   @author Alexander Potochkin
   @run main bug6989617
*/

import javax.swing.*;
import java.awt.*;

public class bug6989617 {

    private boolean isPaintingOrigin;
    private boolean innerPanelRepainted, outerPanelRepainted;

    public bug6989617() {

        final JButton button = new JButton("button");

        JPanel innerPanel = new JPanel() {
            protected boolean isPaintingOrigin() {
                return isPaintingOrigin;
            }

            public void repaint(long tm, int x, int y, int width, int height) {
                if (button.getParent() != null) {
                    innerPanelRepainted = true;
                    if (!button.getSize().equals(new Dimension(width, height))) {
                        throw new RuntimeException("Wrong size of the dirty area");
                    }
                    if (!button.getLocation().equals(new Point(x, y))) {
                        throw new RuntimeException("Wrong location of the dirty area");
                    }
                }
                super.repaint(tm, x, y, width, height);
            }
        };

        JPanel outerPanel = new JPanel() {
            protected boolean isPaintingOrigin() {
                return isPaintingOrigin;
            }

            public void repaint(long tm, int x, int y, int width, int height) {
                if (button.getParent() != null) {
                    outerPanelRepainted = true;
                    if (!button.getSize().equals(new Dimension(width, height))) {
                        throw new RuntimeException("Wrong size of the dirty area");
                    }
                }
                super.repaint(tm, x, y, width, height);
            }
        };


        outerPanel.add(innerPanel);
        innerPanel.add(button);

        outerPanel.setSize(100, 100);
        innerPanel.setBounds(10, 10, 50, 50);
        button.setBounds(10, 10, 20, 20);

        if (innerPanelRepainted || outerPanelRepainted) {
            throw new RuntimeException("Repainted flag is unexpectedly on");
        }
        button.repaint();
        if (innerPanelRepainted || outerPanelRepainted) {
            throw new RuntimeException("Repainted flag is unexpectedly on");
        }
        isPaintingOrigin = true;
        button.repaint();
        if (!innerPanelRepainted || !outerPanelRepainted) {
            throw new RuntimeException("Repainted flag is unexpectedly off");
        }
    }

    public static void main(String... args) throws Exception {
        new bug6989617();
    }
}
