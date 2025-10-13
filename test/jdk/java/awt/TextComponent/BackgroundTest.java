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
 * @bug 4258667 4405602
 * @summary  Make sure TextComponents are grayed out when non-editable
 *           if the background color has not been set by client code.
 *           Make sure TextComponents are not grayed out when non-editable
 *           if the background color has been set by client code.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual BackgroundTest
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.TextArea;
import java.awt.TextField;

public class BackgroundTest {
    private static final String enableString = "EnableText";
    private static final String disableString = "DisableText";

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. When the frame appears, it should have a blue background.
                2. The first TextField and TextArea will be the default color.
                   The second TextField and TextArea will be green.
                3. Press the "DisableText" button.
                   The first TextField and TextArea should change colors to the
                   default disabled color.  On Windows, this is usually gray.
                   On linux and macos it will match the environment settings.
                   If the TextField or the TextArea do not change colors as described,
                   the test FAILS.
                4. The second TextField and TextArea should still be green.
                   If either of them are not green, the test FAILS.
                   Press the "EnableText" button (same button as before).
                   The first TextField and TextArea should return to their
                   original colors as described in the first paragraph. If they
                   do not, the test FAILS.
                5. The second TextField and TextArea should still be green.
                   If either of them are not green, the test FAILS.
                   Otherwise, the test PASSES.
                """;

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(BackgroundTest::initialize)
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        Frame frame = new Frame("Background Test");
        frame.setLayout(new FlowLayout());
        TextField tf = new TextField(30);
        TextArea ta = new TextArea(4, 30);
        TextField setTf = new TextField(30);
        TextArea setTa = new TextArea(4, 30);
        Button enableButton = new Button(disableString);

        enableButton.setBackground(Color.red);
        frame.setSize(500, 250);

        frame.setBackground(Color.blue);

        tf.setText("Background not set - should be default");
        tf.setEditable(true);
        frame.add(tf);
        ta.setText("Background not set - should be default");
        ta.setEditable(true);
        frame.add(ta);

        setTf.setText("Background is set - should be Green");
        setTf.setBackground(Color.green);
        setTf.setEditable(true);
        frame.add(setTf);
        setTa.setText("Background is set - should be Green");
        setTa.setBackground(Color.green);
        setTa.setEditable(true);
        frame.add(setTa);

        enableButton.addActionListener(e -> {
            boolean currentlyEditable = tf.isEditable();

            if (currentlyEditable) {
                tf.setEditable(false);
                ta.setEditable(false);
                setTf.setEditable(false);
                setTa.setEditable(false);
                enableButton.setLabel(enableString);
            } else {
                tf.setEditable(true);
                ta.setEditable(true);
                setTf.setEditable(true);
                setTa.setEditable(true);
                enableButton.setLabel(disableString);
            }
        });
        frame.add(enableButton);
        return frame;
    }
}