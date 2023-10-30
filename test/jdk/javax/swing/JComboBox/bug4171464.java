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
   @bug 4171464
   @summary JComboBox should not throw InternalError
*/

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.event.ListDataListener;

public class bug4171464 {

    public static void main(String args[]) {
        ComboBoxModel model = new ComboBoxModel() {
            public void setSelectedItem(Object anItem) {}
            public Object getSelectedItem() {return null;}
            public int getSize() {return 0;}
            public Object getElementAt(int index) {return null;}
            public void addListDataListener(ListDataListener l) {}
            public void removeListDataListener(ListDataListener l) {}
        };
        JComboBox comboBox = new JComboBox();
        comboBox.setModel(model);
        try {
            comboBox.addItem(new Object() {});
        } catch (InternalError e) {
            // InternalError not suitable if app supplies non-mutable model.
            throw new RuntimeException("4171464 TEST FAILED");
        } catch (Exception e) {
            // Expected exception due to non-mutable model.
        }
    }
}
