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
  @bug 4689398
  @summary Inserting items in a Container with CardLayout does not work since Merlin
*/

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;

public class CardsOrderTest {

    public static void main(String[] args) throws Exception {

        CardLayout layout = new CardLayout();
        Container cont = new Container();
        Component comp1 = new Component() {};
        Component comp2 = new Component() {};
        Component comp3 = new Component() {};
        cont.setLayout(layout);
        cont.add(comp1, "1", 0);
        cont.add(comp2, "2", 0);
        cont.add(comp3, "3", 0);

        // Testing visibility "state" - not actually if its visible on screen
        // since this test does not require a UI.
        System.out.println("comp1.isVisible() = " + comp1.isVisible());
        System.out.println("comp2.isVisible() = " + comp2.isVisible());
        System.out.println("comp3.isVisible() = " + comp3.isVisible());

        if (!comp1.isVisible() || comp2.isVisible() || comp3.isVisible()) {
            throw new RuntimeException("first added component must be visible");
        }

        System.out.println("CardLayout.next()");
        layout.next(cont);

        System.out.println("comp1.isVisible() = " + comp1.isVisible());
        System.out.println("comp2.isVisible() = " + comp2.isVisible());
        System.out.println("comp3.isVisible() = " + comp3.isVisible());

        if (!comp3.isVisible() ||comp1.isVisible() || comp2.isVisible()) {
            throw new RuntimeException("the wrong component is visible after CardLayout.next() (must be comp3)");
        }
    }
}
