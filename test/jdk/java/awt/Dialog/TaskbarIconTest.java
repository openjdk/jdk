/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;

/*
 * @test
 * @bug 6488834
 * @requires (os.family == "windows")
 * @summary Tests that native dialogs (file, page, print) appear or
    don't appear on the windows taskbar depending of their parent
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TaskbarIconTest
*/

public class TaskbarIconTest {
    private static WindowListener wl = new WindowAdapter() {
        public void windowClosing(WindowEvent we) {
            Window w = we.getWindow();
            w.dispose();
            Window owner = w.getOwner();
            if (owner != null) {
                owner.dispose();
            }
        }
    };

    private static ActionListener al = new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
            Button b = (Button) ae.getSource();

            String bLabel = b.getLabel();
            boolean hasParent = (bLabel.indexOf("parentless") < 0);
            Frame parent = hasParent ? new Frame("Parent") : null;

            if (bLabel.startsWith("Java")) {
                Dialog d = new Dialog(parent, "Java dialog", true);
                d.setBounds(0, 0, 160, 120);
                d.addWindowListener(wl);
                d.setVisible(true);
            } else if (bLabel.startsWith("File")) {
                FileDialog d = new FileDialog(parent, "File dialog");
                d.setVisible(true);
            } else if (bLabel.startsWith("Print")) {
                PrinterJob pj = PrinterJob.getPrinterJob();
                pj.printDialog();
            } else if (bLabel.startsWith("Page")) {
                PrinterJob pj = PrinterJob.getPrinterJob();
                pj.pageDialog(new PageFormat());
            }
        }
    };

    private static final String INSTRUCTIONS = """
            When the test starts a frame 'Main' is shown. It contains
            several buttons, pressing each of them shows a dialog.
            Some of the dialogs have a parent window, others are
            parentless, according to the corresponding button's test.

            Press each button one after another. Make sure that all
            parentless dialogs have an icon in the windows taskbar
            and all the dialogs with parents don't. Press PASS or
            FAIL button depending on the result.

            Note: as all the dialogs shown are modal, you have to close
            them before showing the next dialog or PASS or FAIL buttons."
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("WindowInputBlock")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(TaskbarIconTest::createGUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createGUI() {
        Button b;

        Frame mainFrame = new Frame("Main");
        mainFrame.setBounds(120, 240, 160, 240);
        mainFrame.setLayout(new GridLayout(6, 1));

        b = new Button("Java dialog, with parent");
        b.addActionListener(al);
        mainFrame.add(b);

        b = new Button("Java dialog, parentless");
        b.addActionListener(al);
        mainFrame.add(b);

        b = new Button("File dialog, with parent");
        b.addActionListener(al);
        mainFrame.add(b);

        b = new Button("File dialog, parentless");
        b.addActionListener(al);
        mainFrame.add(b);

        b = new Button("Print dialog, parentless");
        b.addActionListener(al);
        mainFrame.add(b);

        b = new Button("Page dialog, parentless");
        b.addActionListener(al);
        mainFrame.add(b);

        return mainFrame;
    }
}
