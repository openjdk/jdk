/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4311614
  @summary  findComponentAt() should check for isShowing() instead of isVisible()
  @key headful
*/

import java.awt.Button;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;

public class FindComponentAtTest {
    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            Panel aContainer;
            Panel bContainer;
            Panel cContainer;
            Button button = new Button("button4");
            Frame frame = new Frame("FindComponentAtTest");

            try {
                aContainer = new Panel();
                bContainer = new Panel();
                cContainer = new Panel();
                aContainer.setName("ACONT");
                bContainer.setName("BCONT");

                frame.add(aContainer);

                aContainer.add(bContainer);
                bContainer.add(cContainer);
                cContainer.add(button);

                bContainer.setVisible(false);

                frame.setSize(200, 200);
                frame.setVisible(true);
                frame.validate();

                System.out.println("Test set for FindComponentAt() method.");
                System.out.println("aContainer - visible");
                System.out.println("bContainer - child of aContainer - is invisible");
                System.out.println("cContainer - child of bContainer - is visible");
                System.out.println("button4  - child of cContainer - is visible");
                Component comp = cContainer.findComponentAt(
                        cContainer.getWidth() / 2,
                        cContainer.getHeight() / 2);

                if (comp != null) {
                    throw new RuntimeException(
                            "cContainer: Visible component inserted into "
                                    + "invisible container have found "
                                    + "by findComponentAt(x, y) method");
                }
            } finally {
                frame.dispose();
            }
            System.out.println("FindComponentAt Test Succeeded.");
        });
    }
}
