/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import java.util.Locale;

/*
 * @test
 * @key headful
 * @bug 8377938
 * @summary manual test for VoiceOver activating JButtons via AccessibleAction
 * @requires os.family == "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual VoiceOverAccessibleActionButtonTest
 */

public class VoiceOverAccessibleActionButtonTest {
    public static void main(String[] args) throws Exception {
        // this bug did not reproduce in English
        Locale.setDefault(Locale.forLanguageTag("de-DE"));

        // A button's AccessibleAction description is localized. So in English
        // it's "click", and in German it's "clicken".
        // Bug 8377938 points out that we wouldn't let VoiceOver trigger
        // the AccessibleAction unless the action description was "click".
        // So in German: the AccessibleAction wasn't used. Instead: VoiceOver
        // very quickly moved the mouse over the button, simulated a mouse
        // click, and the restored the mouse position.
        // This bug focuses on making sure the button is NOT activated via
        // a MouseEvent.

        String INSTRUCTIONS = "INSTRUCTIONS:\n" +
                "1. Open VoiceOver\n" +
                "2. Move the VoiceOver cursor over the button.\n" +
                "3. Press CTRL + OPTION + SPACE to activate the button.\n\n" +
                "Expected behavior: the text box / console should identify " +
                "the AWTEvent that helped activate the JButton. The event " +
                "should NOT be a MouseEvent or KeyEvent. (In practice it " +
                "happens to be an InvocationEvent.)";

        PassFailJFrame.builder()
                .title("VoiceOverAccessibleActionButtonTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(VoiceOverAccessibleActionButtonTest::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JButton button = new JButton("Sample Button");
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);

        button.addActionListener(e -> {
            AWTEvent currentEvent = EventQueue.getCurrentEvent();
            log(textArea, "Activated via " + currentEvent.getClass().getSimpleName());
        });

        JFrame frame = new JFrame();
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(button, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 400));
        frame.getContentPane().add(scrollPane);
        frame.pack();
        return frame;
    }

    private static void log(JTextArea textArea, String msg) {
        System.out.println(msg);
        textArea.setText((textArea.getText() + "\n" + msg).trim());
    }
}
