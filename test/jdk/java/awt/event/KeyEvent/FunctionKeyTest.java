/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Event;
import java.awt.Frame;
import java.awt.Label;
import java.awt.TextArea;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4011219
 * @summary Test for function key press/release received by Java client.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FunctionKeyTest
 */

public class FunctionKeyTest {
    private static final String INSTRUCTIONS = """
            Press and release function keys F11 and F12.
            Look at the test window:
               On KeyPress:   'e.id=403' is printed
               On KeyRelease: 'e.id=404' is printed
            If the above is true, then click Pass, else click Fail.
            """;

    private static FunctionKeyTester frame;

    public static void main(String[] args) throws Exception {

        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                .title("FunctionKeyTest Instructions Frame")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(10)
                .columns(45)
                .build();

        SwingUtilities.invokeAndWait(() -> {
            frame = new FunctionKeyTester();

            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame
                    .positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);

            frame.setVisible(true);
        });

        Thread.sleep(500);

        SwingUtilities.invokeAndWait(() -> frame.requestFocus());

        passFailJFrame.awaitAndCheck();
    }
}

class FunctionKeyTester extends Frame {

    Label l = new Label ("NULL");
    Button b =  new Button ();
    TextArea log = new TextArea();

    FunctionKeyTester() {
        super("Function Key Test");
        this.setLayout(new BorderLayout());
        this.add(BorderLayout.NORTH, l);
        this.add(BorderLayout.SOUTH, b);
        this.add(BorderLayout.CENTER, log);
        log.setFocusable(false);
        log.setEditable(false);
        l.setBackground(Color.red);
        setSize(200, 200);
    }

    public boolean handleEvent(Event e) {
        String message = "e.id=" + e.id + "\n";
        System.out.print(message);
        log.append(message);

        return super.handleEvent(e);
    }

    public boolean keyDown(Event e, int key) {
        l.setText("e.key=" + Integer.valueOf(e.key).toString());
        return false;
    }
}
