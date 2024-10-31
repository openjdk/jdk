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

import java.awt.Button;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/*
 * @test
 * @bug 4940645
 * @summary Test to verify setAlwaysOnTop(true) does
 *          work in modal dialog in Windows
 * @requires (os.family == "windows" | os.family == "linux" )
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TopmostModalDialogTest
 */

public class TopmostModalDialogTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                (This test verifies that modal dialog can be made always on top
                This test should only be run on the platforms which support always-on-top windows
                Such platforms are: Windows, Linux with GNOME2/Metacity window manager,
                Solaris with GNOME2/Metacity window manager
                If you are not running on any of these platforms, please select 'Pass' to skip testing
                If you are unsure on which platform you are running please select 'Pass')

                1. After test started you see a frame with \\"Main Frame\\" title
                   It contains three buttons. Every button starts one of test stage
                   You should test all three stages
                2. After you press button to start the stage. It shows modal dialog
                   This modal dialog should be always-on-top window
                3. Since it's a modal the only way to test this is try to cover it
                   using some native window
                4. If you will able to cover it be native window - test FAILS, otherwise - PASS

                Note: in stages #2 and #3 dialog is initially shown as regular modal dialogs
                You will see \\"Let's wait\\" message in the message area below
                Please wait until message \\"Let's make it topmost\\" will be printed in the area
                After that you can continue testing.
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .logArea(8)
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        final Tester tester = new Tester();
        Frame frame = new Frame("Main Frame");
        frame.setLayout(new GridLayout(3, 1));
        for (int i = 0; i < 3; i++) {
            Button btn = new Button("Stage #" + i);
            frame.add(btn);
            btn.addActionListener(tester);
        }
        frame.pack();
        return frame;
    }
}

class Tester implements ActionListener {
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        PassFailJFrame.log(command);
        int cmd = Integer.parseInt(command.substring(command.length() - 1));
        PassFailJFrame.log("" + cmd);
        Dialog dlg = new Dialog(new Frame(""), "Modal Dialog", true);
        dlg.setBounds(100, 100, 100, 100);
        dlg.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                Window self = we.getWindow();
                Window owner = self.getOwner();
                if (owner != null) {
                    owner.dispose();
                } else {
                    self.dispose();
                }
            }
        });

        switch (cmd) {
            case 0:
                dlg.setAlwaysOnTop(true);
                dlg.setVisible(true);
                break;
            case 1:
                (new Thread(new TopmostMaker(dlg))).start();
                dlg.setVisible(true);
                break;
            case 2:
                dlg.setFocusableWindowState(false);
                (new Thread(new TopmostMaker(dlg))).start();
                dlg.setVisible(true);
                break;
            default:
                PassFailJFrame.log("Unsupported operation :(");
        }
    }
}

class TopmostMaker implements Runnable {
    final Window wnd;

    public TopmostMaker(Window wnd) {
        this.wnd = wnd;
    }

    public void run() {
        PassFailJFrame.log("Let's wait");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            PassFailJFrame.log("Test was interrupted. " + ie);
            ie.printStackTrace();
        }
        PassFailJFrame.log("Let's make it topmost");
        wnd.setAlwaysOnTop(true);
    }
}
