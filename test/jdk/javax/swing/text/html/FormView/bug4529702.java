/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4529702
 * @summary Test that radio buttons with different names should be selectable at the same time
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4529702
*/

import javax.swing.JFrame;
import javax.swing.JTextPane;

public class bug4529702 {

    static final String INSTRUCTIONS = """
        There are two rows of radio buttons, each row having two buttons.
        If you can select radio buttons from the first and the second rows
        at the same time the test PASSES otherwise the test FAILS.
    """;

    static JFrame createUI() {
        JFrame frame = new JFrame("bug4529702");
        JTextPane jtp = new JTextPane();
        jtp.setContentType("text/html");
        jtp.setText("<html><body><form><input type=radio name=a><input type=radio name=a><br>" +
                    "<input type=radio name=b><input type=radio name=b></form></body></html>");
        frame.add(jtp);
        frame.setSize(200, 200);
        return frame;
    }

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(40)
            .testUI(bug4529702::createUI)
            .build()
            .awaitAndCheck();
    }
}
