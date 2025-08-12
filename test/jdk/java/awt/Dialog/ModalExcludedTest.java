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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.JobAttributes;
import java.awt.PageAttributes;
import java.awt.Panel;
import java.awt.PrintJob;
import java.awt.TextArea;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

import sun.awt.SunToolkit;

/*
 * @test
 * @bug 4813288 4866704
 * @summary Test for "modal exclusion" functionality
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame
 * @modules java.desktop/sun.awt
 * @run main/manual ModalExcludedTest
 */

public class ModalExcludedTest {
    private static final String INSTRUCTIONS = """
            1. Press 'Modal dialog w/o modal excluded' button below
                A window, a modeless dialog and a modal dialog will appear
                Make sure the frame and the modeless dialog are inaccessible,
                i.e. receive no mouse and keyboard events. MousePressed and
                KeyPressed events are logged in the text area - use it
                to watch events
            Close all 3 windows

            2. Press 'Modal dialog w/ modal excluded' button below
                Again, 3 windows will appear (frame, dialog, modal dialog),
                but the frame and the dialog would be modal excluded, i.e.
                behave the same way as there is no modal dialog shown. Verify
                this by pressing mouse buttons and typing any keys. The
                RootFrame would be modal blocked - verify this too
            Close all 3 windows

            3. Repeat step 2 for file and print dialogs using appropriate
                buttons below

            Notes: if there is no printer installed in the system you may not
                get any print dialogs
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("ModalExcludedTest")
                .instructions(INSTRUCTIONS)
                .rows(10)
                .columns(35)
                .testUI(ModalExcludedTest::createGUIs)
                .build()
                .awaitAndCheck();
    }

    public static Frame createGUIs() {
        final Frame f = new Frame("RootFrame");
        f.setBounds(0, 0, 480, 500);
        f.setLayout(new BorderLayout());

        final TextArea messages = new TextArea();

        final WindowListener wl = new WindowAdapter() {
            public void windowClosing(WindowEvent ev) {
                if (ev.getSource() instanceof Window) {
                    ((Window) ev.getSource()).dispose();
                }
            }
        };
        final MouseListener ml = new MouseAdapter() {
            public void mousePressed(MouseEvent ev) {
                messages.append(ev + "\n");
            }
        };
        final KeyListener kl = new KeyAdapter() {
            public void keyPressed(KeyEvent ev) {
                messages.append(ev + "\n");
            }
        };

        if (!SunToolkit.isModalExcludedSupported()) {
            throw new jtreg.SkippedException("Modal exclude is not supported on this platform.");
        }

        messages.addMouseListener(ml);
        messages.addKeyListener(kl);
        f.add(messages, BorderLayout.CENTER);

        Panel buttons = new Panel();
        buttons.setLayout(new GridLayout(6, 1));

        Button b = new Button("Modal dialog w/o modal excluded");
        b.addActionListener(ev -> {
            Frame ff = new Frame("Non-modal-excluded frame");
            ff.setBounds(400, 0, 200, 100);
            ff.addWindowListener(wl);
            ff.addMouseListener(ml);
            ff.addKeyListener(kl);
            ff.setVisible(true);

            Dialog dd = new Dialog(ff, "Non-modal-excluded dialog", false);
            dd.setBounds(500, 100, 200, 100);
            dd.addWindowListener(wl);
            dd.addMouseListener(ml);
            dd.addKeyListener(kl);
            dd.setVisible(true);

            Dialog d = new Dialog(f, "Modal dialog", true);
            d.setBounds(600, 200, 200, 100);
            d.addWindowListener(wl);
            d.addMouseListener(ml);
            d.addKeyListener(kl);
            d.setVisible(true);
        });
        buttons.add(b);

        Button c = new Button("Modal dialog w/ modal excluded");
        c.addActionListener(ev -> {
            JFrame ff = new JFrame("Modal-excluded frame");
            ff.setBounds(400, 0, 200, 100);
            ff.addWindowListener(wl);
            ff.addMouseListener(ml);
            ff.addKeyListener(kl);
            JMenuBar mb = new JMenuBar();
            JMenu m = new JMenu("Test menu");
            m.add("Test menu item");
            m.add("Test menu item");
            m.add("Test menu item");
            m.add("Test menu item");
            m.add("Test menu item");
            m.add("Test menu item");
            m.add("Test menu item");
            m.add("Test menu item");
            m.add("Test menu item");
            mb.add(m);
            ff.setJMenuBar(mb);
            // 1: set visible
            ff.setVisible(true);

            Dialog dd = new Dialog(ff, "Modal-excluded dialog", false);
            dd.setBounds(500, 100, 200, 100);
            dd.addWindowListener(wl);
            dd.addMouseListener(ml);
            dd.addKeyListener(kl);
            dd.setVisible(true);

            // 2: set modal excluded
            SunToolkit.setModalExcluded(ff);

            Dialog d = new Dialog(f, "Modal dialog", true);
            d.setBounds(600, 200, 200, 100);
            d.addWindowListener(wl);
            d.addMouseListener(ml);
            d.addKeyListener(kl);
            d.setVisible(true);
        });
        buttons.add(c);

        Button c1 = new Button("Modal dialog before modal excluded");
        c1.addActionListener(ev -> {
            // 1: create dialog
            Dialog d = new Dialog(f, "Modal dialog", true);
            d.setBounds(600, 200, 200, 100);
            d.addWindowListener(wl);
            d.addMouseListener(ml);
            d.addKeyListener(kl);

            // 2: create frame
            Frame ff = new Frame("Modal-excluded frame");
            // 3: set modal excluded
            SunToolkit.setModalExcluded(ff);
            ff.setBounds(400, 0, 200, 100);
            ff.addWindowListener(wl);
            ff.addMouseListener(ml);
            ff.addKeyListener(kl);
            // 4: show frame
            ff.setVisible(true);

            Dialog dd = new Dialog(ff, "Modal-excluded dialog", false);
            dd.setBounds(500, 100, 200, 100);
            dd.addWindowListener(wl);
            dd.addMouseListener(ml);
            dd.addKeyListener(kl);
            dd.setVisible(true);

            // 5: show dialog
            d.setVisible(true);
        });
        buttons.add(c1);

        Button d = new Button("File dialog w/ modal excluded");
        d.addActionListener(ev -> {
            Frame ff = new Frame("Modal-excluded frame");
            ff.setBounds(400, 0, 200, 100);
            ff.addWindowListener(wl);
            ff.addMouseListener(ml);
            ff.addKeyListener(kl);
            // 1: set modal excluded (peer is not created yet)
            SunToolkit.setModalExcluded(ff);
            // 2: set visible
            ff.setVisible(true);

            Dialog dd = new Dialog(ff, "Modal-excluded dialog", false);
            dd.setBounds(500, 100, 200, 100);
            dd.addWindowListener(wl);
            dd.addMouseListener(ml);
            dd.addKeyListener(kl);
            dd.setVisible(true);
            SunToolkit.setModalExcluded(dd);

            Dialog d1 = new FileDialog(f, "File dialog");
            d1.setVisible(true);
        });
        buttons.add(d);

        Button e = new Button("Native print dialog w/ modal excluded");
        e.addActionListener(ev -> {
            Frame ff = new Frame("Modal-excluded frame");
            ff.setBounds(400, 0, 200, 100);
            ff.addWindowListener(wl);
            ff.addMouseListener(ml);
            ff.addKeyListener(kl);
            ff.setVisible(true);
            SunToolkit.setModalExcluded(ff);

            Dialog dd = new Dialog(ff, "Modal-excluded dialog", false);
            dd.setBounds(500, 100, 200, 100);
            dd.addWindowListener(wl);
            dd.addMouseListener(ml);
            dd.addKeyListener(kl);
            dd.setVisible(true);

            JobAttributes jobAttributes = new JobAttributes();
            jobAttributes.setDialog(JobAttributes.DialogType.NATIVE);
            PageAttributes pageAttributes = new PageAttributes();
            PrintJob job = Toolkit.getDefaultToolkit().getPrintJob(f, "Test", jobAttributes, pageAttributes);
        });
        buttons.add(e);

        Button g = new Button("Common print dialog w/ modal excluded");
        g.addActionListener(ev -> {
            Frame ff = new Frame("Modal-excluded frame");
            ff.setBounds(400, 0, 200, 100);
            ff.addWindowListener(wl);
            ff.addMouseListener(ml);
            ff.addKeyListener(kl);
            ff.setVisible(true);
            SunToolkit.setModalExcluded(ff);
            ff.dispose();
            // modal excluded must still be alive
            ff.setVisible(true);

            Dialog dd = new Dialog(ff, "Modal-excluded dialog", false);
            dd.setBounds(500, 100, 200, 100);
            dd.addWindowListener(wl);
            dd.addMouseListener(ml);
            dd.addKeyListener(kl);
            dd.setVisible(true);

            JobAttributes jobAttributes = new JobAttributes();
            jobAttributes.setDialog(JobAttributes.DialogType.COMMON);
            PageAttributes pageAttributes = new PageAttributes();
            PrintJob job = Toolkit.getDefaultToolkit().getPrintJob(f, "Test", jobAttributes, pageAttributes);
        });
        buttons.add(g);

        f.add(buttons, BorderLayout.SOUTH);
        return f;
    }
}
