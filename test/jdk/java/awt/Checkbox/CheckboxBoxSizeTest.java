/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Panel;

/*
 * @test
 * @bug 4410522
 * @requires (os.family == "windows")
 * @summary The box size of the Checkbox control should be the same as
 *          in Windows native applications.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CheckboxBoxSizeTest
 */

public class CheckboxBoxSizeTest {
    private static final String INSTRUCTIONS = """
            This test must be run at UI Scale of 100% AND
            150% or greater.
            Compare the size of box to any of native apps on Windows
            (Eg. Font Dialog Settings on Word).
            They should be the same.

            If the sizes are same Press PASS, else Press FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .title("CheckboxBoxSizeTest Instructions")
                      .instructions(INSTRUCTIONS)
                      .rows((int) INSTRUCTIONS.lines().count() + 2)
                      .columns(35)
                      .testUI(CheckboxBoxSizeTest::createTestUI)
                      .build()
                      .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Frame frame = new Frame("CheckboxBoxSizeTest");
        Panel panel = new Panel(new FlowLayout());
        Checkbox checkbox = new Checkbox("Compare the box size");
        panel.add(checkbox);
        frame.add(panel);
        frame.pack();
        return frame;
    }
}
