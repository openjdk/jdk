/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.ScrollPane;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.WindowConstants;

import jtreg.SkippedException;

/*
 * @test
 * @bug 4368500
 * @key multimon
 * @summary Dialog needs a constructor with GraphicsConfiguration
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame
 * @run main/manual DialogTest
 */

public class DialogTest {
    static GraphicsDevice[] gds;

    private static Frame f;
    private static Frame dummyFrame = new Frame();
    private static Dialog dummyDialog = new Dialog(dummyFrame);

    public static void main(String[] args) throws Exception {
        gds = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        if (gds.length < 2) {
            throw new SkippedException("You have only one monitor in your system" +
                                       " - test skipped");
        }

        String INSTRUCTIONS = """
                This test tests the multiscreen functionality of Dialogs and JDialogs.
                You should see the message "X screens detected", where X
                is the number of screens on your system. If X is incorrect, press Fail.

                In the test window, there are a list of buttons representing each
                type of dialog for each screen.
                If there aren't buttons for every screen in your system, press Fail.

                Press each button, and the indicated type of dialog should appear
                on the indicated screen.
                Modal dialogs should not allow to click on the Instructions or
                DialogTest windows.

                The buttons turn yellow once they have been pressed, to keep track
                of test progress.

                If all Dialogs appear correctly, press Pass.
                If Dialogs appear on the wrong screen or don't behave in
                proper modality, press Fail.""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(40)
                .logArea(5)
                .testUI(DialogTest::init)
                .build()
                .awaitAndCheck();
    }

    public static Frame init() {
        PassFailJFrame.log(gds.length + " screens detected.");
        f = new Frame("DialogTest UI");
        f.setSize(400, 400);
        MyScrollPane sp = new MyScrollPane();

        Panel p = new Panel();
        p.setLayout(new GridLayout(0, 1));

        for (int i = 0; i < gds.length; i++) {
            Button btn;

            //screen # , modal, frame-owned, swing
            btn = new MyButton(new DialogInfo(i, false, false, false));
            p.add(btn);

            btn = new MyButton(new DialogInfo(i, true, false, false));
            p.add(btn);

            btn = new MyButton(new DialogInfo(i, false, true, false));
            p.add(btn);

            btn = new MyButton(new DialogInfo(i, true, true, false));
            p.add(btn);

            btn = new MyButton(new DialogInfo(i, false, false, true));
            p.add(btn);

            btn = new MyButton(new DialogInfo(i, true, false, true));
            p.add(btn);

            btn = new MyButton(new DialogInfo(i, false, true, true));
            p.add(btn);

            btn = new MyButton(new DialogInfo(i, true, true, true));
            p.add(btn);

        }
        sp.add(p);
        f.add(sp);
        return f;
    }

    static class MyScrollPane extends ScrollPane {
        @Override
        public Dimension getPreferredSize() {
            return f.getSize();
        }
    }

    static class MyButton extends Button {
        public MyButton(DialogInfo info) {
            setLabel(info.toString());
            addActionListener(new PutupDialog(info));
        }
    }

    static class PutupDialog implements ActionListener {
        DialogInfo info;

        public PutupDialog(DialogInfo info) {
            this.info = info;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ((Button) (e.getSource())).setBackground(Color.yellow);
            Dialog d = info.createDialog();
            d.show();
        }
    }

    static class DialogInfo {
        int num;
        boolean modal;
        boolean frameOwned;
        boolean swing;

        public DialogInfo(int num, boolean modal, boolean frameOwned, boolean swing) {
            this.num = num;
            this.modal = modal;
            this.frameOwned = frameOwned;
            this.swing = swing;
        }

        public Dialog createDialog() {
            GraphicsConfiguration gc = gds[num].getDefaultConfiguration();

            Dialog d;

            if (swing) {
                if (frameOwned) {
                    d = new JDialog(dummyFrame, toString(), modal, gc);
                } else {
                    d = new JDialog(dummyDialog, toString(), modal, gc);
                }

                ((JDialog) d).setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
                if (modal) {
                    ((JDialog) d).getContentPane().add(new JLabel("Check that I am modal!"));
                }
            } else {
                if (frameOwned) {
                    d = new Dialog(dummyFrame, toString(), modal, gc);
                } else {
                    d = new Dialog(dummyDialog, toString(), modal, gc);
                }

                d.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        e.getComponent().hide();
                    }
                });
                if (modal) {
                    d.add(new Label("Check that I am modal!"));
                }
            }

            d.setLocation(new Point((int) (gc.getBounds().getX() + 20)
                          , (int) (gc.getBounds().getY() + 20)));
            d.setSize(300, 100);

            return d;
        }

        public String toString() {
            return "Screen " + num + (frameOwned ? " Frame-owned" : " Dialog-owned")
                    + (modal ? " modal " : " non-modal ")
                    + (swing ? "JDialog" : "Dialog");
        }
    }
}


