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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.List;

/*
 * @test
 * @bug 4141361
 * @summary Test to Ensures that mouse enter / exit is delivered to a new
 *          frame or component during a drag
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MouseDragEnterExitTest
 */

public class MouseDragEnterExitTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Click on the blue frame, drag to the white frame, and back
                You should get enter/exit messages for the frames when dragging
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(MouseEvents.initialize())
                .logArea(8)
                .build()
                .awaitAndCheck();
    }
}

class MouseEvents extends Frame implements MouseListener,
        MouseMotionListener {
    static int WITH_WIDGET = 0;

    public MouseEvents(int mode) {
        super("Mouse Drag Enter/Exit Test");
        setSize(300, 300);

        addMouseListener(this);
        addMouseMotionListener(this);

        if (mode == WITH_WIDGET) {
            setLayout(new BorderLayout());
            add("Center", new SimplePanel());
        }
    }

    public void mouseMoved(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
        printEvent(e);
    }


    public void mouseExited(MouseEvent e) {
        printEvent(e);
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public static void printEvent(MouseEvent e) {
        Component source = e.getComponent();

        String type = "none";

        if (e.getID() == MouseEvent.MOUSE_PRESSED)
            type = "MOUSE_PRESSED";

        if (e.getID() == MouseEvent.MOUSE_CLICKED)
            type = "MOUSE_CLICKED";

        if (e.getID() == MouseEvent.MOUSE_RELEASED)
            type = "MOUSE_RELEASED";

        if (e.getID() == MouseEvent.MOUSE_MOVED)
            type = "MOUSE_MOVED";

        if (e.getID() == MouseEvent.MOUSE_ENTERED)
            type = "MOUSE_ENTERED";

        if (e.getID() == MouseEvent.MOUSE_ENTERED)
            type = "MOUSE_ENTERED";

        if (e.getID() == MouseEvent.MOUSE_EXITED)
            type = "MOUSE_EXITED";


        if (e.getID() == MouseEvent.MOUSE_DRAGGED)
            type = "MOUSE_DRAGGED";

        PassFailJFrame.log(source.getName() + ": " + type + " " +
                e.getX() + " " + e.getY());

    }

    public static List<Frame> initialize() {
        MouseEvents m = new MouseEvents(MouseEvents.WITH_WIDGET);
        m.setLocation(500, 300);
        MouseEvents t = new MouseEvents(MouseEvents.WITH_WIDGET + 1);
        t.setLocation(200, 200);
        return List.of(m, t);
    }
}

class SimplePanel extends Panel implements MouseListener,
        MouseMotionListener {

    public SimplePanel() {
        super();
        setName("Test Panel");
        addMouseListener(this);
        addMouseMotionListener(this);
        setSize(100, 100);
        setVisible(true);
        setBackground(Color.blue);
    }

    public void mouseMoved(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
        printEvent(e);
    }

    public void mouseExited(MouseEvent e) {
        printEvent(e);
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public static void printEvent(MouseEvent e) {
        MouseEvents.printEvent(e);
    }
}

