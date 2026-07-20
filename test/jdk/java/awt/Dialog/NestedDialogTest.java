/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Choice;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.List;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Vector;
import java.util.Enumeration;

/*
 * @test
 * @bug 4110094 4178930 4178390
 * @summary Test: Rewrite of Win modal dialogs
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual NestedDialogTest
 */

public class NestedDialogTest {
    private static Vector windows = new Vector();
    static String instructions = """
            To solve various race conditions, windows modal dialogs were rewritten. This
            test exercises various modal dialog boundary conditions and checks that
            previous fixes to modality are incorporated in the rewrite.

            Check the following:
            - No IllegalMonitorStateException is thrown when a dialog closes

            - Open multiple nested dialogs and verify that all other windows
            are disabled when modal dialog is active.

            - Check that the proper window is activated when a modal dialog closes.

            - Close nested dialogs out of order (e.g. close dialog1 before dialog2)
            and verify that this works and no deadlock occurs.

            - Check that all other windows are disabled when a FileDialog is open.

            - Check that the proper window is activated when a FileDialog closes.

            - Verify that the active window nevers switches to another application
            when closing dialogs, even temporarily.

            - Check that choosing Hide always sucessfully hides a dialog. You should
            try this multiple times to catch any race conditions.

            - Check that the scrollbar on the Choice component in the dialog works, as opposed
              to just using drag-scrolling or the cursor keys
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("NestedDialogTest")
                .instructions(instructions)
                .testTimeOut(5)
                .rows((int) instructions.lines().count() + 2)
                .columns(35)
                .testUI(NestedDialogTest::createGUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createGUI() {
        Frame frame1 = new NestedDialogTestFrame("frame0");
        Frame frame2 = new NestedDialogTestFrame("frame1");
        frame2.setLocation(100, 100);
        return frame1;
    }

    public static void addWindow(Window window) {
        // System.out.println("Pushing window " + window);
        windows.removeElement(window);
        windows.addElement(window);
    }

    public static void removeWindow(Window window) {
        // System.out.println("Popping window " + window);
        windows.removeElement(window);
    }

    public static Window getWindow(int index) {
        return (Window) windows.elementAt(index);
    }

    public static Enumeration enumWindows() {
        return windows.elements();
    }

    public static int getWindowIndex(Window win) {
        return windows.indexOf(win);
    }
}

class NestedDialogTestFrame extends Frame {
    NestedDialogTestFrame(String name) {
        super(name);
        setSize(200, 200);
        show();

        setLayout(new FlowLayout());
        Button btnDlg = new Button("Dialog...");
        add(btnDlg);
        Button btnFileDlg = new Button("FileDialog...");
        add(btnFileDlg);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent ev) {
                System.exit(0);
            }
        });

        btnDlg.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        Dialog d1 = new SimpleDialog(NestedDialogTestFrame.this, null, true);
                        System.out.println("Returned from showing dialog: " + d1);
                    }
                }
        );

        btnFileDlg.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        FileDialog dlg = new FileDialog(NestedDialogTestFrame.this);
                        dlg.show();
                    }
                }
        );

        validate();
    }

    public void show() {
        if (!isVisible()) {
            NestedDialogTest.addWindow(this);
        }
        super.show();
    }

    public void dispose() {
        NestedDialogTest.removeWindow(this);
        super.dispose();
    }
}

class SimpleDialog extends Dialog {
    Button btnNested;
    Button btnFileDlg;
    Button btnShow;
    Button btnHide;
    Button btnDispose;
    Button btnExit;
    List listWins;
    Dialog dlgPrev;

    public SimpleDialog(Frame frame, Dialog prev, boolean isModal) {
        super(frame, "", isModal);

        dlgPrev = prev;

        addWindowListener(new WindowAdapter() {
            public void windowActivated(WindowEvent ev) {
                populateListWin();
            }
        });

        setTitle(getName());

        Panel panelNorth = new Panel();
        panelNorth.setLayout(new GridLayout(1, 1));
        listWins = new List();
        panelNorth.add(listWins);

        Panel panelSouth = new Panel();
        panelSouth.setLayout(new FlowLayout());
        btnNested = new Button("Dialog...");
        panelSouth.add(btnNested);
        btnFileDlg = new Button("FileDialog...");
        panelSouth.add(btnFileDlg);
        btnShow = new Button("Show");
        panelSouth.add(btnShow);
        btnHide = new Button("Hide");
        panelSouth.add(btnHide);
        btnDispose = new Button("Dispose");
        panelSouth.add(btnDispose);

        Choice cbox = new Choice();
        cbox.add("Test1");
        cbox.add("Test2");
        cbox.add("Test3");
        cbox.add("Test4");
        cbox.add("Test5");
        cbox.add("Test6");
        cbox.add("Test7");
        cbox.add("Test8");
        cbox.add("Test9");
        cbox.add("Test10");
        cbox.add("Test11");
        panelSouth.add(cbox);

        validate();

        add("Center", panelNorth);
        add("South", panelSouth);

        btnNested.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Dialog dlg = new SimpleDialog((Frame) getParent(), SimpleDialog.this, true);
                System.out.println("Returned from showing dialog: " + dlg);
            }
        });

        btnFileDlg.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                FileDialog dlg = new FileDialog((Frame) getParent());
                dlg.show();
            }
        });

        btnHide.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Window wnd = getSelectedWindow();
                System.out.println(wnd);
                wnd.hide();
            }
        });

        btnShow.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getSelectedWindow().show();
            }
        });

        btnDispose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getSelectedWindow().dispose();
                populateListWin();
            }
        });

        pack();
        setSize(getSize().width, getSize().height * 2);
        if (dlgPrev != null) {
            Point pt = dlgPrev.getLocation();
            setLocation(pt.x + 30, pt.y + 50);
        }
        show();
    }

    private Window getSelectedWindow() {
        Window window;
        int index = listWins.getSelectedIndex();

        window = NestedDialogTest.getWindow(index);
        return window;
    }

    private void populateListWin() {
        Enumeration enumWindows = NestedDialogTest.enumWindows();

        listWins.removeAll();
        while (enumWindows.hasMoreElements()) {
            Window win = (Window) enumWindows.nextElement();
            listWins.add(win.getName());
        }
        listWins.select(NestedDialogTest.getWindowIndex(this));
    }

    public void show() {
        if (!isVisible()) {
            NestedDialogTest.addWindow(this);
        }
        super.show();
    }

    public void dispose() {
        NestedDialogTest.removeWindow(this);
        super.dispose();
    }
}
