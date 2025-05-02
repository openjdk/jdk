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
  @bug 4198994
  @summary getInsets should return Insets object that is safe to modify
  @key headful
  @run main ClobberSharedInsetsObjectTest
*/

/**
 * ClobberSharedInsetsObjectTest.java
 *
 * summary: The bug is that getInsets directly returns Insets object
 * obtained from peer getInsets.  The latter always return the
 * reference to the same object, so modifying this object will affect
 * other code that calls getInsets.  The test checks that it's safe to
 * modify the Insets object returned by getInsets.  If the change to
 * this object is not visible on the next invocation, the bug is
 * considered to be fixed.
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Panel;

public class ClobberSharedInsetsObjectTest {
    static Panel p;

    // Impossible inset value to use for the test
    final static int SENTINEL_INSET_VALUE = -10;
    static Frame f;

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            try {
                // Need a peer anyway, so let the bug manifest visuially, even
                // though we can detect it automatically.
                f = new Frame();
                p = new Panel();
                p.setBackground(Color.red);
                f.setLayout (new BorderLayout ());
                f.add(p, "Center");

                Insets insetsBefore = p.getInsets();
                insetsBefore.top = SENTINEL_INSET_VALUE;

                Insets insetsAfter = p.getInsets();
                if (insetsAfter.top == SENTINEL_INSET_VALUE) { // OOPS!
                    throw new Error("4198994: getInsets returns the same object on subsequent invocations");
                }

                f.setSize (200,200);
                f.setLocationRelativeTo(null);
                f.setVisible(true);

                System.out.println("getInsets is ok.  The object it returns is safe to modify.");
            } finally {
                if (f != null) {
                    f.dispose();
                }
            }
        });
    }
}
