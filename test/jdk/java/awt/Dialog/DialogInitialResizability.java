/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

/*
 * @test
 * @bug 4912551
 * @summary Checks that with resizable set to false before show()
 *          dialog can not be resized.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DialogInitialResizability
 */

public class DialogInitialResizability {
    static String instructions = """
                    When this test is run a dialog will display (setResizable Test).
                    This dialog should not be resizable.

                    Additionally ensure that there are NO componentResized events in the log section.
                    If the above conditions are true, then Press PASS else FAIL.
                    """;

    private static final Dimension INITIAL_SIZE = new Dimension(400, 150);
    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("DialogInitialResizability")
                .instructions(instructions)
                .testTimeOut(5)
                .rows((int) instructions.lines().count() + 2)
                .columns(40)
                .testUI(DialogInitialResizability::createGUI)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    public static MyDialog createGUI() {
        Frame f = new Frame("invisible dialog owner");

        MyDialog ld = new MyDialog(f);
        ld.setBounds(100, 100, INITIAL_SIZE.width, INITIAL_SIZE.height);
        ld.setResizable(false);

        PassFailJFrame.log("Dialog isResizable is set to: " + ld.isResizable());
        PassFailJFrame.log("Dialog Initial Size " + ld.getSize());
        return ld;
    }

    private static class MyDialog extends Dialog implements ComponentListener {
        public MyDialog(Frame f) {
            super(f, "setResizable test", false);
            this.addComponentListener(this);
        }

        public void componentResized(ComponentEvent e) {
            if (!e.getComponent().getSize().equals(INITIAL_SIZE)) {
                PassFailJFrame.log("Component Resized. Test Failed!!");
            }
        }

        public void componentMoved(ComponentEvent e) {
        }

        public void componentShown(ComponentEvent e) {
        }

        public void componentHidden(ComponentEvent e) {
        }
    }
}
