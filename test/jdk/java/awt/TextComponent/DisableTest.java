/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5042122
 * @summary Verifies the TextComponent is grayed out when disabled
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DisableTest
 */

import javax.swing.BoxLayout;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionListener;
import java.util.Vector;
import java.util.Iterator;

public class DisableTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Click "Enable" and "Disable" buttons and verify the text
                   components are disabled and enabled correctly.
                2. Verify that the disabled text components are grayed
                   out and are uneditable.
                3. Click PASS or FAIL accordingly.
                """;

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(DisableTest::initialize)
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        Frame frame = new Frame("TextComponent Disabled test");
        frame.setLayout(new BorderLayout());
        frame.setSize(200, 200);
        final Vector comps = new Vector();
        comps.add(new TextField("TextField"));
        TextArea ta = new TextArea("TextArea", 2, 100, TextArea.SCROLLBARS_NONE);
        comps.add(ta);
        Panel pc = new Panel();
        pc.setLayout(new BoxLayout(pc, BoxLayout.Y_AXIS));
        Iterator iter = comps.iterator();
        while (iter.hasNext()) {
            Component c = (Component) iter.next();
            c.setEnabled(false);
            pc.add(c);
        }
        frame.add(pc, BorderLayout.CENTER);
        Panel p = new Panel();
        final Button be = new Button("Enable");
        final Button bd = new Button("Disable");
        p.add(be);
        p.add(bd);
        ActionListener al = ev -> {
            boolean enable = (ev.getSource() == be);
            Iterator iterator = comps.iterator();
            while (iterator.hasNext()) {
                Component c = (Component) iterator.next();
                c.setEnabled(enable);
            }
        };
        be.addActionListener(al);
        bd.addActionListener(al);
        frame.add(p, BorderLayout.SOUTH);
        return frame;
    }
}
