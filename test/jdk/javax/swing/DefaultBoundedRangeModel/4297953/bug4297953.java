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
 * @test
 * @bug 4297953
 * @summary Tests that DefaultBoundedRangeModel doesn't zero out the
 *          extent value when maximum changes
 * @run main bug4297953
 */

import javax.swing.JScrollBar;

public class bug4297953 {
    public static void main(String[] args)  {
        JScrollBar sb = new JScrollBar(JScrollBar.HORIZONTAL, 90, 10, 0, 100);
        sb.setMaximum(80);
        if (sb.getVisibleAmount() != 10) {
            throw new RuntimeException("Failed: extent is " + sb.getVisibleAmount());
        }
    }
}
