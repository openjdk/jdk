/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Choice;
import java.awt.Frame;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4151949
 * @summary Verifies that Components are reshaped to their preferred size
 *          when their Container is packed.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CheckChoiceTest
 */

public class CheckChoiceTest {

    private static JComponent componentToFocus;

    private static final String INSTRUCTIONS = """
            Verify that the widths of the Choice components are all the same
            and that none is the minimum possible size.
            (The Choices should be at least as wide as the Frame.)
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .title("CheckChoiceTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 3)
                .columns(45)
                .testUI(CheckChoiceTest::createAndShowUI)
                .splitUIBottom(CheckChoiceTest::createComponentToFocus)
                .build();

        // focus away from the window with choices
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> componentToFocus.requestFocus());

        passFailJFrame.awaitAndCheck();
    }

    private static JComponent createComponentToFocus() {
        componentToFocus = new JPanel();
        return componentToFocus;
    }

    private static Frame createAndShowUI() {
        Frame f = new Frame("Check Choice");
        f.setLayout(new BorderLayout());

        Choice choice1 = new Choice();
        Choice choice2 = new Choice();
        Choice choice3 = new Choice();

        f.add(choice1, BorderLayout.NORTH);
        f.add(choice3, BorderLayout.CENTER);
        f.add(choice2, BorderLayout.SOUTH);
        f.pack();

        choice1.add("I am Choice, yes I am : 0");
        choice2.add("I am the same, yes I am : 0");
        choice3.add("I am the same, yes I am : 0");

        return f;
    }
}
