/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4193070
 * @summary Tests correct mouse pointer shape
 * @requires (os.family != "mac")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4193070
 */

import java.awt.Dimension;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;

public class bug4193070 {

    private static final String INSTRUCTIONS = """
        Two internal frame will be shown. Select any internal frame;
        Move mouse pointer inside the selected internal frame,
            then to border of internal frame.
        Mouse pointer should take the shape of resize cursor.
        Now slowly move the mouse back inside the internal frame.
        If mouse pointer shape does not change back to
            normal shape of mouse pointer, then test failed.
        Now try fast resizing an internal frame.
        Check that mouse pointer always has resize shape,
            even when it goes over other internal frame.
        If during resizing mouse pointer shape changes,
            then test failed. Otherwise test succeded.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4193070 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug4193070::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame f = new JFrame("bug4193070");
        JDesktopPane dp = new JDesktopPane();

        JInternalFrame intFrm1 = new JInternalFrame();
        intFrm1.setResizable(true);
        dp.add(intFrm1);

        JInternalFrame intFrm2 = new JInternalFrame();
        intFrm2.setResizable(true);
        dp.add(intFrm2);

        f.setContentPane(dp);
        f.setSize(new Dimension(500, 275));

        intFrm1.setBounds(25, 25, 200, 200);
        intFrm1.show();

        intFrm2.setBounds(275, 25, 200, 200);
        intFrm2.show();
        return f;
    }
}
