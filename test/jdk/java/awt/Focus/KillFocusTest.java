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
/*
 * @test
 * @bug 4402942
 * @summary After deactivation and activation of frame, focus should be restored correctlty
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual KillFocusTest
*/

import java.awt.Frame;
import java.awt.TextField;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class KillFocusTest {

    private static final String INSTRUCTIONS = """
         After starting the test you should see \"Test Frame\"
         with the \"Click me\" text field.
         Click on this text field and try to type something in it.
         Make sure that the field receives focus and you can enter text in it.
         Click on any non-java window.
         Click on \"Click me\" text field to return focus to it
         If the caret is in the text field and you are able to type
         in it then press pass else press fail.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("KillFocusTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(KillFocusTest::createTestUI)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {

        Frame frame = new Frame("KillFocusTest Frame");
        TextField textField = new TextField("Click me", 10);
        textField.addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent fe) {
                PassFailJFrame.log("Focus gained");
            }
            public void focusLost(FocusEvent fe) {
                PassFailJFrame.log("Focus lost");
            }
        });
        frame.add(textField);
        frame.setSize(200, 100);
        return frame;
    }


}

