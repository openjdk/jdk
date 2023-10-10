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

import javax.swing.JSplitPane;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/*
 * @test
 * @bug 4147653
 * @summary JSplitPane.DIVIDER_LOCATION_PROPERTY is a property,
 * you can use that to know when the position changes.
 * @run main bug4147653
 */

public class bug4147653 {
    private static volatile boolean flag = false;

    static class DevMoved implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent evt) {
            flag = true;
        }
    }

    public static void main(String[] args) throws Exception {
        JSplitPane sp = new JSplitPane();

        DevMoved pl = new DevMoved();
        sp.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, pl);
        sp.setDividerLocation(sp.getDividerLocation() + 10);
        Thread.sleep(1000);

        if (!flag) {
            throw new RuntimeException("Divider property was not changed...");
        }
        System.out.println("Test Passed!");
    }
}
