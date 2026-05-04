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

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.event.WindowListener;
import java.util.List;

/*
 * @test
 * @bug 4058953 4094035
 * @summary Test to verify system menu of a dialog on win32
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DialogSystemMenu
 */

public class DialogSystemMenu {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Check the following on the first dialog window:
                    Right-clicking on the title bar
                    should bring up a system menu.
                    The system menu should not allow any
                    of the Maximize, Minimize and
                    Restore actions

                2. The second dialog should be non-resizable
                    and have no application icon.
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .build()
                .awaitAndCheck();
    }

    public static List<Dialog> initialize() {
        Frame frame = new java.awt.Frame("Parent Frame");
        String txt = """
                This is a resizable dialog
                Right-clicking on the title bar
                should bring up a system menu
                The system menu should not
                allow any
                of the Maximize, Minimize and
                Restore actions
                """;
        String txt_non = """
                This is a non-resizable dialog
                It should be really non-resizable
                and have no application icon
                """;
        TestApp resizable = new TestApp(frame, "Test for 4058953", txt, true);
        resizable.setLocation(0, 0);

        TestApp non_resizable = new TestApp(frame, "Test for 4094035", txt_non, false);
        non_resizable.setLocation(320, 0);
        return List.of(resizable, non_resizable);
    }
}


class TestApp extends Dialog implements WindowListener {
    public TestApp(java.awt.Frame parent, String title, String txt, boolean resize) {
        super(parent, title, false);

        java.awt.TextArea ta = new java.awt.TextArea(txt);
        ta.setEditable(false);
        this.add(ta, "Center");
        this.addWindowListener(this);
        this.setSize(300, 200);
        this.setResizable(resize);
    }


    public void windowOpened(java.awt.event.WindowEvent myEvent) {
    }

    public void windowClosed(java.awt.event.WindowEvent myEvent) {
    }

    public void windowIconified(java.awt.event.WindowEvent myEvent) {
    }

    public void windowDeiconified(java.awt.event.WindowEvent myEvent) {
    }

    public void windowActivated(java.awt.event.WindowEvent myEvent) {
    }

    public void windowDeactivated(java.awt.event.WindowEvent myEvent) {
    }

    public void windowClosing(java.awt.event.WindowEvent myEvent) {
        this.dispose();
    }
}
