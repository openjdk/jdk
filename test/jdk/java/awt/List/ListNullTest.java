/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4195583
  @summary Tests List.add(String item) to make sure an NPE is not thrown
                 when item == null
  @key headful
  @run main ListNullTest
*/

import java.awt.FlowLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.List;
import java.awt.Robot;

public class ListNullTest {
   List list;
   Frame frame;

   public static void main(String[] args) throws Exception {
      ListNullTest test = new ListNullTest();
      test.start();
   }

   public void start () throws Exception {
      try {
         EventQueue.invokeAndWait(() -> {
            list = new List(15);
            frame = new Frame("ListNullTest");
            frame.add(list);
            frame.setLayout(new FlowLayout());
            frame.setSize(200, 200);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            list.add("", 0);
            list.add((String) null, 1);
         });
      } finally {
         EventQueue.invokeAndWait(() -> {
            if (frame != null) {
               frame.dispose();
            }
         });
      }
   }
}
