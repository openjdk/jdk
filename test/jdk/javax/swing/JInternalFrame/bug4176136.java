/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4176136
 * @summary Default close operation JInternalFrame.DO_NOTHING_ON_CLOSE works correctly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4176136
 */


import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;

public class bug4176136 {

    private static final String INSTRUCTIONS = """
        Click the close button of the internal frame.
        You will see the close button activate,
             but nothing else should happen.
        If the internal frame closes, the test fails.
        If it doesn't close, the test passes.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4176136 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(25)
                .testUI(bug4176136::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4176136");
        JDesktopPane dp = new JDesktopPane();
        frame.add(dp);
        JInternalFrame inf = new JInternalFrame();
        dp.add(inf);
        inf.setDefaultCloseOperation(JInternalFrame.DO_NOTHING_ON_CLOSE);
        inf.setSize(100, 100);
        inf.setClosable(true);
        inf.setVisible(true);
        frame.setSize(200, 200);
        return frame;
    }
}
