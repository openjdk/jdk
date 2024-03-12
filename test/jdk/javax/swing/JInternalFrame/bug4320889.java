/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;

import static javax.swing.SwingUtilities.invokeAndWait;

/*
 * @test
 * @bug 4320889
 * @key headful
 * @summary Tests if default background color is set correctly for JInternalFrame
*/

public class bug4320889 {
    private static JFrame jFrame;

    private static final int FRAME_SIZE = 200;
    private static final int JIF_SIZE = 100;

    public static void main(String[] args) throws Exception {
        invokeAndWait(() -> {
            try {
                jFrame = new JFrame("bug4320889 - JFrame b/g color");
                JDesktopPane desktop = new JDesktopPane();
                jFrame.setSize(FRAME_SIZE, FRAME_SIZE);
                jFrame.setContentPane(desktop);

                JInternalFrame jif = new JInternalFrame();
                jif.setSize(JIF_SIZE, JIF_SIZE);
                jif.setLocation(5, 5);
                desktop.add(jif);
                jif.setVisible(true);
                jFrame.setVisible(true);

                if ((jif.getBackground()).equals(desktop.getBackground())) {
                    throw new RuntimeException("Test failed: default background color" +
                            " is not set correctly for JInternalFrame");
                }
            } finally {
                if (jFrame != null) {
                    jFrame.dispose();
                }
            }
        });
    }
}
