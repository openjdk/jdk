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

/*
 * @test
 * @bug 4380809
 * @summary Focus disappears after deiconifying frame
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DeiconifyTest
*/

import java.awt.Button;
import java.awt.Frame;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class DeiconifyTest {

    private static final String INSTRUCTIONS = """
         1. Activate frame \"Main frame\"
         be sure that button has focus
         2. Minimize frame and then restore it.
         If the button has focus then test passed, else failed""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("DeiconifyTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int)INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(DeiconifyTest::createTestUI)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI()   {
        Frame frame = new Frame("Main frame");
        Button button = new Button("button");
        button.addFocusListener(new FocusListener() {
              public void focusGained(FocusEvent fe) {
                  println("focus gained");
              }
              public void focusLost(FocusEvent fe) {
                  println("focus lost");
              }
          });
        frame.add(button);
        frame.setSize(300, 100);

        return frame;
    }

    static void println(String messageIn) {
        PassFailJFrame.log(messageIn);
    }
}

