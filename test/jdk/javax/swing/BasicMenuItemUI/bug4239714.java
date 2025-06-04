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
 * @test
 * @bug 4239714
 * @summary Tests that BasicMenuItemUI.installComponent() is protected
 */

import javax.swing.JMenuItem;
import javax.swing.plaf.basic.BasicMenuItemUI;

public class bug4239714 {
    public static void main(String[] argv) throws Exception {
        Tester tester = new Tester();
        tester.test();
    }

    static class Tester extends BasicMenuItemUI {
        public void test() {
            JMenuItem mi = new JMenuItem("bug4239714");
            installComponents(mi);
        }
    }
}
