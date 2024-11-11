/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6225679
 * @summary Tests that checkbox changes into radiobutton dynamically
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DynamicChangeTest
 */

import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Frame;
import java.awt.GridLayout;

public class DynamicChangeTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                This test is primarily for Windows platform, but should pass
                on other platforms as well. Ensure that 'This is checkbox' is
                checkbox, and 'This is radiobutton' is radiobutton.
                If it is so, press pass else fail.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(DynamicChangeTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame f = new Frame("Dynamic Change Checkbox Test");
        f.setSize(200, 200);

        f.setLayout(new GridLayout(2, 1));
        Checkbox ch1 = new Checkbox("This is checkbox",
                new CheckboxGroup(), true);
        f.add(ch1);
        Checkbox ch2 = new Checkbox("This is radiobutton", null, true);
        f.add(ch2);

        ch1.setCheckboxGroup(null);
        ch2.setCheckboxGroup(new CheckboxGroup());
        return f;
    }
}
