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

import java.awt.Choice;
import java.awt.Frame;
import java.awt.FlowLayout;
import java.awt.Window;

/*
 * @test
 * @bug 4288285
 * @summary Verifies choice works with many items
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ChoiceBigTest
 */

public class ChoiceBigTest {
    private static final String INSTRUCTIONS = """
            Click the Choice button, press Pass if:

            - all looks good.
            - if you can select the item 1000

            Otherwise press Fail.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("ChoiceBigTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 3)
                .columns(45)
                .testUI(ChoiceBigTest::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static Window createAndShowUI() {
        Frame frame = new Frame("Check Choice");
        frame.setLayout(new FlowLayout());
        Choice choice = new Choice();
        frame.setSize(400, 200);
        for (int i = 1; i < 1001; ++i) {
            choice.add("I am Choice, yes I am : " + i);
        }
        frame.add(choice);
        return frame;
    }
}
