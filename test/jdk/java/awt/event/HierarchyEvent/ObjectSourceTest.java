/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4420658
  @summary No ClassCastException should be thrown when getComponent()
           is called on an event with a non-Component source.
           The result should be null.
  @run main ObjectSourceTest
*/

import java.awt.Component;
import java.awt.Panel;
import java.awt.event.HierarchyEvent;
import java.lang.reflect.InvocationTargetException;


public class ObjectSourceTest {
    static Panel panel;

    public static void main(String args[]) throws InterruptedException,
            InvocationTargetException {
        panel = new Panel();

        HierarchyEvent he = new HierarchyEvent(panel, HierarchyEvent.ANCESTOR_MOVED,
                panel, panel);
        Object obj = new Object();
        he.setSource(obj);

        Component comp = he.getComponent();
        if (comp != null) {
            throw new RuntimeException("ObjectSourceTest failed.  comp != null");
        }
    }
}
