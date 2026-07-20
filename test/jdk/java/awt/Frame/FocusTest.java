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

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.Window;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/*
 * @test
 * @bug 4140293
 * @summary Tests that focus is returned to the correct Component when a Frame
 *          is reactivated.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FocusTest
 */

public class FocusTest {
    private static final String INSTRUCTIONS = """
              Click on the bottom rectangle. Move the mouse slightly.
              A focus rectangle should appear around the bottom rectangle.

              Now, deactivate the window and then reactivate it.
              (You would click on the caption bar of another window,
              and then on the caption bar of the FocusTest Frame.)

              If the focus rectangle appears again, the test passes.
              If it does not appear, or appears around the top rectangle,
              the test fails.
              """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("FocusTest Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .logArea(6)
                .testUI(FocusTest::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static Window createAndShowUI() {
        Frame frame = new Frame("FocusTest");

        frame.add(new FocusTestPanel());
        frame.setSize(400, 400);

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                frame.dispose();
            }
        });

        frame.validate();
        return frame;
    }

    private static class FocusTestPanel extends Panel {
        PassiveClient pc1 = new PassiveClient("pc1");
        PassiveClient pc2 = new PassiveClient("pc2");

        public FocusTestPanel() {
            super();
            setLayout(new GridLayout(2, 1, 10, 10));
            add(pc1);
            add(pc2);
        }
    }

    private static class PassiveClient extends Canvas implements FocusListener {
        boolean haveFocus = false;
        final String name;

        PassiveClient(String name) {
            super();
            this.name = name;
            setSize(400, 100);
            setBackground(Color.cyan);
            setVisible(true);
            setEnabled(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    requestFocus();
                }
            });
            addFocusListener(this);
        }

        public void paint(Graphics g) {
            g.setColor(getBackground());
            Dimension size = getSize();
            g.fillRect(0, 0, size.width, size.height);
            if (haveFocus) {
                g.setColor(Color.black);
                g.drawRect(0, 0, size.width - 1, size.height - 1);
                g.drawRect(1, 1, size.width - 3, size.height - 3);
            }
            g.setColor(getForeground());
        }

        public void focusGained(FocusEvent event) {
            haveFocus = true;
            paint(getGraphics());
            PassFailJFrame.log("<<<< %s Got focus!! %s>>>>".formatted(this, event));
        }

        public void focusLost(FocusEvent event) {
            haveFocus = false;
            paint(getGraphics());
            PassFailJFrame.log("<<<< %s Lost focus!! %s>>>>".formatted(this, event));
        }

        @Override
        public String toString() {
            return "PassiveClient " + name;
        }
    }
}
