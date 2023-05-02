/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.datatransfer.SystemFlavorMap;
import java.awt.dnd.DropTarget;

/*
  @test
  @bug 4785476
  @summary tests that DropTarget.setFlavorMap(null) works properly
  @key headful
  @run main DropTargetNullFlavorMapTest
*/
public class DropTargetNullFlavorMapTest {

    public static void main(String[] args) {
        DropTargetNullFlavorMapTest test = new DropTargetNullFlavorMapTest();
        test.init();
    }

    public void init() {
        final DropTarget dropTarget = new DropTarget();

        if (!SystemFlavorMap.getDefaultFlavorMap().equals(dropTarget.getFlavorMap())) {
            System.err.println("Default flavor map: " + SystemFlavorMap.getDefaultFlavorMap());
            System.err.println("DropTarget's flavor map: " + dropTarget.getFlavorMap());
            throw new RuntimeException("Incorrect flavor map.");
        }

        Thread.currentThread().setContextClassLoader(new ClassLoader() {});

        dropTarget.setFlavorMap(null);

        if (!SystemFlavorMap.getDefaultFlavorMap().equals(dropTarget.getFlavorMap())) {
            System.err.println("Default flavor map: " + SystemFlavorMap.getDefaultFlavorMap());
            System.err.println("DropTarget's flavor map: " + dropTarget.getFlavorMap());
            throw new RuntimeException("Incorrect flavor map.");
        }
    }
}
