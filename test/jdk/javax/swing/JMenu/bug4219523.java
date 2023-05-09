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
  @bug 4219523
  @summary Tests if JMenu completely uninstalls UI
  @run main bug4219523
*/

import java.awt.Insets;

import javax.swing.JMenuItem;
import javax.swing.plaf.basic.BasicMenuItemUI;


public class bug4219523 {
    public static void main(String args[]) {
        class TestMenuItem extends JMenuItem {
            public int SetMarginCalls;
            TestMenuItem(){
                super();
                SetMarginCalls = 0;
            }
            public void setMargin(Insets m){
                if (m == null) SetMarginCalls++;
                super.setMargin(m);
            }
        }
        BasicMenuItemUI bmiui = new BasicMenuItemUI();
        TestMenuItem mi = new TestMenuItem();
        bmiui.installUI(mi);
        int installCall = mi.SetMarginCalls;
        bmiui.uninstallUI(mi);
        if (mi.SetMarginCalls <= installCall) {
            throw new Error("Test failed: Uninstall UI does " +
                    "not uninstall DefaultMargin properties");
        }
    }
}
