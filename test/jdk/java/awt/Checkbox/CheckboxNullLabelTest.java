/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Panel;

/*
 * @test
 * @bug 4383735
 * @summary Checkbox buttons are too small with java 1.3 and 1.4
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CheckboxNullLabelTest
 */

public class CheckboxNullLabelTest {
    private static final String INSTRUCTIONS = """
                        Please look at the frame titled 'CheckboxNullLabelTest'.
                        Check if all the check boxes in each group
                        (of 3 check boxes) have the same size.

                        If the size of labeled check box is NOT the same as
                        the size of non-labeled Press FAIL otherwise Press PASS.
                        """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .title("Test Instructions")
                      .instructions(INSTRUCTIONS)
                      .rows((int) INSTRUCTIONS.lines().count() + 1)
                      .columns(35)
                      .testUI(CheckboxNullLabelTest::createAndShowUI)
                      .build()
                      .awaitAndCheck();
    }

    private static Frame createAndShowUI() {
        Frame f = new Frame("CheckboxNullLabelTest");
        f.setLayout(new BorderLayout());
        f.add(new CheckboxTest(Color.gray, new Font(null, 0, 12)), "North");
        f.add(new CheckboxTest(Color.green, new Font(null, 0, 18)), "South");
        f.add(new CheckboxTest(Color.red, new Font(null, 0, 24)), "East");
        f.add(new CheckboxTest(Color.white, new Font(null, 0, 30)), "West");
        f.add(new CheckboxTest(f.getBackground(), new Font(null, 0, 36)), "Center");
        f.setSize(600, 450);
        return f;
    }

    private static class CheckboxTest extends Panel {
        Checkbox cb1, cb2, cb3;

        CheckboxTest(Color background, Font font) {
            setBackground(background);
            CheckboxGroup cbg = new CheckboxGroup();

            cb1 = new Checkbox(null, cbg, true);
            cb1.setFont(font);

            cb2 = new Checkbox("", cbg, true);
            cb2.setFont(font);

            cb3 = new Checkbox("Label", cbg, false);
            cb3.setFont(font);

            add(cb1);
            add(cb2);
            add(cb3);
        }
    }
}
