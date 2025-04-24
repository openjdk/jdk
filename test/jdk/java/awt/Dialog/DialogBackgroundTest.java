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
 * @bug 4255230 4191946
 * @summary Tests to verify Dialog inherits background from its owner
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DialogBackgroundTest
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.TextField;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class DialogBackgroundTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Perform the following steps:
                1) Select "New Frame" from the "File" menu of the
                   "TreeCopy Frame #1" frame.
                2) Select "Configure" from the "File" menu in the
                   *new* frame.
                   If label text "This is a label:" in the appeared
                   "Configuration Dialog" dialog has a grey background
                   test PASSES, otherwise it FAILS
                   """;
        TreeCopy treeCopy = new TreeCopy(++TreeCopy.windowCount, null);
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(treeCopy)
                .logArea(8)
                .build()
                .awaitAndCheck();
    }
}

class TreeCopy extends Frame implements ActionListener {
    TextField tfRoot;
    ConfigDialog configDlg;
    MenuItem miConfigure = new MenuItem("Configure...");
    MenuItem miNewWindow = new MenuItem("New Frame");
    static int windowCount = 0;
    Window parent;

    public TreeCopy(int windowNum, Window myParent) {
        super();
        setTitle("TreeCopy Frame #" + windowNum);
        MenuBar mb = new MenuBar();
        Menu m = new Menu("File");
        configDlg = new ConfigDialog(this);
        parent = myParent;

        m.add(miConfigure);
        m.add(miNewWindow);
        miConfigure.addActionListener(this);
        miNewWindow.addActionListener(this);
        mb.add(m);
        setMenuBar(mb);
        m.addActionListener(this);

        tfRoot = new TextField();
        tfRoot.setEditable(false);
        add(tfRoot);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                dispose();
            }
        });

        setSize(200, 100);
        setLocationRelativeTo(parent);
    }

    public void actionPerformed(ActionEvent ae) {
        Object source = ae.getSource();

        if (source == miConfigure) {
            configDlg.setVisible(true);
            if (configDlg.getBackground() != configDlg.labelColor)
                PassFailJFrame.log("FAIL: Test failed!!!");
        } else if (source == miNewWindow) {
            new TreeCopy(++windowCount, this).setVisible(true);
        }
    }
}

class ConfigDialog extends Dialog implements ActionListener {
    public Button okButton;
    public Button cancelButton;
    public Label l2;
    public Color labelColor;

    public ConfigDialog(Frame parent) {
        super(parent, "Configuration Dialog");
        okButton = new Button("OK");
        cancelButton = new Button("Cancel");
        l2 = new Label("This is a label:");

        setLayout(new FlowLayout());
        add(l2);
        add(okButton);
        add(cancelButton);

        okButton.addActionListener(this);
        cancelButton.addActionListener(this);

        pack();
        labelColor = l2.getBackground();
    }

    public void actionPerformed(ActionEvent ae) {
        dispose();
    }
}
