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

/*
 * @test
 * @bug 4128659
 * @summary Tests whether a focus request will work on a focus lost event.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FocusKeepTest
 */

import java.awt.BorderLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.Frame;
import javax.swing.JFrame;
import javax.swing.JTextField;

public class FocusKeepTest {

    private static final String INSTRUCTIONS = """
         When window comes up, hit tab key.
         If Focus stay on the first component press Pass else press Fail""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("FocusKeepTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(FocusKeepTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        return new TestFocusSwing();
    }

}

class TestFocusSwing extends JFrame {

    public TestFocusSwing() {
        JTextField tf;

        tf = new JTextField ("TextField 1");
        tf.addFocusListener (new MyFocusAdapter ("TextField 1"));
        add(tf, BorderLayout.NORTH);

        tf = new JTextField ("TextField 2");
        tf.addFocusListener (new MyFocusAdapter ("TextField 2"));
        add(tf, BorderLayout.SOUTH);

        pack();
    }

    class MyFocusAdapter extends FocusAdapter {
        private String myName;

        public MyFocusAdapter (String name) {
            myName = name;
        }

        public void focusLost (FocusEvent e) {
            if (myName.equals ("TextField 1")) {
                e.getComponent().requestFocus ();
            }
        }

        public void focusGained (FocusEvent e) {
        }
    }
}
