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

import java.awt.Button;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 4258667 4405602
 * @summary  Make sure TextComponents are grayed out when non-editable
 *           if the background color has not been set by client code.
 *           Make sure TextComponents are not grayed out when non-editable
 *           if the background color has been set by client code.
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual BackgroundTest
 */

public class BackgroundTest {
    private static final String instructions =
            """
                    The test frame should have a blue background.
                    The first TextField and TextArea will be the default color.
                    On Windows and macOS, this is usually white.  On Solaris, it will match
                    your environment settings.
                    The second TextField and TextArea will be green.

                    Press the DisableText button.

                    The first TextField and TextArea should change colors to the
                    default disabled color.  On Windows, this is usually gray.
                    On Solaris, it will match your environment settings.  If either
                    the TextField or the TextArea do not change colors as described,
                    the test FAILS.

                    The second TextField and TextArea should still be green.
                    If either of them are not green, the test FAILS.

                    Press the EnableText button (same button as before).

                    The first TextField and TextArea should return to their
                    original colors as described in the first paragraph. If they
                    do not, the test FAILS.

                    The second TextField and TextArea should still be green.
                    If either of them are not green, the test FAILS.

                    Otherwise, the test PASSES.
                    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(instructions)
                .rows((int) instructions.lines().count() + 1)
                .columns(45)
                .testUI(BackgroundTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame f = new Frame("BackgroundTest");

        f.setBackground(Color.blue);
        f.setLayout(new FlowLayout(FlowLayout.CENTER));

        TextField tf = new TextField(30);
        TextArea ta = new TextArea(4, 30);
        TextField setTf = new TextField(30);
        TextArea setTa = new TextArea(4, 30);

        Button enableButton = new Button("DisableText");
        enableButton.setBackground(Color.red);

        tf.setText("Background not set - should be default");
        tf.setEditable(true);
        f.add(tf);
        ta.setText("Background not set - should be default");
        ta.setEditable(true);
        f.add(ta);

        setTf.setText("Background is set - should be Green");
        setTf.setBackground(Color.green);
        setTf.setEditable(true);
        f.add(setTf);
        setTa.setText("Background is set - should be Green");
        setTa.setBackground(Color.green);
        setTa.setEditable(true);
        f.add(setTa);

        enableButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean currentlyEditable = tf.isEditable();

                tf.setEditable(!currentlyEditable);
                ta.setEditable(!currentlyEditable);
                setTf.setEditable(!currentlyEditable);
                setTa.setEditable(!currentlyEditable);
                enableButton.setLabel(currentlyEditable ? "EnableText" : "DisableText");
            }
        });

        f.add(enableButton);

        f.setSize(300, 300);
        return f;
    }
}
