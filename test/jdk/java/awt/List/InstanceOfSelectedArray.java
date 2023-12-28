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

/*
  @test
  @bug 4322321
  @summary tests that List.getSelectedIndexes() doesn't return reference to internal array
  @key headful
  @run main InstanceOfSelectedArray
*/

import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;

public class InstanceOfSelectedArray {
     List testList;
     Frame frame;
     int[] selected;

     public static void main(String[] args) throws Exception {
         InstanceOfSelectedArray test = new InstanceOfSelectedArray();
         test.start();
     }

     public void start () throws Exception {
         try {
             EventQueue.invokeAndWait(() -> {
                 testList = new List();
                 frame = new Frame("InstanceOfSelectedArrayTest");
                 testList.addItem("First");
                 testList.addItem("Second");
                 testList.addItem("Third");

                 frame.add(testList);
                 frame.setLayout(new FlowLayout());
                 frame.setSize(300, 200);
                 frame.setLocationRelativeTo(null);
                 frame.setVisible(true);

                 testList.select(2);

                 selected = testList.getSelectedIndexes();
                 selected[0] = 0;
                 selected = testList.getSelectedIndexes();

                 if (selected[0] == 0) {
                     System.out.println("List returned the reference to internal array.");
                     System.out.println("Test FAILED");
                     throw new RuntimeException("Test FAILED");
                 }
             });

             System.out.println("List returned a clone of its internal array.");
             System.out.println("Test PASSED");
         } finally {
             EventQueue.invokeAndWait(() -> {
                 if (frame != null) {
                     frame.dispose();
                 }
             });
         }
     }
}
