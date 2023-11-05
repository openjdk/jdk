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

/* @test
 * @bug 4677611
 * @summary JViewport sets Opaque after UpdateUI (prevents UI delegates
 * to determine look)
 * @run main bug4677611
 */

import java.awt.Color;

import javax.swing.JScrollPane;
import javax.swing.JViewport;

public class bug4677611 {
    public static void main(String[] args) throws Exception {
        JScrollPane sp = new JScrollPane();
        JViewport vp = new MyViewport();
        vp.setBackground(Color.blue);
        sp.setViewport(vp);

        if (vp.isOpaque()) {
            throw new RuntimeException("JViewport shouldn't set Opaque " +
                    "after update the UI");
        }
    }

    static class MyViewport extends JViewport {
        public void updateUI() {
            setOpaque(false);
            super.updateUI();
        }
    }
}
