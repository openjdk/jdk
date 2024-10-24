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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;

/*
 * @test
 * @bug 4035189
 * @summary Test to verify that Drag events go to wrong component
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MouseDragTest
 */

class HeavySquare extends Canvas {
    private final Color colorNormal;
    private boolean gotADragEvent;

    public HeavySquare(Color color) {
        colorNormal = color;
        setBackground(colorNormal);
        new MouseChecker(this);
        addMouseMotionListener(new DragAdapter());
        addMouseListener(new PressReleaseAdapter());
    }

    class DragAdapter extends MouseMotionAdapter {
        public void mouseDragged(MouseEvent ev) {
            if (gotADragEvent)
                return;

            Point mousePt = ev.getPoint();
            Dimension csize = getSize();
            boolean inBounds =
                    (mousePt.x >= 0 && mousePt.x <= csize.width &&
                            mousePt.y >= 0 && mousePt.y <= csize.height);
            if (!inBounds) {
                setBackground(Color.green);
            }
            gotADragEvent = true;
        }
    }

    class PressReleaseAdapter extends MouseAdapter {
        public void mousePressed(MouseEvent ev) {
            gotADragEvent = false;
        }

        public void mouseReleased(MouseEvent ev) {
            setBackground(colorNormal);
        }
    }

    public Dimension preferredSize() {
        return new Dimension(50, 50);
    }
}

class MouseFrame extends Frame {
    public MouseFrame() {
        super("MouseDragTest");
        new MouseChecker(this);
        setLayout(new FlowLayout());
        add(new HeavySquare(Color.red));
        add(new HeavySquare(Color.blue));
        setBounds(new Rectangle(20, 20, 400, 300));
    }
}

public class MouseDragTest {
    static Frame TestFrame;

    public MouseDragTest() {
        TestFrame = new MouseFrame();
    }

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. A frame with two boxes will appear. Click and drag _very_ quickly
                   off one of the components. You will know you were quick enough
                   when the component you dragged off of turns green
                2. Repeat this several times on both boxes, ensuring you get them
                   to turn green. The components should revert to their original
                   color when you release the mouse
                3. The test FAILS if the component doesn't revert to original
                   color, else PASS.
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(new MouseFrame())
                .build()
                .awaitAndCheck();
    }
}

class MouseChecker implements MouseListener, MouseMotionListener {
    private boolean isPressed = false;
    private MouseEvent evPrev = null;
    private MouseEvent evPrevPrev = null;

    public MouseChecker(Component comp) {
        comp.addMouseListener(this);
        comp.addMouseMotionListener(this);
    }

    private void recordEv(MouseEvent ev) {
        evPrevPrev = evPrev;
        evPrev = ev;
    }

    private synchronized void failure(String str) {
        PassFailJFrame.forceFail("Test Failed : "+str);
    }

    public void mouseClicked(MouseEvent ev) {
        if (!(evPrev.getID() == MouseEvent.MOUSE_RELEASED &&
                evPrevPrev.getID() == MouseEvent.MOUSE_PRESSED)) {
            failure("Got mouse click without press/release preceding.");
        }
        recordEv(ev);
    }

    public void mousePressed(MouseEvent ev) {
        recordEv(ev);
        if (isPressed) {
            failure("Got two mouse presses without a release.");
        }
        isPressed = true;
    }

    public void mouseReleased(MouseEvent ev) {
        recordEv(ev);
        if (!isPressed) {
            failure("Got mouse release without being pressed.");
        }
        isPressed = false;
    }

    public void mouseEntered(MouseEvent ev) {
        recordEv(ev);
        Point mousePt = ev.getPoint();
        Component comp = (Component) ev.getSource();
        Dimension size = comp.getSize();
        boolean inBounds =
                (mousePt.x >= 0 && mousePt.x <= size.width &&
                        mousePt.y >= 0 && mousePt.y <= size.height);

        if (!inBounds) {
            failure("Got mouse entered, but mouse not inside component.");
        }
    }

    public void mouseExited(MouseEvent ev) {
        recordEv(ev);
        Point mousePt = ev.getPoint();
        Component comp = (Component) ev.getSource();
        if (comp instanceof Frame) {
            return;
        }
        Dimension size = comp.getSize();
        boolean isOnChild = (comp != comp.getComponentAt(mousePt));
        boolean inBounds =
                (mousePt.x >= 0 && mousePt.x <= size.width &&
                        mousePt.y >= 0 && mousePt.y <= size.height);
        if (!isOnChild && inBounds) {
            failure("Got mouse exit, but mouse still inside component.");
        }
    }

    public void mouseDragged(MouseEvent ev) {
        recordEv(ev);
        if (!isPressed) {
            failure("Got drag without a press first.");
        }
    }

    public void mouseMoved(MouseEvent ev) {
        recordEv(ev);
    }
}
