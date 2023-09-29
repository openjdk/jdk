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
  @bug 4690266
  @summary REGRESSION: Wizard Page does not move to the next page
*/

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;

public class ObedienceTest {

    public static void main(String[] args) {
        Container cont = new Container();
        Component comp1 = new Component() {};
        Component comp2 = new Component() {};
        CardLayout layout = new CardLayout();
        cont.setLayout(layout);
        cont.add(comp1, "first");
        cont.add(comp2, "second");

        if (!comp1.isVisible()) {
            throw new RuntimeException("first component must be visible");
        }

        comp1.setVisible(false);
        comp2.setVisible(true);
        layout.layoutContainer(cont);

        if (!comp2.isVisible() || comp1.isVisible()) {
            System.out.println("comp1.isVisible() = " + comp1.isVisible());
            System.out.println("comp2.isVisible() = " + comp2.isVisible());
            throw new RuntimeException("manually shown component must be visible after layoutContainer()");
        }
    }
}
