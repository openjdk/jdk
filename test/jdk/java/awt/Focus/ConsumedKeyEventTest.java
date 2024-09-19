/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4700276
 * @summary Peers process KeyEvents before KeyEventPostProcessors
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ConsumedKeyEventTest
*/

import java.awt.Canvas;
import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventPostProcessor;
import java.awt.event.KeyEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ConsumedKeyEventTest implements KeyEventPostProcessor {

    private static final String INSTRUCTIONS = """
            This is a Windows-only test.
            When the test starts, you will see a Frame with two components in it,
            components look like colored rectangles, one of them is lightweight, one is heavyweight.
            Do the following:
            1. Click the mouse on the left component.
               If it isn't yellow after the click (that means it doesn't have focus), the test fails.
            2. Press and release ALT key.
               In the output window, the text should appear stating that those key events were consumed.
               If no output appears, the test fails.
            3. Press space bar. If system menu drops down, the test fails.
            4. Click the right rectangle.
               It should become red after the click. If it doesn't, it means that it didn't get the focus, and the test fails.
            5. Repeat steps 2. and 3.
            6. If the test didn't fail on any of the previous steps, the test passes.""";


    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("ConsumedKeyEventTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 5)
                .columns(35)
                .testUI(ConsumedKeyEventTest::createTestUI)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().
            addKeyEventPostProcessor((e) -> {
                System.out.println("postProcessor(" + e + ")");
                // consumes all ALT-events
                if (e.getKeyCode() == KeyEvent.VK_ALT) {
                    println("consumed " + e);
                    e.consume();
                    return true;
                }
                return false;
        });
        FocusRequestor requestor = new FocusRequestor();
        Frame frame = new Frame("Main Frame");
        frame.setLayout(new FlowLayout());

        Canvas canvas = new CustomCanvas();
        canvas.addMouseListener(requestor);
        frame.add(canvas);
        canvas.requestFocus();

        Component lwComp = new LWComponent();
        lwComp.addMouseListener(requestor);
        frame.add(lwComp);

        frame.pack();

        return frame;
    }

    public boolean postProcessKeyEvent(KeyEvent e) {
        System.out.println("postProcessor(" + e + ")");
        // consumes all ALT-events
        if (e.getKeyCode() == KeyEvent.VK_ALT) {
            println("consumed " + e);
            e.consume();
            return true;
        }
        return false;
    }

    static void println(String messageIn) {
        PassFailJFrame.log(messageIn);
    }
}// class ConsumedKeyEventTest

class CustomCanvas extends Canvas {
    CustomCanvas() {
        super();
        setName("HWComponent");
        setSize(100, 100);
        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent fe) {
                repaint();
            }

            public void focusLost(FocusEvent fe) {
                repaint();
            }
        });
    }

    public void paint(Graphics g) {
        if (isFocusOwner()) {
            g.setColor(Color.YELLOW);
        } else {
            g.setColor(Color.GREEN);
        }
        g.fillRect(0, 0, 100, 100);
    }

}

class LWComponent extends Component {
    LWComponent() {
        super();
        setName("LWComponent");
        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent fe) {
                repaint();
            }

            public void focusLost(FocusEvent fe) {
                repaint();
            }
        });
    }

    public Dimension getPreferredSize() {
        return new Dimension(100, 100);
    }

    public void paint(Graphics g) {
        if (isFocusOwner()) {
            g.setColor(Color.RED);
        } else {
            g.setColor(Color.BLACK);
        }
        g.fillRect(0, 0, 100, 100);
    }

}

class FocusRequestor extends MouseAdapter {
    static int counter = 0;
    public void mouseClicked(MouseEvent me) {
        System.out.println("mouseClicked on " + me.getComponent().getName());
    }
    public void mousePressed(MouseEvent me) {
        System.out.println("mousePressed on " + me.getComponent().getName());
        me.getComponent().requestFocus();
    }
    public void mouseReleased(MouseEvent me) {
        System.out.println("mouseReleased on " + me.getComponent().getName());
    }
}

