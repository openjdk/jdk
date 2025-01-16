/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8169355
 * @summary Check if Spanish diacritical signs could be typed for TextField
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @run main/manual SpanishDiacriticsTest
*/


import java.util.concurrent.locks.LockSupport;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class SpanishDiacriticsTest {

    static final String INSTRUCTIONS = """
      This test requires the following keyboard layout to be installed:
      Windows OS: Spanish (United States) with 'Latin American' keyboard layout.
      If using a US layout, the results should still be as described but
      you have not tested the real bug.

       1. A frame with a text field should be displayed.
       2. Set focus to the text field and switch to Spanish
          with 'Latin American' keyboard layout.
       3. Type the following: ' ' o - i.e. single quote two times, then o.
          If your keyboard has a US physical layout the [ key can be used
          to type the single quote when in 'Latin American' keyboard mode.
       4. Type these characters at a normal speed but do NOT be concerned
          that they take several seconds to display. That is an
          expected behaviour for this test.

       If the text field displays the same three characters you typed: ''o
       (i.e. two single quotes followed by o without an acute)
       then press Pass; otherwise press Fail.
       """;

    public static void main(String[] args) throws Exception {

        PassFailJFrame.builder()
                      .title("Spanish Diacritics")
                      .instructions(INSTRUCTIONS)
                      .rows(20)
                      .columns(50)
                      .testUI(SpanishDiacriticsTest::createTestUI)
                      .build()
                      .awaitAndCheck();
    }

    static JFrame createTestUI() {
        JFrame frame = new JFrame("Spanish Diacritics Test Frame");
        JTextField textField = new JTextField(20);
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                LockSupport.parkNanos(1_000_000_000L);
            }
        });
        frame.add(textField);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        return frame;
    }
}

