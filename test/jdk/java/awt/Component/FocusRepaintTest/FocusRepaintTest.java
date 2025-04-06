/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4079435
 * @summary Calling repaint() in focus handlers messes up the window.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FocusRepaintTest
 */

import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.lang.reflect.InvocationTargetException;

public class FocusRepaintTest extends Frame implements FocusListener {
    static final String INSTRUCTIONS = """
            Hit the tab key repeatedly in the Test window.
            If any of the buttons disappear press Fail, otherwise press Pass.
            """;

    public FocusRepaintTest() {
        setTitle("Test");
        setLayout(new FlowLayout());
        setSize(200, 100);
        Button b1 = new Button("Close");
        Button b2 = new Button("Button");
        add(b1);
        add(b2);
        b1.setSize(50, 30);
        b2.setSize(50, 30);
        b1.addFocusListener(this);
        b2.addFocusListener(this);
    }

    public void focusGained(FocusEvent e) {
        Button b = (Button) e.getSource();
        PassFailJFrame.log("Focus gained for " + b.getLabel());
        b.repaint();
    }

    public void focusLost(FocusEvent e) {
        Button b = (Button) e.getSource();
        PassFailJFrame.log("Focus lost for " + b.getLabel());
        b.repaint();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Focus Repaint")
                .testUI(FocusRepaintTest::new)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .logArea()
                .build()
                .awaitAndCheck();
    }
}
