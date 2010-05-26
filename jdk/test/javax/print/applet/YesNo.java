/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.*;
import java.awt.event.*;

public class YesNo extends Panel implements ActionListener {

    static String nl = System.getProperty("line.Separator", "\n");
    static String instructions =
        "Wait until 5 applets have initialised and started and display string"
        +nl+
        "messages. Applet 0 and Applet 2 should find one less print service"
        +nl+
        "than the rest."
        +nl+
        "Specifically all except Applets 0 and 2 should find a service called"
        +nl+
        "Applet N printer where N is the number of the applet."
        +nl+
        "They should *NOT* find Applet M printer (where M != N)."
        +nl+
        "After deciding if the test passes, Quit appletviewer, and next"
        +nl+
        "Select either the Pass or Fail button below";


    public static void main(String args[]) {
        Frame f = new Frame("Test Execution Instructions");
        f.setLayout(new BorderLayout());
        TextArea ta = new TextArea(instructions, 12,80);
        ta.setEditable(false);
        f.add(BorderLayout.CENTER, ta);
        f.add(BorderLayout.SOUTH, new YesNo());
        f.pack();
        f.setVisible(true);
    }

    public YesNo() {
        Button pass = new Button("Pass");
        Button fail = new Button("Fail");
        pass.addActionListener(this);
        fail.addActionListener(this);
        add(pass);
        add(fail);
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("Pass")) {
            System.exit(0);
        } else {
            System.exit(-1);
        }
    }

}
