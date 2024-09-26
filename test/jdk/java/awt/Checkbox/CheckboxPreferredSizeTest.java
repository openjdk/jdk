/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;

/*
 * @test
 * @bug 4304049
 * @summary tests that Checkbox fits into its preferred size entirely
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CheckboxPreferredSizeTest
 */

public class CheckboxPreferredSizeTest {
    private static final String INSTRUCTIONS = """
                    As the test starts, ensure that the
                    whole checkbox with all its text is visible.
                    If the checkbox is entirely visible, press PASS else,
                    press FAIL.
                    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .title("Test Instructions")
                      .instructions(INSTRUCTIONS)
                      .rows((int) INSTRUCTIONS.lines().count() + 1)
                      .columns(35)
                      .testUI(CheckboxPreferredSizeTest::createAndShowUI)
                      .build()
                      .awaitAndCheck();
    }

     private static Frame createAndShowUI() {
         Frame frame = new Frame("Checkbox Preferred Size Test");
         frame.setBackground(Color.BLUE);
         Checkbox box = new Checkbox("Checkbox_With_Some_Size");
         box.setFont(new Font("Helvetica", Font.PLAIN, 36));
         box.setBackground(Color.RED);
         frame.add(box);
         frame.pack();
         return frame;
     }
}
