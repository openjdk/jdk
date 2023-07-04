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
   @bug 4244614
   @summary Tests that JComboBox has setAction(Action) constructor
*/

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import javax.swing.Action;
import javax.swing.JComboBox;

public class bug4244614 {

/** Auxiliary class implementing Action
 */
    static class NullAction implements Action {
        public void addPropertyChangeListener(
                       PropertyChangeListener listener) {}
        public void removePropertyChangeListener(
                       PropertyChangeListener listener) {}
        public void putValue(String key, Object value) {}
        public void setEnabled(boolean b) {}
        public void actionPerformed(ActionEvent e) {}

        public Object getValue(String key) { return null; }
        public boolean isEnabled() { return false; }
    }

    public static void main(String[] argv) {
        Object[] comboData = {"First", "Second", "Third"};
        JComboBox combo = new JComboBox(comboData);
        Action action = new NullAction();
        combo.setAction(action);
    }
}
