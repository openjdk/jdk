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
 * @bug 4197019
 * @key headful
 * @run main bug4197019
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Polygon;
import java.awt.event.ActionEvent;

import javax.swing.Action;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

public class bug4197019 {
    static volatile JMenuItem mi1;
    static volatile JMenuItem mi2;
    static volatile Icon i2;
    static volatile boolean isPassed = false;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JMenu fileMenu = new JMenu("File");
            JPopupMenu p = new JPopupMenu();
            Icon i = new ArrowIcon();
            Action a = new TestAction("Test", i);
            mi1 = fileMenu.add(a);
            mi2 = p.add(a);

            i2 = new SquareIcon();
            a.putValue(Action.SMALL_ICON, i2);

            isPassed = (mi2.getIcon() != i2) || (mi1.getIcon() != i2) ||
                    (mi1.getIcon() != mi2.getIcon());
        });
        if (isPassed) {
            throw new RuntimeException("Failed bug test 4197019");
        }
    }

    private static class TestAction extends AbstractAction {
        public TestAction(String s, Icon i) {
            super(s,i);
        }
        public void actionPerformed(ActionEvent e) {

        }
    }

    private static class ArrowIcon implements Icon {
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Polygon p = new Polygon();
            p.addPoint(x, y);
            p.addPoint(x+getIconWidth(), y+getIconHeight()/2);
            p.addPoint(x, y+getIconHeight());
            g.fillPolygon(p);

        }
        public int getIconWidth() { return 4; }
        public int getIconHeight() { return 8; }
    } // End class MenuArrowIcon

    private static class SquareIcon implements Icon {
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(Color.red);
            g.fill3DRect(x,y,4,8,true);
        }
        public int getIconWidth() { return 8; }
        public int getIconHeight() { return 8; }
    } // End class MenuArrowIcon

}
