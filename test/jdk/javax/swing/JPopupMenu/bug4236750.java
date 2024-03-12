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

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;

/*
 * @test
 * @bug 4236750
 * @summary Tests presence of JPopupMenu.insert(Action, int)
 * @run main bug4236750
 */

public class bug4236750 {
    private static MenuElement[] elements;
    private static volatile boolean passed = true;

    /**
     * Auxilliary class implementing Action
     */
    static class NullAction implements Action {
        public void addPropertyChangeListener(
                PropertyChangeListener listener) {
        }

        public void removePropertyChangeListener(
                PropertyChangeListener listener) {
        }

        public void setEnabled(boolean b) {
        }

        public boolean isEnabled() {
            return true;
        }

        public void actionPerformed(ActionEvent e) {
        }

        private String name;

        public NullAction(String s) {
            name = s;
        }

        public void putValue(String key, Object value) {
            if (key.equals(Action.NAME)) {
                name = (String) value;
            }
        }

        public Object getValue(String key) {
            if (key.equals(Action.NAME)) {
                return name;
            }
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu popup;
            popup = new JPopupMenu("Test Popup");
            JMenuItem item0 = popup.add(new NullAction("0"));
            JMenuItem item2 = popup.add(new NullAction("2"));
            popup.insert(new NullAction("1"), 1);
            elements = popup.getSubElements();
            for (int i = 0; i < 3; i++) {
                JMenuItem mi = (JMenuItem) elements[i];
                if (!mi.getText().equals("" + i)) {
                    passed = false;
                }
            }
        });

        if (!passed) {
            throw new RuntimeException("Failed: wrong order of menuitems");
        }
        System.out.println("Test Passed!");
    }
}
