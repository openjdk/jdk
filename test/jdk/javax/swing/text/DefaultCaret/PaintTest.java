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
 * @bug 4193062
 * @summary Tests that when a TextField first gets focus, if modelToView fails
 *          (null is returned) that the caret will start to blink again.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PaintTest
*/

import java.awt.FlowLayout;
import javax.swing.JFrame;
import javax.swing.JTextField;

public class PaintTest {

    static final String INSTRUCTIONS = """
         If the test window displays with the text caret flashing (do wait at
         least several second for it to start) the test PASSES, otherwise it FAILS.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("PaintTest Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(50)
            .testUI(PaintTest::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("PaintTest");
        JTextField tf = new JTextField(20);
        frame.setLayout(new FlowLayout());
        frame.add(tf);
        frame.setSize(300, 300);
        return frame;
    }
}
