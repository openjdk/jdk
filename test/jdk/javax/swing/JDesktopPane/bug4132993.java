/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4132993
 * @summary JDesktopPane.getAllFramesInLayer(..) return iconified frame
 * @run main bug4132993
 */

import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;


public class bug4132993 {
    public static void main(String[] args) throws Exception {
        JDesktopPane mDesktop = new JDesktopPane();
        JInternalFrame jif = new JInternalFrame("My Frame");
        jif.setIconifiable(true);
        mDesktop.add(jif);
        jif.setIcon(true);
        JInternalFrame[] ji =
                mDesktop.getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER);
        for (int i = 0; i < ji.length; i++) {
            if (jif == ji[i]) {
                return;
            }
        }
        throw new RuntimeException("JDesktopPane.getAllFramesInLayer() failed...");
    }
}
